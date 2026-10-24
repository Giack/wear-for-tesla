# 20: Tile refresh on view and work off the main thread

**What to build:** The Tile asks to be refreshed every 15 minutes, so it scans for the car periodically even when nobody looks at it. It refreshes when the user swipes to it instead. Loading the car pictures and decrypting stored secrets happen off the main thread.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Car pictures load off the main thread
- [ ] Tile refreshes on view, not on a timer (checked on the watch)

## Comments

- 2026-10-24: From the final review (deferred minors on Tile freshness and main-thread work). Dropped from the list: the usage text in $ is correct (Tesla bills Fleet API in USD) and unreadable Bluetooth replies no longer crash the app.
