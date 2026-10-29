# 23: One app instance however it is opened

**What to build:** Opening the app from the icon, a complication or the Tile stacked a new copy of the main screen each time, so Back walked through several copies before leaving. The main screen is a single instance that every entry point reuses.

**Blocked by:** None

**Status:** done

- [x] Reproduced on the Galaxy Watch (launcher, home, complication-style start ×2: one task with 3 MainActivity records)
- [x] After the fix the same sequence leaves one record and one Back returns to the watch face

## Comments

- 2026-10-29: Reported by the maintainer. Root cause: `MainActivity` used the default `standard` launch mode; Samsung's launcher and watch faces start it with `NEW_TASK` intents that differ from the task's root intent, and each start added an instance (the Google Wear OS 5 emulator does not reproduce it). Fix: `launchMode="singleTask"`; a reopen goes through `onResume`, which already refreshes Home. Verified on SM-L350 with the Personal build.
