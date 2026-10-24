# Spec: Wear for Tesla, public open-source release

**Status:** ready-for-agent

Read first: `GLOSSARY.md`, `design/adr/0001-bluetooth-mode-default-own-developer-app.md`, `design/adr/0002-tesla-material-only-in-personal-build.md`.

## Problem Statement

The app works only for its author. Using it needs a Mac, adb, a developer.tesla.com account, a GitHub Pages domain and seven manual setup steps; it cannot run at all without the Fleet API; the code hard-codes one region, one model and one maintainer domain; the UI is Italian only; and the car images come from Tesla-owned assets that cannot be published. Someone who owns a Tesla and a Wear OS watch has no realistic way to install and use it, and the maintainer cannot publish it without redistributing Tesla's intellectual property or paying for strangers' Fleet API usage.

## Solution

Publish **Wear for Tesla** as a free, Apache-2.0 open-source project with a public repository, signed APKs on GitHub Releases and a mini-site that explains installation and setup step by step.

Out of the box the app runs in **Bluetooth mode**: after installing it, the user picks their car's model, paint and wheels, stands next to the car, lets the watch create its own **BLE key**, and confirms it with the **Keycard**. From then on the watch shows the car's state and sends commands whenever the car is near, with no Tesla account, server or computer involved after installation.

Users who want remote control can enable **Fleet mode** by registering their own **Developer app** (guided by the mini-site and a helper script); the app then also reads the car's real configuration, detects the Fleet API region, and limits its own Fleet API usage to a monthly budget the user controls.

The car is drawn with project-owned **Car illustrations** tinted with the car's paint. The maintainer's **Personal build** keeps the Tesla logo and **Official renders** outside the repository, plugged in through an extension point the **Public build** does not depend on.

## User Stories

### Installing

1. As a Tesla owner with a Wear OS watch, I want to download a signed APK from the project's GitHub Releases, so that I don't have to build the app myself.
2. As a Tesla owner, I want a mini-site page that walks me through installing an APK on my watch (wireless adb from a computer, or a phone installer app), so that sideloading is not a barrier.
3. As a user, I want each new release to install over the previous one without losing my pairing, so that updating is safe.
4. As a user, I want the app name, store-style description and about screen to state that the app is not affiliated with Tesla, Inc. or Google LLC, so that I am not misled about who makes it.
5. As a developer, I want to build the Public build from source with one documented command, so that I can verify or modify what I run.

### First run and Bluetooth mode

6. As a new user, I want the first screen to explain that the app works over Bluetooth near the car and that a Tesla account is optional, so that I know what I'm setting up.
7. As a new user, I want to choose my car's model (Model 3 or Model Y), paint colour and wheel style from a short list, so that the watch shows my car.
8. As a new user, I want the watch to find my car over Bluetooth by itself, so that I don't have to type my VIN.
9. As a new user with more than one Tesla nearby, I want to pick mine from the cars the watch sees, so that I pair with the right car.
10. As a new user, I want the watch to create its own BLE key that never leaves the watch, so that no secret is copied between devices.
11. As a new user, I want the watch to tell me exactly when to tap the Keycard on the centre console, so that I confirm the BLE key in time.
12. As a new user, I want the watch to confirm when the car has accepted the BLE key, or tell me clearly that it hasn't and let me retry, so that I know whether pairing worked.
13. As a new user, I want to be told that no request will appear in the Tesla phone app for this pairing, so that I don't wait for one.
14. As a user in Bluetooth mode, I want to see lock state, doors, trunk, frunk, battery and climate whenever the car is near, so that I can check the car from my wrist.
15. As a user in Bluetooth mode, I want lock/unlock, frunk, trunk, climate and flash-lights commands to work when the car is near, so that I can control it without my phone.
16. As a user in Bluetooth mode, I want the app to say plainly that the car is out of Bluetooth range when it is, instead of a generic error, so that I understand why a command is unavailable.
17. As a user in Bluetooth mode, I want remote-only features to be hidden or explained, so that I am not offered actions that cannot work.
18. As a user, I want to remove the car and its BLE key from the watch, so that I can hand over or reset the watch.

### Fleet mode

