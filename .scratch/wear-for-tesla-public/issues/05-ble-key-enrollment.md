# 05: BLE key enrollment from the watch

**What to build:** The watch creates its own BLE key (never leaves the Keystore), sends the add-key request with the driver role to the car over Bluetooth, tells the user to tap the Keycard on the centre console now (and that no Tesla-app prompt will appear), then checks acceptance with an authenticated status read. Outcomes are distinct and retryable: paired, not yet paired, insufficient privileges, out of range, no reply.

**Blocked by:** 01

**Status:** done

- [x] Add-key request construction and reply classification tested through the faked protocol transport
- [x] BLE key status recorded in the Setup state (pending → paired)
- [x] Bluetooth commands use the BLE key, never the Cloud key
- [x] Verified at the real car: maintainer's watch pairs its BLE key with the Keycard and reads status (closes the pending pairing from 2026-10-18)

## Comments

- 2026-10-18: The add-key request mirrors Tesla's `tesla-control add-key-request` (raw `ToVCSECMessage`, `SIGNATURE_TYPE_PRESENT_KEY`, driver role, Android-device form factor); its test compares against bytes produced by Tesla's Go SDK v0.4.1, decoded because Go writes the fields in another order. Acceptance is classified from an authenticated VCSEC status read: paired, not paired (`UNKNOWN_KEY_ID`), insufficient privileges, out of range, no reply. 6 tests at seam 2, two checked by temporarily breaking the code. On the watch the BLE key is created in the Android Keystore (private key never exported); Bluetooth never uses the Cloud key (imported BLE key if present, else the Keystore one, each with its own session cache). Setup screen has "Abbina chiave Bluetooth": request, Keycard prompt, checks every 3 s for 90 s, records pending/paired in the Setup state. Installed on the maintainer's watch: starts fine, Keystore key created. Pending at the car: pairing with the Keycard and a status read with the new key.
- 2026-10-23: Verified at the car. The first at-car run did pair (the car listed the watch key, driver role, Android form factor, slot 8) but the watch never saw "abbinata ✓": VCSEC status and command replies carry no `request_uuid`, only the request's routing address, and BleTransport dropped them, timing out. Found by logging every datagram from the official Go SDK on the Mac. Fix (`f64a483`): replies match by routing address (`isReplyTo`, 6 tests on the captured shapes), and infotainment reads go one category at a time, since a combined charge/climate/closures read gets `MESSAGEFAULT_ERROR_RESPONSE_MTU_EXCEEDED`. On the watch after the fix: key check → PAIRED, status over BLE, flash lights and five SetTemp commands over BLE all OK. The paired key is the imported BLE key from `secrets/` (the import takes precedence over the Keystore one). Lock and unlock over BLE also confirmed by the maintainer at the car.
