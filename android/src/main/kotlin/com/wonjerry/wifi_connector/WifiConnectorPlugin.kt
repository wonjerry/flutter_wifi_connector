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
import java.lang.Exception

class WifiConnectorPlugin : MethodCallHandler, FlutterPlugin, PluginRegistry.ActivityResultListener, ActivityAware {
  companion object {
    const val MANAGE_SETTINGS_RESULT_CODE = 1001
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
  private var result: Result? =null
  private var suggestion: WifiNetworkSuggestion? =null
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
      if (Settings.System.canWrite(activityContext)) {
        result.success(true)
        return
      }
    }
    result.success(false)
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
      intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      activity?.startActivityForResult(intent, MANAGE_SETTINGS_RESULT_CODE)
      return
    }
    result.success(true)
  }

  private fun connectToWifi(call: MethodCall, result: Result) {
    val argMap = call.arguments as Map<String, Any>
    val ssid = argMap["ssid"] as String
    val password = argMap["password"] as String?
    val isWap2 = argMap["isWap2"] as Boolean
    val isWap3 = argMap["isWap3"] as Boolean
    val internetRequired = argMap["internetRequired"] as Boolean

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if (internetRequired) {
        connectToWifiPostQWithInternet(result, ssid, password, isWap2, isWap3)
      } else {
        connectToWifiPostQWithoutInternet(result, ssid, password, isWap2, isWap3)
      }
    } else {
      connectToWifiPreQ(result, ssid, password)
    }
  }


  @RequiresApi(Build.VERSION_CODES.Q)
  private fun connectToWifiPostQWithInternet(result: Result, ssid: String, password: String?, isWap2: Boolean, isWap3: Boolean) {
    val context = activityContext
    if (context == null) {
      result.error("500", "Activity Context is null", "")
      return
    }
    val suggestion = WifiNetworkSuggestion.Builder()
      .setSsid(ssid)
      .apply {
        password?.let {
          if (isWap2) {
            setWpa2Passphrase(it)
          } else if(isWap3){
            setWpa3Passphrase(it)
          }
        }
      }
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      this.result = result
      this.suggestion = suggestion
      val intent = Intent(ACTION_WIFI_ADD_NETWORKS)
      intent.putExtra(EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
      intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      activity?.startActivityForResult(intent, ADD_WIFI_RESULT_CODE)
      return
    }
    connectToWifiPostQWithSuggestion(context, suggestion, result)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun connectToWifiPostQWithSuggestion(context: Context, suggestion: WifiNetworkSuggestion, result: Result){
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
    result.success(status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun connectToWifiPostQWithoutInternet(result: Result, ssid: String, password: String?, isWap2: Boolean, isWap3: Boolean) {
    val context = activityContext
    if (context == null) {
      result.error("500", "Activity Context is null", "")
      return
    }
    val specifier = WifiNetworkSpecifier.Builder()
      .setSsid(ssid)
      .apply {
        if (password != null) {
          if (isWap2) {
            setWpa2Passphrase(password)
          } else if (isWap3) {
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
        connectivityManager.bindProcessToNetwork(network)
        result.success(true)
        // cannot unregister callback here since it would disconnect form the network
      }

      override fun onUnavailable() {
        result.success(false)
        connectivityManager.unregisterNetworkCallback(this)
        networkCallback = null
      }
    }

    val handler = Handler(context.mainLooper)
    connectivityManager.requestNetwork(request, networkCallback!!, handler)
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
    val result = result ?: return false;
    val suggestion = suggestion ?: return false;
    val context = activityContext ?: return false;
    when (code) {
        ADD_WIFI_RESULT_CODE -> {
          if (resultCode == Activity.RESULT_OK){
            result.success(true)
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectToWifiPostQWithSuggestion(context, suggestion, result)
          } else {
            result.success(false)
          }
        }
        MANAGE_SETTINGS_RESULT_CODE -> {
          result.success(resultCode == Activity.RESULT_OK)
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