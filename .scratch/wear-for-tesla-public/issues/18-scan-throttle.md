# 18: Respect Android's Bluetooth scan limit

**What to build:** Android silently returns no results when an app starts more than five Bluetooth scans in 30 seconds, so the car would look out of range (and Fleet mode would fall back to the cloud). The app keeps track of its own scan starts; when the limit is reached it waits for the next free slot if that fits in the caller's time budget, otherwise it reports "not seen" and logs why.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Scan budget logic covered by JVM tests (5 starts per 30 s, wait or skip)
- [x] Skipped scans are written to the Bluetooth log

## Comments

- 2026-10-24: From the final review (deferred minor "repeated startScan may hit Android 5-scans/30 s throttle").
- 2026-10-24: Built. `ScanBudget` (one shared instance for the app) counts scan starts in a 30 s window; `BleScanner` waits for the next free slot when at least 1 s of scanning is left, otherwise reports not seen with "BLE scan skipped" in the log. Tests `ScanBudgetTest` (4).
