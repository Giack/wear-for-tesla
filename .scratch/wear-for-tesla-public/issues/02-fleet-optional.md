# 02: Fleet link optional (Bluetooth mode routing)

**What to build:** With Fleet mode disabled the app works over Bluetooth only: commands run when the car is near, status reads never fall back to the cloud, and when the car isn't reachable the user sees a clear "car out of Bluetooth range" message instead of a generic error. Remote-only behaviour is unavailable. With Fleet mode enabled, today's behaviour is unchanged (Bluetooth first, cloud fallback, wake only for confirmed commands).

**Blocked by:** 01

**Status:** done

- [x] Command routing and status refresh accept the absence of a Fleet link; covered through the existing router/refresher test seam with fake links
- [x] Out-of-range is a distinct outcome shown to the user
- [x] No code path tries the Fleet API when Fleet mode is disabled (tested)
- [x] Fleet mode behaviour and its tests unchanged

## Comments

- 2026-10-18: The Fleet link is optional in command routing and status refresh; the app passes it only when the Setup state has Fleet mode enabled, so Bluetooth mode cannot reach the Fleet API by construction. New outcome "out of Bluetooth range" (car not reachable, or Bluetooth link dropped) shown by home and confirm screens. Five tests at seam 1 (`BluetoothModeTest`), out-of-range ones checked by temporarily breaking the code; existing Fleet-mode tests unchanged and green. Pending on device: maintainer's install (Fleet mode via migration) behaves as before.
- 2026-10-18 21:07, on device: Personal build in Fleet mode unchanged; away from the car it scans Bluetooth, then reads over Fleet (usage data 65 → 66, no wake, no command). Done.
