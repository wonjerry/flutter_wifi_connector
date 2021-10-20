# wifi_connector

## Prerequisite

- iOS

    - Change development target to 11.0

    - Add hot spot configuration capability to Runner.entitlements

        ```
            <key>com.apple.developer.networking.wifi-info</key>
            <true/>
        ```

    - Add hot spot configuration capability to Runner.entitlements

        ```
            <key>com.apple.developer.networking.HotspotConfiguration</key>
            <true/>
        ```
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
