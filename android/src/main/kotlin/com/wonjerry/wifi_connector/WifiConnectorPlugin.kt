package com.wonjerry.wifi_connector

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar

class WifiConnectorPlugin : MethodCallHandler, FlutterPlugin {
  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val instance = WifiConnectorPlugin()
      // activity의 context를 받아오기위해 FlutterPlugin을 상속받고, interfacee들을 구현한다.
      instance.onAttachedToEngine(registrar.context(), registrar.messenger())
    }
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
    when (call.method) {
      "connectToWifi" -> connectToWifi(call, result)
      else -> result.notImplemented()
    }
  }

  private fun connectToWifi(call: MethodCall, result: Result) {
    val argMap = call.arguments as Map<String, Any>
    val ssid = argMap["ssid"] as String
    val password = argMap["password"] as String?

    // 비밀번호가 있냐 없냐에 따라 wifi configration을 설정한다.
    val wifiConfiguration =
            if (password == null) {
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

      // 위에서 생성한 configration을 추가하고 해당 네트워크와 연결한다.
      var networkId = addNetwork(wifiConfiguration)
      if (networkId == null) {
        result.success(false)
        return;
      }
      disconnect()
      enableNetwork(networkId, true)
      reconnect()
      result.success(true)
    }

  }
}

fun String.wrapWithDoubleQuotes(): String = "\"$this\""