import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

class WifiConnector {
  static const MethodChannel _channel = const MethodChannel('wifi_connector');

  /// Connect to wifi with the native platform api's
  /// Security type
  ///   Android: Supported WPA2/WPA3
  ///   iOS: Supported WEP/WPA2
  static Future<bool> connectToWifi({required String ssid, String? password, SecurityType securityType = SecurityType.NONE}) async {
    if (password != null && securityType == SecurityType.NONE) {
      throw ArgumentError('If you are using a password you should also set the correct securityType');
    }
    final result = await _channel.invokeMethod<bool>('connectToWifi', {
      'ssid': ssid,
      'password': password,
      'isWep': securityType == SecurityType.WEP,
      'isWpa2': securityType == SecurityType.WPA2,
      'isWpa3': securityType == SecurityType.WPA3,
    });
    return result == true;
  }

  /// Connect to wifi with the native platform api's
  ///   Android: This will use the `peer-to-peer` API. You will not see that you are connected to an other network. but you can configure your device with this API.
  ///            Mostly used for IOT device setup. Chomecast/IOT/Router/Gateway/...
  ///            You will not see this in you OS settings. only the app that did the connect process will have a connection to the wifi network.
  ///            You will not have internet connectivity.
  ///   iOS: This is not supported by iOS so this will be a no-op (return always false)
  /// Security type
  ///   Android: Supported WPA2/WPA3
  ///   iOS: Supported WEP/WPA2
  /// internetRequired: true
  static Future<bool> connectToPeerToPeerWifi({required String ssid, String? password, SecurityType securityType = SecurityType.NONE}) async {
    if (!Platform.isAndroid) return false;
    if (password != null && securityType == SecurityType.NONE) {
      throw ArgumentError('If you are using a password you should also set the correct securityType');
    }
    final result = await _channel.invokeMethod<bool>('connectToPeerToPeerWifi', {
      'ssid': ssid,
      'password': password,
      'isWep': securityType == SecurityType.WEP,
      'isWpa2': securityType == SecurityType.WPA2,
      'isWpa3': securityType == SecurityType.WPA3,
    });
    return result == true;
  }

  /// Open the permission screen
  /// Android only required for Android 10+. This will clean up a still active connection that was started with `internetRequired = false`
  /// iOS will be a no-op
  static Future<void> disconnectPeerToPeerConnection() async {
    if (Platform.isIOS) return;
    await _channel.invokeMethod<bool>('disconnectPeerToPeerConnection');
  }

  /// Check if your app already has permission to update the wifi settings
  /// Android only required Android 10+
  /// iOS will always return true because there is no permission needed
  static Future<bool> hasPermission() async {
    if (Platform.isIOS) return true;
    final result = await _channel.invokeMethod<bool>('hasPermission');
    return result == true;
  }

  /// Open the permission screen
  /// Android only required Android 10+
  /// iOS will be a no-op because there are no permissions needed for iOS
  static Future<void> openPermissionsScreen() async {
    if (Platform.isIOS) return;
    await _channel.invokeMethod<bool>('openPermissionsScreen');
  }
}

enum SecurityType {
  NONE,
  WEP,
  WPA2,
  WPA3,
}
