import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

class WifiConnector {
  static const MethodChannel _channel = const MethodChannel('wifi_connector');

  /// Connect to wifi with the native platform api's
  /// Security type
  ///   Android: Supported WPA2/WPA3
  ///   iOS: Supported WPA2
  static Future<bool> connectToWifi({required String ssid, String? password, SecurityType securityType = SecurityType.NONE, bool internetRequired = true}) async {
    if (password != null && securityType == SecurityType.NONE) {
      throw ArgumentError('If you are using a password you should also set the correct securityType');
    }
    final result = await _channel.invokeMethod<bool>('connectToWifi', {
      'ssid': ssid,
      'password': password,
      'isWEP': securityType == SecurityType.WEP,
      'isWpa2': securityType == SecurityType.WPA2,
      'isWpa3': securityType == SecurityType.WPA3,
      'internetRequired': internetRequired,
    });
    print('RESULT: $result');
    return result == true;
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
  /// iOS will always return true because there is no permission needed & no screen will be opened
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
