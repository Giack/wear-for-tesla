# 18: Respect Android's Bluetooth scan limit

**What to build:** Android silently returns no results when an app starts more than five Bluetooth scans in 30 seconds, so the car would look out of range (and Fleet mode would fall back to the cloud). The app keeps track of its own scan starts; when the limit is reached it waits for the next free slot if that fits in the caller's time budget, otherwise it reports "not seen" and logs why.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Scan budget logic covered by JVM tests (5 starts per 30 s, wait or skip)
- [ ] Skipped scans are written to the Bluetooth log

## Comments

- 2026-10-24: From the final review (deferred minor "repeated startScan may hit Android 5-scans/30 s throttle").
