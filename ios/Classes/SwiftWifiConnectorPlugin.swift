import Flutter
import UIKit
import NetworkExtension
import SystemConfiguration.CaptiveNetwork

public class SwiftWifiConnectorPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "wifi_connector", binaryMessenger: registrar.messenger())
    let instance = SwiftWifiConnectorPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "connectToWifi":
      self.connectToWifi(call, result)
    default:
      result(FlutterMethodNotImplemented)
    }
  }
  
  private func connectToWifi(_ call: FlutterMethodCall, _ result: @escaping FlutterResult) {
    guard let argMaps = call.arguments as? Dictionary<String, Any>,
      let ssid = argMaps["ssid"] as? String,
      let isWep = argMaps["isWep"] as? Bool else {
        result(FlutterError(code: call.method, message: "Missing arguments", details: nil))
        return
    }
    
    var hotspotConfiguration: NEHotspotConfiguration
    if let password = argMaps["password"] as? String {
      hotspotConfiguration = NEHotspotConfiguration(ssid: ssid, passphrase: password, isWEP: isWep)
    } else {
      hotspotConfiguration = NEHotspotConfiguration(ssid: ssid)
    }
    
    hotspotConfiguration.lifeTimeInDays = 1
    
    NEHotspotConfigurationManager.shared.apply(hotspotConfiguration) { (error) in
      guard let error = error else {
        result(true)
        return
      }
      let nsError = error as NSError
      if let hotspotError = NEHotspotConfigurationError(rawValue: nsError.code) {
        switch hotspotError {
          case NEHotspotConfigurationError.userDenied:
            result(false)
            break
          case NEHotspotConfigurationError.alreadyAssociated:
            result(true)
            break
          default:
            result(FlutterError(code: call.method, message: error.localizedDescription, details: hotspotError.rawValue))
        }
      } else {
        result(false)
      }
    }
  }
}
