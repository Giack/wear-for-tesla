# Privacy

Wear for Tesla has no server, no account, no analytics and no advertising. **Nothing is sent to the project or its maintainer.**

## What stays on the watch

- The **BLE key** is created in the watch's Android Keystore and never leaves the watch.
- The **VIN**, the car profile, the car's Bluetooth address (for the background check) and the car's last known state are kept in the app's private storage.
- In Fleet mode, the **Cloud key**, your Developer app's credentials and your Tesla sign-in **tokens** are kept in the app's private storage too. *Settings → Turn off Fleet mode* deletes them; *Settings → Remove car* deletes everything.

## Who the app talks to

- **Your car**, directly over Bluetooth.
- In Fleet mode only, **Tesla's** sign-in and Fleet API servers, through your own Developer app, to read the car's state and run the commands you confirm. Tesla's privacy policy applies to that traffic.
- Wear OS hands the Tesla sign-in to your phone, where you complete it.

The Fleet mode helper script (`setup/setup.sh`) runs on your computer and talks only to GitHub (to publish your public key), Tesla (to register your domain) and your watch (over `adb`). The private key and credentials it creates stay in `secrets/` on your computer, which git ignores.

The source code is in this repository, so you can verify all of the above or build the app yourself.
