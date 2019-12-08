# wifi_connector

## Prerequisite

- iOS

    1. Change development target to 11.0

    1. Add hot spot configuration capability

- Android

    - Add wifi permissions to `AndroidManifest.xml`

        ```
            <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
            <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
        ```

## Usage

- Connect to public wifi

    ```dart

    await WifiConnector.connectToWifi(ssid: ssid);

    ```

- Connect to private wifi

    ```dart

    await WifiConnector.connectToWifi(ssid: ssid, password: password);

    ```
