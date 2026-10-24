# 08: Configurable Fleet API budget

**What to build:** In Fleet mode the user sets a monthly Fleet API budget, defaulting to Tesla's free monthly credit, and can see how much has been used. When the budget is exhausted cloud reads stop while Bluetooth keeps working; confirmed commands follow the existing rule.

**Blocked by:** 07

**Status:** done

- [x] Budget limit configurable from the settings screen, default equal to the free credit
- [x] Usage shown to the user
- [x] Exhausted-budget behaviour covered by the usage meter and refresher tests

## Comments

- 2026-10-23: Budget picker in Setup (Fleet mode): 2/5/10/20/50 $ per month, default 10 $ (Tesla's monthly discount), stored in the `usage` prefs; `UsageMeter` levels follow it. Setup shows this month's spending with reads/commands/wakes; Home shows "API: x $ / budget $". Same change, asked by the maintainer: refresh behaves like the phone app. Fleet API has no data for a sleeping car (vehicle_data answers 408, still billed), so the stored snapshot is shown and marked asleep ("auto in sospensione · dati di 2 h fa") instead of the old amber "dati vecchi"; only "↻ Aggiorna" wakes the car. BLE status also records the sleep state. Home: messages share one fixed-height slot with the refresh button (a message appearing no longer shifts the climate controls), larger 48 dp −/+ buttons, and a tap during a set-point send no longer cancels that send (found by a new test). Seen on the watch the same evening: Home with the fixed slot and −/+ buttons, Setup budget and usage (which now reloads on open so it matches Home).
