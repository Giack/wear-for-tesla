# 21: No repeated Fleet API reads while the car sleeps

**What to build:** When the Fleet API says the car is asleep, the stored state keeps its old date (the data is from before it fell asleep), so the two-minute rule from ticket 19 never applies and every app open makes another billed read. The snapshot also records when the car was last checked; opening the app skips the cloud read when that check is under two minutes old, while the screen keeps showing the data's real age.

**Blocked by:** 19

**Status:** done

- [x] App open right after an "asleep" answer makes no Fleet API call (StatusRefresher test)
- [x] The check time survives a restart (SnapshotCodec test); the age shown is still the data's age

## Comments

- 2026-10-24: Found while building ticket 19.
- 2026-10-24: Built. `Snapshot.checkedAtMs` (persisted as `checked`) is set when the Fleet API answers asleep; `lastCheckMs` = max(data date, check). APP_OPEN uses `lastCheckMs` for the 2-minute rule; ages on screen still use `updatedAtMs`. Tests `appOpenDoesNotAskAgainRightAfterFindingTheCarAsleep`, codec round trip.
