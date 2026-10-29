# 22: Show whether the frunk is open

**What to build:** The frunk button on Home and the Tile always looked closed: the app never read the frunk state, although every source reports it (Bluetooth VCSEC `closureStatuses.frontTrunk`, readable even while the car sleeps; Bluetooth infotainment `door_open_trunk_front`; Fleet API `vehicle_state.ft`). The snapshot carries the frunk state, a successful open marks it open at once, and both surfaces highlight the button while it is open.

**Blocked by:** None

**Status:** needs-device-check

- [x] Frunk state mapped from VCSEC, infotainment and Fleet API (mapper tests)
- [x] Persisted, merged and set after Open frunk (codec and snapshot tests)
- [x] Home and Tile highlight the button when open (Tile test)
- [ ] Open the frunk from the watch, then close it by hand: the button follows within one Bluetooth read

## Comments

- 2026-10-29: Reported by the maintainer ("buttons that don't keep a state, like the frunk"). Root cause: no frunk field in `Snapshot`; Tile hard-coded `active = false`. The command stays Open frunk when it is already open (the frunk cannot be closed remotely).
