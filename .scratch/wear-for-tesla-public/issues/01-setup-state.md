# 01: Setup state in core

**What to build:** Prefactor. Introduce the Setup state as the single source of truth for the app's configuration: the chosen car (VIN or advertised local name), the car profile (model, paint, wheels, and whether it came from the user or from Fleet mode), the BLE key status (none, pending confirmation, paired) and whether Fleet mode is enabled. The app stops assuming that being set up means having Fleet secrets. The maintainer's existing setup must keep working unchanged after migration.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Setup state lives in `core` and is covered by JVM tests for every transition: unconfigured → car chosen → BLE key pending → paired → Fleet mode enabled/disabled, plus reset
- [x] Car profile precedence is tested: a Fleet-sourced profile replaces a manual one; disabling Fleet mode keeps the last known profile
- [x] Existing installs (secrets imported via the setup script) migrate to a Setup state with Fleet mode enabled and keep working
- [x] All existing tests pass

## Comments

- 2026-10-18: Setup state implemented in `core` with 10 JVM tests at the agreed seam (transitions, car profile precedence, Fleet mode, reset, migration). Wired into the app: persisted in its own preferences, migration runs when the imported secrets are loaded. All tests and the release build pass. Pending: on-device check that the maintainer's install migrates (watch unreachable over adb at the time). Links (Bluetooth/Fleet) still read the imported secrets; moving them onto the Setup state is tickets 02 and 05.
- 2026-10-18 21:06, on device: maintainer's install migrated to the Setup state (Fleet mode on, BLE key none, car local name `S594b5b8d14ca042bC` = the name the car advertises, confirming the VIN derivation). Done.
