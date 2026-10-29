# Contributing to Wear for Tesla

Thanks for helping. This is a small spare-time project that controls real cars, so changes go through review and a few rules are firm. Please read this page before opening a pull request.

## Before you start

- **Open an issue first** for anything bigger than a typo or an obvious bug fix, and say you would like to work on it. Agreeing on the approach before the code saves both of us time.
- Bug reports and feature requests use the issue forms. **Never post your VIN, keys, tokens or an unedited Bluetooth log.**
- Security problems are reported privately: see [SECURITY.md](SECURITY.md).

## What the project will not accept

These come from the project's design decisions (see `design/adr/`) and are not open to negotiation in a pull request:

- **Tesla-owned material** of any kind: logos, official renders, 3D models or other assets extracted from Tesla's apps, configurator image URLs ([ADR-0002](design/adr/0002-tesla-material-only-in-personal-build.md)). Car illustrations must be the project's own work, with their source and licence in the repository.
- **Weaker command safety:** every command needs hold to confirm and shows the exact action and the age of the known state; an unknown outcome is never retried automatically.
- **Waking the car or spending the Fleet API budget behind the user's back:** reads never wake the car; the only background activity is the documented twice-an-hour check ([budget page](docs/budget.md)).
- **Sending anything to the project or to third parties:** no analytics, crash reporting or servers of our own. The app talks only to the car and, in Fleet mode, to Tesla ([PRIVACY.md](PRIVACY.md)).

## Development setup

You need JDK 21 and the Android SDK (compile SDK 37). Only the Public build is needed; the Personal build depends on files that are not in this repository and is never built in CI.

```sh
cd android
./gradlew test :wear:lintPublicRelease :wear:assemblePublicRelease   # exactly what CI runs
```

The APK lands in `android/wear/build/outputs/apk/public/release/`. To try it on a watch, see the [install guide](https://giack.github.io/wear-for-tesla/install/).

Project layout:

| Module | What it holds |
|---|---|
| `android/protocol` | Tesla's vehicle-command protocol: sessions, signing, framing (pure Kotlin) |
| `android/core` | Routing, refresh policy, Fleet API client, setup state, usage budget (pure Kotlin) |
| `android/wear` | The Wear OS app: UI, Bluetooth, Tile, complications, storage |
| `docs/` | The [mini-site](https://giack.github.io/wear-for-tesla/) (MkDocs Material) |
| `design/` | Decision records and design notes (not published) |

## Making a change

- **Tests:** logic belongs in `protocol` or `core` (or in plain classes in `wear`) where JVM tests can cover it. Add a failing test first, then the fix. Behaviour only a watch or a car can show (Bluetooth, Keystore, Tile rendering) is described in the pull request with what you checked on which device.
- **Style:** match the code around you: naming, comment density (comments say *why*), small functions, no new dependency without a reason in the pull request.
- **Text:** every user-visible string goes in `values/strings.xml` (English) and `values-it/strings.xml` (Italian). If you do not speak Italian, say so and the maintainer will translate.
- **Docs:** if behaviour changes, update `docs/` in the same pull request (`pip install -r requirements-site.txt && mkdocs build --strict`).
- **Commits:** imperative subject prefixed with the area, e.g. `BLE: retry the handshake once after a timeout`, body explaining why. One logical change per pull request.

## Pull requests and review

`main` is protected:

- Changes reach `main` only through a pull request; direct pushes and force pushes are blocked.
- The **Build and test** check must pass on a branch that is up to date with `main` (it is skipped, and counts as passed, when nothing under `android/` changes).
- The maintainer (code owner) must approve. Approvals are dismissed when new commits are pushed, and every review conversation must be resolved.
- Pull requests are **squash-merged**, so the pull request title becomes the commit subject.
- Workflows from first-time contributors' forks run only after the maintainer approves them.

Expect a first review within a couple of weeks. Small, focused pull requests get merged much faster than large ones.

## Licence

The project is licensed under the [Apache License 2.0](LICENSE). By submitting a contribution you agree that it is licensed under the same terms (section 5 of the licence), and that you have the right to submit it.