19. As an advanced user, I want a mini-site guide that explains how to create my own Developer app, publish my Cloud key on a domain and register it, so that I can enable remote control without the maintainer's involvement.
20. As an advanced user, I want a helper script that performs the Developer-app setup steps and sends the credentials to my watch, so that I don't copy secrets by hand.
21. As an advanced user, I want to sign in to my Tesla account from the watch (completing it on my phone), so that the watch gets Fleet API access.
22. As an advanced user, I want the app to detect my Fleet API region automatically, so that it works outside Europe.
23. As an advanced user, I want my car's real model, paint and wheels to replace what I picked manually, so that the illustration matches the car.
24. As an advanced user, I want commands to go over Bluetooth when the car is near and over the Fleet API otherwise, so that I always use the free, fast path when possible.
25. As an advanced user, I want the app to wake the car through the Fleet API only to execute a command I confirmed, never just to read its state, so that I don't drain the battery or my budget.
26. As an advanced user, I want to set a monthly Fleet API budget, defaulting to Tesla's free monthly credit, so that I never pay without realising it.
27. As an advanced user, I want to see how much of my monthly budget the app has used, so that I can adjust it.
28. As an advanced user, I want cloud reads to stop when my budget is exhausted while Bluetooth keeps working, so that the app degrades instead of failing.
29. As an advanced user, I want to switch back to Bluetooth mode and have my Fleet credentials deleted from the watch, so that I can revoke the app's remote access.

### Watch surfaces

30. As a user, I want the Tile to show my car's state and quick actions, so that I can act without opening the app.
31. As a user, I want the battery complication on my watch face, so that I see the charge at a glance.
32. As a user, I want every command from the app or Tile to require hold-to-confirm, with no way to turn it off, so that I never trigger one by accident.
33. As a user, I want the confirmation screen to show the exact action and how old the known state is, so that I know what will happen.

### Car images

34. As a user of the Public build, I want to see a clean illustration of my model in my paint colour, so that the app feels like it's about my car.
35. As a user, I want the illustration to fall back to a neutral colour when the paint is unknown, so that the app never shows a broken image.
36. As the maintainer, I want my Personal build to show Official renders and the Tesla logo, so that my own watch keeps the realistic look.
37. As a contributor, I want the illustrations' source files and licence in the repository, so that I can improve them or add other models.

### Language, privacy and project

38. As an Italian or English speaker, I want the app in my language, so that every message is clear.
39. As a user, I want a privacy page stating that keys, tokens and the VIN stay on the watch and nothing is sent to the project, so that I can trust the app.
40. As a user, I want the mini-site to have a troubleshooting/FAQ page covering the errors seen in practice (car not found, insufficient privileges, key not paired, out of range), so that I can fix problems myself.
41. As a contributor, I want CI to build and test every pull request, so that I know my change doesn't break the app.
42. As the maintainer, I want pushing a version tag to build, sign and publish the Public APK, so that releasing is one step.
43. As the maintainer, I want the signing key kept only in encrypted CI secrets plus an offline backup, so that releases stay updatable and the key does not leak.
44. As the maintainer, I want the Personal build to be built only locally and to be installable next to the Public build, so that I can test both on one watch.

## Implementation Decisions

