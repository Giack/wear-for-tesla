# 04: Manual car profile and first Car illustration

**What to build:** The user can pick their car's model, paint and wheel style from a short list, and the Public build draws a project-owned Car illustration of a Model Y in three-quarter view, tinted with the chosen paint (neutral grey when unknown). The illustration is a stylised layered vector drawing with a separate paint layer; its source file and licence live in the repository.

**Blocked by:** 01, 03

**Status:** done

- [x] Picker stores the car profile through the Setup state
- [x] Image provider returns the Model Y three-quarter illustration with the paint colour applied; tested for tint and neutral fallback
- [x] Illustration source and licence committed; verified visually on the watch (home screen and Tile)
- [x] Personal build still prefers Official renders

## Comments

- 2026-10-18: Illustration choice in `core` (model, view, paint colour; unknown paint or no profile → neutral grey Model Y), 3 tests at seam 4. Six generic paints (white, black, grey, silver, blue, red), not Tesla trade names. Drawing: stylised Model Y three-quarter front, two layers on a 200×100 grid (paint layer multiplied by the paint colour, fixed details on top, light roof edge so dark paints read on black); original artwork, Apache-2.0, notes in `android/wear/ILLUSTRATIONS.md`. Public build draws it on home and Tile; Personal build prefers Official renders and falls back to it. Picker in the setup screen ("La tua auto": model, colour, wheels) writes the car profile; the Tile reloads its image when the profile changes. Checked on the watch: home screen and Tile in red (renders temporarily hidden), all six paints previewed locally. Model 3 and side view: ticket 09.
