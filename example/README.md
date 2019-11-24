# wifi_connector_example

## Prerequisite

- iOS

1. Change development target to 11.0

2. Add hot spot configuration capability

- Android

1. Add wifi permissions

```
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
  package="com.wonjerry.wifi_connector">

    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
</manifest>

```

- Connect to public wifi

```dart

await WifiConnector.connectToWifi(ssid: ssid);

```

## Usage

- Connect to private wifi

```dart

await WifiConnector.connectToWifi(ssid: ssid, password: password);

```