- **Two Gradle flavors, `public` and `personal`.** They differ in application ID (`io.github.giack.wearfortesla` vs `dev.giack.teslawear`), app name and image provider. The `personal` source set is git-ignored and may be absent; the Public build must compile without it. Tesla-owned material (logo, compositor client, Godot render scripts, logo extraction) moves out of the repository into the personal side.
- **Setup state (new, in `core`)** is the single source of truth for the app's configuration: which car (VIN or local name found by scan), the car profile (model, paint, wheels, with its source: manual or Fleet), the BLE key status (none, pending confirmation, paired), and whether Fleet mode is enabled. It replaces the current assumption that provisioning requires Fleet secrets. Transitions: unconfigured → car chosen → BLE key pending → paired (Bluetooth mode) → optionally Fleet mode enabled/disabled. Fleet configuration overrides a manual car profile but disabling Fleet mode keeps the last known profile.
- **Fleet path becomes optional.** Command routing and status refresh accept the absence of a Fleet link: without it, commands run only over Bluetooth and report "out of range" when the car isn't reachable; reads never fall back to the cloud. With it, current behaviour stays (BLE first, cloud fallback, wake only for confirmed commands, usage meter).
- **Usage meter limit becomes user-configurable**, defaulting to Tesla's free monthly credit; the existing exhausted-budget behaviour is kept.
- **BLE key enrollment** is done by the watch: it generates the key pair in the Android Keystore, sends the VCSEC add-key request (driver role) over Bluetooth, prompts for the Keycard, then verifies acceptance with an authenticated status read, distinguishing "not yet paired" from "insufficient privileges" and "out of range". The BLE key is separate from the Cloud key in both modes.
- **Car discovery without a VIN:** in Bluetooth mode the car is identified by the advertised local name chosen during setup; the VIN is learned when available (Fleet mode) but not required.
- **Fleet region detection** uses the Fleet API's user-region endpoint after login instead of a hard-coded base URL; the Developer app domain and client credentials come from the imported configuration, never from code constants.
- **Image provider interface:** given the car profile and a view, returns the image to draw. The Public implementation returns a Car illustration (stylised layered SVG for Model 3 and Model Y, three-quarter and side views) with the paint layer tinted to the profile's colour, falling back to neutral grey. The Personal implementation returns Official renders when present.
- **Credentials import for Fleet mode** keeps the existing adb-based helper script, rewritten to be generic (user's own domain, no maintainer defaults) and documented on the mini-site.
- **Localisation:** all user-visible strings move to Android string resources, English default with Italian translation.
- **Branding:** app name "Wear for Tesla"; disclaimer "Not affiliated with Tesla, Inc. or Google LLC" in the about screen, README and mini-site; no Tesla logo in the Public build.
- **Repository and site:** a new public repository starting from a clean snapshot; Apache-2.0 licence, `PRIVACY.md`, README; mini-site with MkDocs Material from `docs/`, internal design docs moved out of the published folder; GitHub Actions for PR build/test and tag-triggered signed release.

## Testing Decisions

- Good tests exercise external behaviour through the agreed seams and survive refactors; they never assert on private state or call order.
- **Seam 1: command routing and status refresh with fake Bluetooth and Fleet links.** Covers Bluetooth mode without a Fleet link, Fleet mode routing and fallback, out-of-range reporting, and the configurable budget. Prior art: the existing command-router and status-refresher tests in `core`.
- **Seam 2: protocol transport faked.** Covers BLE key enrollment: the add-key request built for the driver role, and classification of the car's replies (accepted, not paired, insufficient privileges, no reply). Prior art: the command-executor and session tests in `protocol`.
- **Seam 3: Setup state.** Covers every transition, car profile precedence (manual vs Fleet), and reset. Prior art: controller tests in `wear` (home and confirm controllers) using plain JVM tests with fakes.
- **Seam 4: image provider.** Covers illustration choice per model and view, paint tinting and neutral fallback, and that the Public build works with no personal provider present. Prior art: the compositor URL test (which moves to the personal side).
- Not covered by automated tests, verified by hand on a real watch and car: GATT/Bluetooth behaviour, Keystore, SVG rendering quality, Tile and complication rendering, release signing, mini-site.

## Out of Scope

- A shared Fleet API Developer app run by the maintainer (rejected in ADR-0001).
- Official renders or the Tesla logo in the Public build, including runtime compositor fetching (ADR-0002).
- Play Store publication (possible later; nothing here should prevent it).
- A phone companion app.
- Multiple cars per watch.
- Car illustrations for Model S, Model X and Cybertruck (open to contributions).
- Handing off Fleet credentials through a web page instead of the adb script.

## Further Notes

- Verified 2026-10-18 at the car: the Cloud key paired through `tesla.com/_ak` is rejected over Bluetooth with `MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES`, both from the watch and from Tesla's own `tesla-control`; the add-key request is confirmed only with the Keycard on the centre console, with no prompt in the Tesla phone app. The maintainer's own BLE key pairing is still pending and will validate the enrollment flow end to end.
- The car's Bluetooth protocol exposes state, not configuration (no paint, wheels or model in the VCSEC or car-server messages), which is why Bluetooth mode needs a manual car profile.
- The current repository's history contains the Tesla-asset extraction scripts, hence the clean-snapshot public repository.
