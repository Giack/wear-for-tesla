# Install the app

Wear for Tesla is not on the Play Store. You install the APK yourself ("sideloading"), once, and again for each update.

## Requirements

- A watch with **Wear OS 4 or later** (Android 13, API level 33).
- A **Tesla Model 3 or Model Y** (other models work over Bluetooth but have no car illustration yet).
- The car's **Keycard** and its **VIN**, for pairing.

## 1. Download the APK

Download the `.apk` file attached to the [latest release](https://github.com/Giack/wear-for-tesla/releases/latest). Every release is signed with the same project key, so a newer version installs over an older one and keeps your pairing.

## 2. Turn on wireless debugging on the watch

1. On the watch, open **Settings → System → About → Versions** (on some watches **Settings → About watch → Software**) and tap **Software version** seven times until developer options are enabled.
2. Open **Settings → Developer options**, turn on **ADB debugging** and then **Wireless debugging**.
3. Connect the watch to the same Wi-Fi network as the computer or phone you will install from.

## 3. Install

=== "From a computer"

    You need `adb` from the [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools).

    1. On the watch, in **Wireless debugging**, tap **Pair new device**. It shows an IP address, a port and a pairing code.
    2. Pair and connect:

        ```sh
        adb pair <ip>:<pairing port>        # type the pairing code
        adb connect <ip>:<port>             # the port shown on the Wireless debugging screen
        adb devices                         # the watch is listed as <ip>:<port>
        ```

    3. Install, replacing an earlier version if there is one:

        ```sh
        adb -s <ip>:<port> install -r wear-for-tesla-<version>.apk
        ```

    Watches drop Wi-Fi to save battery. If `adb` loses the watch, open **Wireless debugging** again (the port may have changed) and repeat `adb connect`. Keeping the watch on its charger helps.

=== "From an Android phone"

    Some third-party phone apps can install an APK on a watch over wireless debugging, for example *Wear Installer 2* or *Bugjaeger*. They are not part of this project: follow their own instructions, pair them with the watch's **Wireless debugging** screen and pick the downloaded APK.

=== "Build from source"

    With a JDK 17 or later and the Android SDK:

    ```sh
    git clone https://github.com/Giack/wear-for-tesla.git
    cd wear-for-tesla/android
    ./gradlew :wear:assemblePublicRelease
    ```

    The APK is in `android/wear/build/outputs/apk/public/release/`. An APK you build yourself is signed with a different key from the releases, so switching between the two means uninstalling first and pairing again.

## 4. Open it

Find **Wear for Tesla** in the watch's app list and follow [Bluetooth mode setup](bluetooth-mode.md). When you are done you can turn **ADB debugging** off again; it is only needed to install.
