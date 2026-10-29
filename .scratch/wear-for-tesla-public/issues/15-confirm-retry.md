# 15: Retry from the confirmation screen after an error

**What to build:** When a command from the Tile or a complication fails, the confirmation screen keeps the hold button enabled, but holding it again does nothing. Holding it after an error sends the same command again, but only when the command certainly did not run: the trunk command actuates (opens or closes), so after an unknown outcome a retry could undo it. A success or failure also gives a haptic signal.

**Blocked by:** None (can start immediately)

**Status:** needs-device-check

- [x] Hold after an error resends the resolved command (ConfirmController test)
- [x] Hold while sending, after success or after an unknown outcome does nothing
- [ ] Haptic on outcome (checked on the watch)

## Comments

- 2026-10-24: From the final review of the personal app (deferred minor "ERROR phase enables hold button but confirm ignores non-READY").
- 2026-10-24: Built. New phase `UNCERTAIN` for `CommandResult.Unknown` (red message, hold disabled); `ERROR` (no connection, out of range, car declined, sign in) re-enables the hold. Haptic Confirm on success, Reject on error/uncertain. Tests `holdAfterAnErrorSendsTheSameCommandAgain`, `holdAfterAnUnknownOutcomeSendsNothing`. Pending: feel the haptics on the watch.
