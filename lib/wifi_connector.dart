import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

class WifiConnector {
  static const MethodChannel _channel = const MethodChannel('wifi_connector');

  static Future<bool> connectToWifi({@required String ssid, String password, bool isWEP = false}) async {
    return await _channel.invokeMethod('connectToWifi', {'ssid': ssid, 'password': password, 'isWEP': isWEP});
  }
}
