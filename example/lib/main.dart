import 'package:flutter/material.dart';

import 'package:wifi_connector/wifi_connector.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String ssid = '';
  String password = '';
  bool isSucceed = false;

  Widget _buildTextInput(String title, void Function(String) onChanged) {
    return Row(
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.only(left: 24.0),
          child: Container(width: 80.0, child: Text(title)),
        ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32.0),
            child: TextField(
              onChanged: onChanged,
            ),
          ),
        )
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
          appBar: AppBar(
            title: const Text('Wifi connector example app'),
          ),
          body: Column(
            children: <Widget>[
              _buildTextInput('ssid', (ssid) {
                setState(() {
                  this.ssid = ssid;
                });
              }),
              _buildTextInput('password', (password) {
                setState(() {
                  this.password = password;
                });
              }),
              Padding(
                padding: const EdgeInsets.only(top: 24.0),
                child: FlatButton(
                  color: Colors.blue,
                  child: Text('확인', style: TextStyle(color: Colors.white),),
                  onPressed: () async {
                    final isSucceed = await WifiConnector.connectToWifi(ssid: ssid, password: password);
                    setState(() {
                      this.isSucceed = isSucceed;
                    });
                  },
                ),
              ),
              Expanded(
                child: Center(
                  child: Text('Is wifi connected?: $isSucceed'),
                ),
              )
            ],
          )),
    );
  }
}
