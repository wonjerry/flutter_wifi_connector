package com.wonjerry.wifi_connector

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.provider.Settings
import android.provider.Settings.ACTION_WIFI_ADD_NETWORKS
import android.provider.Settings.EXTRA_WIFI_NETWORK_LIST
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar


class WifiConnectorPlugin : MethodCallHandler, FlutterPlugin, PluginRegistry.ActivityResultListener, ActivityAware {
  companion object {
    const val ADD_WIFI_RESULT_CODE = 1002
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val instance = WifiConnectorPlugin()
      instance.activity = registrar.activity()
      // activity의 context를 받아오기위해 FlutterPlugin을 상속받고, interfacee들을 구현한다.
      instance.onAttachedToEngine(registrar.context(), registrar.messenger())
      registrar.addActivityResultListener(instance)
    }
  }

  private var binding: ActivityPluginBinding? = null
  private var activity: Activity? = null
  private var networkCallback: ConnectivityManager.NetworkCallback? = null
  private var result: Result? = null
  private var ssid: String? = null
  private var suggestion: WifiNetworkSuggestion? = null
  private val connectivityManager: ConnectivityManager by lazy(LazyThreadSafetyMode.NONE) {
    activityContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }
  private var activityContext: Context? = null
  private var methodChannel: MethodChannel? = null

  // Plugin이 Flutter Activity와 연결되었을 때 불리는 callback method이다.
  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    onAttachedToEngine(binding.applicationContext, binding.flutterEngine.dartExecutor)
  }

  // Plugin이 Flutter Activity와 연결이 떨어졌을 때 불리는 callback method이다.
  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    activityContext = null
    methodChannel?.setMethodCallHandler(null)
    methodChannel = null
  }

  // Plugin이 Flutter Activity와 연결되었을 때 context를 저장 해 둔다.
  private fun onAttachedToEngine(context: Context, messenger: BinaryMessenger) {
    activityContext = context
    methodChannel = MethodChannel(messenger, "wifi_connector").apply {
      setMethodCallHandler(this@WifiConnectorPlugin)
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    try {
      when (call.method) {
        "connectToWifi" -> connectToWifi(call, result)
        "hasPermission" -> hasPermission(call, result)
        "openPermissionsScreen" -> openPermissionsScreen(call, result)
        else -> result.notImplemented()
      }
    } catch (e: Exception) {
      result.error("500", e.message, e.stackTraceToString())
    }
  }

  private fun hasPermission(call: MethodCall, result: Result) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      result.success(Settings.System.canWrite(activityContext))
      return
    }
    result.success(true)
  }

  private fun openPermissionsScreen(call: MethodCall, result: Result) {
    val context = activityContext
    if (context == null) {
      result.error("500", "Activity Context is null", "")
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      this.result = result
      val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
      intent.data = Uri.parse("package:" + context.packageName)
      activity?.startActivity(intent)
      return
    }
    result.success(true)
  }

  private fun connectToWifi(call: MethodCall, result: Result) {
    val argMap = call.arguments as Map<String, Any>
    val ssid = argMap["ssid"] as String
    val password = argMap["password"] as String?
    val isWpa2 = argMap["isWpa2"] as Boolean
    val isWpa3 = argMap["isWpa3"] as Boolean
    val internetRequired = argMap["internetRequired"] as Boolean

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      connectToWifiPreQ(result, ssid, password)
      return
    }
    if (internetRequired) {
      connectToWifiPostQWithInternet(result, ssid, password, isWpa2, isWpa3)
    } else {
      connectToWifiPostQWithoutInternet(result, ssid, password, isWpa2, isWpa3)
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun connectToWifiPostQWithInternet(result: Result, ssid: String, password: String?, isWpa2: Boolean, isWpa3: Boolean) {
    val context = activityContext
    if (context == null) {
      result.error("500", "Activity Context is null", "")
      return
    }
    val suggestion = WifiNetworkSuggestion.Builder()
      .setSsid(ssid)
      .apply {
        password?.let {
          if (isWpa2) {
            setWpa2Passphrase(it)
          } else if(isWpa3){
            setWpa3Passphrase(it)
          }
        }
      }
      .setIsAppInteractionRequired(true)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      this.result = result
      this.ssid = ssid
      this.suggestion = suggestion
      val intent = Intent(ACTION_WIFI_ADD_NETWORKS)
      intent.putExtra(EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
      activity?.startActivityForResult(intent, ADD_WIFI_RESULT_CODE)
      return
    }
    connectToWifiPostQWithSuggestion(context, suggestion, ssid, result)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun connectToWifiPostQWithoutInternet(result: Result, ssid: String, password: String?, isWpa2: Boolean, isWpa3: Boolean) {
    val context = activityContext
    if (context == null) {
      result.error("500", "Activity Context is null", "")
      return
    }
    val specifier = WifiNetworkSpecifier.Builder()
      .setSsid(ssid)
      .apply {
        if (password != null) {
          if (isWpa2) {
            setWpa2Passphrase(password)
          } else if (isWpa3) {
            setWpa3Passphrase(password)
          }
        }
      }
      .build()
    networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
    val request = NetworkRequest.Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .setNetworkSpecifier(specifier)
      .build()

    networkCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        super.onAvailable(network)
        connectivityManager.bindProcessToNetwork(network)
        result.success(true)
        // cannot unregister callback here since it would disconnect form the network
      }

      override fun onUnavailable() {
        super.onUnavailable()
        result.success(false)
        connectivityManager.unregisterNetworkCallback(this)
        networkCallback = null
      }
    }

    val handler = Handler(context.mainLooper)
    connectivityManager.requestNetwork(request, networkCallback!!, handler)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun connectToWifiPostQWithSuggestion(context: Context, suggestion: WifiNetworkSuggestion, ssid: String, result: Result){
    val intentFilter = IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)
    val broadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (!intent.action.equals(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)) {
          return
        }
        result.success(true)
        context.unregisterReceiver(this)
      }
    }
    context.registerReceiver(broadcastReceiver, intentFilter)
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
    if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
        result.success(false)
        return
    }
    result.success(true)
  }

  @SuppressLint("MissingPermission")
  @Suppress("DEPRECATION")
  private fun connectToWifiPreQ(result: Result, ssid: String, password: String?) {
    val wifiConfiguration = if (password == null) {
      WifiConfiguration().apply {
        SSID = ssid.wrapWithDoubleQuotes()
        status = WifiConfiguration.Status.CURRENT
        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
      }
    } else {
      WifiConfiguration().apply {
        SSID = ssid.wrapWithDoubleQuotes()
        preSharedKey = password.wrapWithDoubleQuotes()
        status = WifiConfiguration.Status.CURRENT
        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
      }
    }
    val wifiManager = activityContext?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as WifiManager
    with(wifiManager) {
      if (!isWifiEnabled) {
        isWifiEnabled = true
      }
      var networkId = addNetwork(wifiConfiguration)
      if (networkId != -1) {
        val network = configuredNetworks.find { network -> network.SSID == ssid.wrapWithDoubleQuotes() }
        network?.let { networkId = it.networkId }
      }
      if (networkId != -1) {
        result.success(false)
        return
      }
      disconnect()
      enableNetwork(networkId, true)
      reconnect()
      result.success(true)
    }
  }

  override fun onActivityResult(code: Int, resultCode: Int, data: Intent?): Boolean {
    val context = activityContext ?: return false
    val result = result ?: return false
    val ssid = ssid ?: return false
    val suggestion = suggestion ?: return false
    when (code) {
        ADD_WIFI_RESULT_CODE -> {
          if (resultCode != Activity.RESULT_OK) {
            result.success(false)
            return true
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectToWifiPostQWithSuggestion(context, suggestion, ssid, result)
          } else {
            result.success(true)
          }
        }
    }
    return true
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addActivityResultListener(this)
    this.binding = binding
  }

  override fun onDetachedFromActivityForConfigChanges() {}

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
    binding?.removeActivityResultListener(this)
  }
}

fun String.wrapWithDoubleQuotes(): String = "\"$this\""