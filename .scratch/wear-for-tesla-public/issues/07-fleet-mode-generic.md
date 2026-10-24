# 07: Generic Fleet mode

**What to build:** Fleet mode works with any user's own Developer app: the helper script uses the user's domain and credentials (no maintainer defaults), the app detects the Fleet API region after login instead of a hard-coded URL, the car's real configuration replaces the manual car profile, and disabling Fleet mode deletes the Fleet credentials and tokens from the watch.

**Blocked by:** 01, 02

**Status:** done

- [x] No maintainer domain, region or client constants remain in code
- [x] Region detection covered by a Fleet API client test
- [x] Fleet-sourced car profile overrides the manual one (Setup state test)
- [x] Disabling Fleet mode wipes credentials; the app keeps working in Bluetooth mode
- [x] Maintainer's Personal build verified end to end with his own Developer app

## Comments

- 2026-10-18: No maintainer domain, region or package left in code or `setup.sh` (domain/repo/redirect derived from the user's GitHub account and app package in `secrets/config.env`; partner registration in NA and EU; `push-watch` also sends the domain; `PACKAGE_OVERRIDE` targets another build). The app finds the user's regional server after login (`users/region`, trying NA then EU, skipping 412 "not registered in this region") and remembers it; reads ask for `vehicle_config` too, mapped to the car profile (Tesla paint names to the generic paints; Model S/X/Cybertruck leave the profile alone). Fleet read failures are now reported and logged instead of swallowed. The app's setup is the Setup state (car + VIN) with Fleet pieces only in Fleet mode; "Disattiva Fleet mode" deletes Fleet credentials, tokens and region and keeps Bluetooth. Tests: region discovery and 412 fallback (mock server), config mapping (fixture), refresher hands over config and reports errors.
- On device (Personal build): real `users/region` reached — NA answered 412 (app registered only in EU), EU found and saved; no crash. Pending: profile from Fleet (car asleep, 408 carries no config), "Disattiva Fleet mode" on the Public build (watch dropped off the network).
- 2026-10-18 22:00, on device: with the car awake the Personal build replaced the picked profile with the Fleet one (source FLEET, Model Y, paint RED from Tesla's name, wheels `Crossflow19`). "Disattiva Fleet mode" on the Public build (secrets pushed with `PACKAGE_OVERRIDE`): Fleet mode off, region deleted, VIN and car kept, encrypted secrets 630 → 269 bytes (only empty BLE fields left; since then the file is deleted when nothing worth keeping remains, not re-tested on device); the app reopens on setup with Fleet steps disabled, no crash, no Fleet call. Fixed on the way: `push-watch` started the import activity relative to the application id, which failed for the Public build. Done.
