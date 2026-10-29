# 24: Background check twice an hour

**What to build:** While the app is closed, the watch keeps the stored state current so surfaces show a recent state: twice an hour, battery not low, a direct Bluetooth connection to the car seen last time (no scan: Android returns nothing to background scans without background location, and blocks unfiltered scans with the screen off); if the car is not near, in Fleet mode one Fleet API read (408 for a sleeping car, never a wake), skipped when the last check is under 25 minutes old or the budget is used up. Bluetooth mode does only the Bluetooth part. Every run is logged.

**Blocked by:** 21

**Status:** needs-device-check

- [x] Refresher BACKGROUND trigger: known car over Bluetooth without scanning, else one cloud read, never a wake, skips when recent or out of budget, Bluetooth only in Bluetooth mode (StatusRefresher tests)
- [x] Log line per run (BackgroundOutcome tests); runs are scheduled (seen in jobscheduler on the watch, first run logged)
- [x] Mini-site (budget, home) and privacy updated; the car's Bluetooth address is forgotten with the car
- [ ] After a day: runs happen about twice an hour; a Bluetooth run near the car succeeds with the screen off (Tesla BLE address stable)

## Comments

- 2026-10-29: Asked by the maintainer ("a background task like TeslaMate, never waking the car, as frugal as possible; twice an hour"). Replaces the earlier constraint "no Fleet API polling". One `vehicle_data` call per run instead of TeslaMate's state-then-data, since Fleet API answers 408 for a sleeping car without waking it. WorkManager 2.11.2, `PeriodicWorkRequest` 30 min, `requiresBatteryNotLow`. The car's address is saved whenever a foreground scan finds it (`ble_known_car` prefs); the first Bluetooth background run needs one sighting near the car first. Device evidence also exposed a race in ticket 18's scan budget (fixed in the same change, see ticket 18).
