import Flutter
import UIKit
import NetworkExtension
import SystemConfiguration.CaptiveNetwork

public class SwiftWifiConnectorPlugin: NSObject, FlutterPlugin {
        
  var currentWifiSSD: String? {
    if let interfaces = CNCopySupportedInterfaces() as NSArray? {
      for interface in interfaces {
        if let interfaceInfo = CNCopyCurrentNetworkInfo(interface as! CFString) as NSDictionary? {
          return interfaceInfo[kCNNetworkInfoKeySSID as String] as? String
        }
      }
    }
    return nil
  }
    
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
    // Flutter 쪽에서 arguments로 map을 보낸 것을 이렇게 Dictionary로 parsing해서 사용한다.
    guard let argMaps = call.arguments as? Dictionary<String, Any>,
      let ssid = argMaps["ssid"] as? String,
      let isWEP = argMaps["isWEP"] as? Bool else {
        result(FlutterError(code: call.method, message: "Missing argument: ssid", details: nil))
        return
    }
    
    var hotspotConfiguration: NEHotspotConfiguration
    
    if isWEP {
      result(FlutterError(code: call.method, message: "WEP is not supported", details: nil))
      return
    }
    
    if let password = argMaps["password"] as? String {
      hotspotConfiguration = NEHotspotConfiguration(ssid: ssid, passphrase: password, isWEP: false)
    } else {
      hotspotConfiguration = NEHotspotConfiguration(ssid: ssid)
    }
    
    hotspotConfiguration.lifeTimeInDays = 1
    
    // 연결을 시도하고, 성공 여부를 callback으로 전달받는다.
    NEHotspotConfigurationManager.shared.apply(hotspotConfiguration) { (error) in
      guard let error = error else {
        result(self.currentWifiSSD == ssid)
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
