# 10: English/Italian localisation and branding

**What to build:** Every user-visible string moves to Android resources with English as default and an Italian translation. The app is named Wear for Tesla and its about screen carries the disclaimer "Not affiliated with Tesla, Inc. or Google LLC".

**Blocked by:** 06, 07

**Status:** done

- [x] No hard-coded user-visible strings remain (lint check)
- [x] Watch in English and in Italian shows correct text on every screen, Tile and complication
- [x] About screen with name, version, licence and the double disclaimer

## Comments

- 2026-10-23: All user-visible text is in `wear/src/main/res/values/strings.xml` (English, default) and `values-it/strings.xml`. `core` and `protocol` stay Android-free, so they now return typed values instead of Italian text: `Age` (was `ageLabel`), `Progress.WAKING_CAR` (was "Sveglio l'auto…"), and `ResolvedAction` carries only the command; the wording is chosen in `wear` (`ui/UiText.kt`). Controllers emit `UiText` (resource id + args) resolved only when drawn, so their JVM tests compare ids and Home colours errors with a `messageIsError` flag instead of matching Italian prefixes. Numbers are formatted by the resources, so they follow the language (21.5° / 21,5°, 0.58 $ / 0,58 $); counts use plurals. The lint check is two layers: `HardcodedStringsTest` (Kotlin literals that reach the screen, manifest labels, EN/IT key parity, matching format arguments) and Android lint (`HardcodedText`, `MissingTranslation`, string-format and plural checks, 0 findings). The Tesla sign-in page opens in the watch's language instead of always `it-IT`. About screen (Settings → About): app name, version, Apache 2.0, "Not affiliated with Tesla, Inc. or Google LLC." and the trademark line. On-watch diagnostic log lines are English. Seen on the watch (Personal build, per-app locale `en-US` then system `it-IT`): every Home, Settings and About screen, the Tile, the confirmation screen and the battery complication on the watch face; this led to shortening the English "Charge port" button label to "Port". Not seen on the device: the climate complication (not on the current face; its text is ON/OFF/°, the same in both languages) and the complication names in the watch-face editor. The Personal build keeps its own `app_name` ("Tesla"); the Public build shows "Wear for Tesla".

