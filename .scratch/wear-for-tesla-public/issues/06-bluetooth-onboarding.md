# 06: Bluetooth mode onboarding end to end

**What to build:** A brand-new user goes from first launch to a working watch without any computer or Tesla account: an intro explaining Bluetooth mode, the car profile picker, a scan that lists nearby Teslas by name so the user picks theirs (no VIN), BLE key enrollment with the Keycard, then the home screen with live state and commands. The user can remove the car and its BLE key later.

**Blocked by:** 02, 04, 05

**Status:** needs-car-check

- [ ] Fresh install of the Public build completes onboarding at the car and shows state and commands
- [x] With several Teslas nearby the user can choose; with none, a clear retry message
- [x] Remove car resets the Setup state and deletes the BLE key
- [x] Onboarding flow logic covered via the Setup state tests

## Comments

- 2026-10-18 (found during ticket 07): Bluetooth commands need the VIN, because Tesla's signatures include it (personalization tag). "No VIN" in this ticket must become "the user enters the VIN" (17 characters; e.g. typed on the phone keyboard), checked against the scanned local name, which is derived from the VIN, so a typo is caught before pairing.
- 2026-10-23: Built. The VIN identifies the car, so "choose among several Teslas" became automatic: the scan looks for the name derived from the typed VIN; Teslas nearby but none matching → "controlla il VIN" (typo), none at all → "avvicinati e riprova". Onboarding = intro → car profile (model, paint, wheels) → VIN (Wear text input: watch keyboard, voice or phone; case, spaces and dashes ignored; I/O/Q rejected) → scan → BLE key with the Keycard (the pairing flow is now shared with Settings as `BleKeyPairing`) → Home. Step logic in `core` (`onboardingStep`, `Vin`, `CarSearch`), 10 tests. Settings: Fleet items only in Fleet mode; "Rimuovi auto" (two taps) deletes Setup state, Keystore BLE key, imported secrets, tokens, sessions, pins and snapshot; Back returns to Home; Home hides the API line in Bluetooth mode. Checked on the watch with the Public build: remove car → intro → profile → VIN typed lowercase → permissions requested → scan at home "Nessuna Tesla vicina". Pending at the car: scan finds it, Keycard pairing with the Keystore key, Home with state and commands. No car picture in the profile step yet.
