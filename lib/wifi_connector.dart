import 'dart:async';

import 'package:flutter/services.dart';

class WifiConnector {
  static const MethodChannel _channel = const MethodChannel('wifi_connector');

  static Future<bool> connectToWifi({required String ssid, String? password, bool isWEP = false}) async {
    final result = await _channel.invokeMethod<bool>('connectToWifi', {
      'ssid': ssid,
      'password': password,
      'isWEP': isWEP,
    });
    return result == true;
  }
}
