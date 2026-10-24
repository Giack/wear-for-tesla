# 17: Bluetooth link robustness

**What to build:** Three small fixes in the GATT link. The reassembler drops a half-received message when the next chunk arrives more than one second later, as Tesla's Go SDK does, so one lost chunk cannot corrupt every later reply. GATT callback fields are safely published across threads and the write path cannot crash when the car disconnects mid-write. A disconnect while waiting for a reply ends the wait at once (outcome unknown) instead of after the full timeout.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Reassembler resets after a 1 s gap between chunks (BleFraming test)
- [ ] No `!!` on GATT state written from callbacks; fields `@Volatile`
- [ ] Disconnect during a reply wait fails fast (checked on the watch)

## Comments

- 2026-10-24: From the final review (deferred minors on BleTransport and Reassembler).
