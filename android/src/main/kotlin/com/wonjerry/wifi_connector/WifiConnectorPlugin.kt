package com.wonjerry.wifi_connector

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.provider.Settings
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

class WifiConnectorPlugin : MethodCallHandler, FlutterPlugin, ActivityAware {
  companion object {
    @JvmStatic
    fun registerWith(registrar: PluginRegistry.Registrar) {
      val instance = WifiConnectorPlugin()
      instance.activity = registrar.activity()
      // activity의 context를 받아오기위해 FlutterPlugin을 상속받고, interfacee들을 구현한다.
      instance.onAttachedToEngine(registrar.context(), registrar.messenger())
    }
  }

  private var activity: Activity? = null
  private var networkCallback: ConnectivityManager.NetworkCallback? = null
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

  // Attatch the engine
  private fun onAttachedToEngine(context: Context, messenger: BinaryMessenger) {
    activityContext = context
    methodChannel = MethodChannel(messenger, "wifi_connector").apply {
      setMethodCallHandler(this@WifiConnectorPlugin)
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    try {
      when (call.method) {
        "hasPermission" -> hasPermission(result)
        "openPermissionsScreen" -> openPermissionsScreen(result)
        "disconnectPeerToPeerConnection" -> disconnectPeerToPeerConnection(result)
        "connectToPeerToPeerWifi" -> connectToPeerToPeerWifi(call, result)
        "connectToWifi" -> connectToWifi(call, result)
        else -> result.notImplemented()
      }
    } catch (e: Exception) {
      result.error("500", e.message, e.stackTraceToString())
    }
  }

  private fun hasPermission(result: Result) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      result.success(Settings.System.canWrite(activityContext))
      return
    }
    result.success(true)
  }

  private fun openPermissionsScreen(result: Result) {
    val context = activityContext
    if (context == null) {
      result.error("500", "Activity Context is null", "")
      return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        result.success(true)
        return
    }
    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
    intent.data = Uri.parse("package:" + context.packageName)
    activity?.startActivity(intent)
  }

  private fun disconnectPeerToPeerConnection(result: Result) {
    networkCallback?.let { cleanupNetworkCallback(it) }
    result.success(true)
  }

  private fun connectToWifi(call: MethodCall, result: Result) {
    val argMap = call.arguments as Map<*, *>
    val ssid = argMap["ssid"] as String
    val password = argMap["password"] as String?
    connectToWifiPreQ(result, ssid, password)
  }

  private fun connectToPeerToPeerWifi(call: MethodCall, result: Result) {
    val argMap = call.arguments as Map<*, *>
    val ssid = argMap["ssid"] as String
    val password = argMap["password"] as String?
    val isWpa2 = argMap["isWpa2"] as Boolean
    val isWpa3 = argMap["isWpa3"] as Boolean

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      connectToWifiPreQ(result, ssid, password)
      return
    }
    connectToWifiPostQWithoutInternet(result, ssid, password, isWpa2, isWpa3)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun connectToWifiPostQWithoutInternet(result: Result, ssid: String, password: String?, isWpa2: Boolean, isWpa3: Boolean) {
    val context = activityContext
    if (context == null) {
      result.error("500", "Activity Context is null", "")
      return
    }
    if (networkCallback != null) {
      result.error("500", "Still connected", "First disconnect")
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
    val request = NetworkRequest.Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .setNetworkSpecifier(specifier)
      .build()
    val networkCallback = object : ConnectivityManager.NetworkCallback() {
      var resultSent = false

      override fun onAvailable(network: Network) {
        super.onAvailable(network)
        connectivityManager.bindProcessToNetwork(network)
        if (resultSent) return
        result.success(true)
        resultSent = true
        // cannot unregister callback here since it would disconnect from the network
      }

      override fun onUnavailable() {
        super.onUnavailable()
        cleanupNetworkCallback(this)
        if (resultSent) return
        resultSent = true
        result.success(false)
      }
    }
    val handler = Handler(context.mainLooper)
    connectivityManager.requestNetwork(request, networkCallback, handler)
    this.networkCallback = networkCallback
  }

  private fun cleanupNetworkCallback(networkCallback: ConnectivityManager.NetworkCallback){
    connectivityManager.unregisterNetworkCallback(networkCallback)
    this.networkCallback = null
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
      if (networkId == -1) {
        result.success(false)
        return
      }
      disconnect()
      enableNetwork(networkId, true)
      reconnect()
      result.success(true)
    }
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {}

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }
}

fun String.wrapWithDoubleQuotes(): String = "\"$this\""