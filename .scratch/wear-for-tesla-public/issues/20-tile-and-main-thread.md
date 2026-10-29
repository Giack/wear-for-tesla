# 20: Tile refresh on view and work off the main thread

**What to build:** The Tile asks to be refreshed every 15 minutes, so it scans for the car periodically even when nobody looks at it. It refreshes when the user swipes to it instead. Loading the car pictures and decrypting stored secrets happen off the main thread.

**Blocked by:** None (can start immediately)

**Status:** needs-device-check

- [x] Car pictures load off the main thread
- [x] Tile refreshes on view, not on a timer
- [ ] Overnight log on the watch shows no Tile scans; a Tile view near the car reads over Bluetooth ("BLE direct")

## Comments

- 2026-10-24: From the final review (deferred minors on Tile freshness and main-thread work). Dropped from the list: the usage text in $ is correct (Tesla bills Fleet API in USD) and unreadable Bluetooth replies no longer crash the app.
- 2026-10-24: Built the main-thread part. `HomeViewModel` loads frames on `Dispatchers.Default`. Secrets are decrypted once and cached by `AppGraph.provisioned()`, which is now `@Synchronized`: the Tile (Default dispatcher) and the app could both build a `Provisioned` on first access, i.e. two `AndroidBleLink`s with two GATT links to the car. That first decrypt still runs on the main thread (from `onResume`), once per process; moving it means making onboarding reload asynchronous, not worth it for one small AES-GCM read. Tile part not built: since Wear OS 4 tile-enter events arrive batched and late, so replacing the 15 min freshness needs an experiment on the watch (does a visible tile get re-requested with freshness 0?).
- 2026-10-29: Tile part, from the watch's log: 247 of 340 Bluetooth scans saw 0 devices, one every 15 min day and night (Tile freshness). Root cause found on the watch: the Tile runs as a service, the app's location permission is foreground-only (`appops`: FINE_LOCATION mode foreground, a reject logged during a Tile scan), so every Tile scan was rejected, screen on or off; the Tile only ever showed state saved by the app. Fix: freshness 0 (the app and the background check push updates through `requestRefresh`); TILE_VIEW connects straight to the known car (`readKnownCarStatus`, as ticket 24) instead of scanning, only with the screen on. Tests `tileRefreshNeverScans`, `tileRefreshMergesBleStatus`. Pending on the watch: overnight log without Tile scans; a Tile view near the car after the app has seen it once.
