# 19: Fewer Fleet API reads on app open

**What to build:** Every time the app comes to the foreground it may read the car over the Fleet API, and on first launch it does so twice. Opening the app skips the cloud read when the stored state was read less than two minutes ago (Bluetooth is still tried, it is free), and the first launch opens Home once.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] App open with a fresh snapshot makes no Fleet API call (StatusRefresher test)
- [ ] Manual refresh and post-command refresh unchanged
- [ ] First launch triggers a single open

## Comments

- 2026-10-24: From the final review (deferred minor "APP_OPEN cloud read on every resume"). The vehicle state check before a cloud command stays: it decides whether to wake the car.
