# Privacy

Wear for Tesla has no server, no account, no analytics and no advertising. Nothing reaches the project or its maintainer.

## What stays on the watch

- The **BLE key** is created in the watch's Android Keystore and never leaves it.
- The **VIN**, the car profile, the car's Bluetooth address (for the background check) and the last known state of the car are stored in the app's private storage.
- In Fleet mode, the **Cloud key**, the Developer app credentials and your Tesla sign-in tokens are also stored in the app's private storage. **Turn off Fleet mode** deletes them; **Remove car** deletes everything.

## Who the app talks to

- **Your car**, directly over Bluetooth.
- In Fleet mode only, **Tesla's** sign-in and Fleet API servers, using your own Developer app, to read the car's state and send the commands you confirm. Tesla's own privacy policy applies to that.
- Wear OS forwards the Tesla sign-in to your phone, where you complete it.

The helper script for Fleet mode runs on your computer and talks only to GitHub (to publish your public key), Tesla (to register your domain) and your watch (over `adb`). The private key and credentials it creates stay in `secrets/` on your computer.

## The source

The app is open source, so you can check all of this in the [repository](https://github.com/Giack/wear-for-tesla), or build it yourself.
