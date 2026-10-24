# Wear for Tesla

A free, open-source Wear OS app that shows your Tesla's state and sends it commands from your wrist: over Bluetooth when the car is near, and optionally through Tesla's Fleet API from anywhere.

> **Not affiliated with Tesla, Inc. or Google LLC.** Tesla is a trademark of Tesla, Inc. Wear OS is a trademark of Google LLC. See the [disclaimer](https://giack.github.io/wear-for-tesla/disclaimer/).

## Documentation

The full guide is at **[giack.github.io/wear-for-tesla](https://giack.github.io/wear-for-tesla/)**:

- [Install the app](https://giack.github.io/wear-for-tesla/install/) on a Wear OS watch
- [Bluetooth mode](https://giack.github.io/wear-for-tesla/bluetooth-mode/): pair the watch with your car using the Keycard
- [Fleet mode](https://giack.github.io/wear-for-tesla/fleet-mode/): remote control with your own Developer app and the helper script
- [Fleet API budget](https://giack.github.io/wear-for-tesla/budget/)
- [Troubleshooting](https://giack.github.io/wear-for-tesla/troubleshooting/)
- [Privacy](https://giack.github.io/wear-for-tesla/privacy/): keys, tokens and the VIN stay on the watch

## Building

```sh
cd android
./gradlew :wear:assemblePublicRelease   # APK in wear/build/outputs/apk/public/release/
./gradlew test                          # JVM tests
```

The site is built with [MkDocs Material](https://squidfunk.github.io/mkdocs-material/) from `docs/`: `pip install -r requirements-site.txt && mkdocs serve`. Design notes and decision records are in `design/`.

## Privacy and licence

Nothing is sent to the project: see [PRIVACY.md](PRIVACY.md). Licensed under the [Apache License 2.0](LICENSE).
