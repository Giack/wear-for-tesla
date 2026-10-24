# 03: Public/Personal flavors and image provider

**What to build:** Split the app into a Public build and a Personal build. Introduce an image provider extension point: given the car profile and a view it returns the image to draw. All Tesla-owned material (logo, configurator compositor client, Godot render scripts, logo extraction) moves to the git-ignored personal side, which may be absent. The Public build compiles and runs without it, showing a neutral placeholder car; the maintainer's Personal build looks exactly as today. Public application ID `io.github.giack.wearfortesla`, Personal `dev.giack.teslawear`; both install side by side.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] A clean checkout without the personal folders builds and tests green
- [x] No Tesla-owned file or Tesla asset URL remains in tracked files (grep check documented in the ticket comments)
- [x] The Personal build shows Official renders and the Tesla logo as before
- [x] Both builds installed together on one watch run independently
- [x] Image provider covered by a JVM test that the Public build works with no personal provider present

## Comments

- 2026-10-18: Flavors `public`/`personal` with a `CarImages` extension point; `personal` is declared only when `android/wear/src/personal/` exists. Moved to git-ignored folders: render cache and compositor client (+ test), Tesla logo and launcher icon, Godot render and logo extraction scripts, the compositor option helper (`setup/personal/`). Verified: a copy of only the publishable files has no personal tasks and builds/tests the Public build green; grep of publishable files finds no compositor URL, Tesla app package or extraction tooling (excluding internal design docs, handled by ticket 11/13); the Public APK's dex has 0 compositor URLs vs 1 in the Personal APK; Public APK label "Wear for Tesla", id `io.github.giack.wearfortesla`. Pending on device: Personal build unchanged visually, both builds side by side. Note: the personal folders are no longer versioned; back them up (private repo).
- 2026-10-18 21:07, on device: Personal build looks identical (Tesla logo, Official render); Public build (`io.github.giack.wearfortesla`) installed next to it, opens on setup, no crash. Done.
