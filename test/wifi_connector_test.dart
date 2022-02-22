import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wifi_connector/wifi_connector.dart';

void main() {
  const MethodChannel channel = MethodChannel('wifi_connector');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return true;
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(
        await WifiConnector.connectToWifi(
          ssid: 'myssid',
          password: 'mypassword',
        ),
        true);
  });
}
