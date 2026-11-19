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
- **Text:** every user-visible string goes in `values/strings.xml` (English) and `values-it/strings.xml` (Italian). If you do not speak Italian, say so and the maintainer will translate. A further language is a new `values-xx/strings.xml`; see [Add a language](#add-a-language).
- **Docs:** if behaviour changes, update `docs/` in the same pull request (`pip install -r requirements-site.txt && mkdocs build --strict`).
- **Commits:** imperative subject prefixed with the area, e.g. `BLE: retry the handshake once after a timeout`, body explaining why. One logical change per pull request.

## Add a language

English in `android/wear/src/main/res/values/strings.xml` is the source. Italian already lives in `values-it/strings.xml`. Another language is a new folder next to those two.

1. Copy `values/strings.xml` to `values-xx/strings.xml`. `xx` is the [Android language code](https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources) (`de`, `fr`, `es`, `pt-rBR`, …).
2. Translate every `<string>` and `<plurals>` that is **not** `translatable="false"`. Leave the `name` attributes alone. Keep every `%1$s`, `%1$d`, `%1$.1f` and the plural quantities (`one`, `other`, …) exactly as in English, or the formatted text crashes at runtime.
3. Strings marked `translatable="false"` (the app name, model names, `BLE`, `CLOUD`, the temperature pattern) stay in English. You can leave those copies in the new file or delete them; do not translate them into a different meaning.

Lint treats a missing or extra translation as a failed build. In `android/wear/build.gradle.kts`, `MissingTranslation`, `ExtraTranslation`, `StringFormatMatches` and `StringFormatInvalid` are errors, and the check set also includes `StringFormatCount`, `PluralsCandidate`, `MissingQuantity` and `UnusedQuantity`. `HardcodedStringsTest` only compares English with Italian, so a new language is not part of that test: lint is what covers it. Adding a string to English still requires the Italian twin, or that test fails.

Check it:

```sh
cd android
./gradlew :wear:lintPublicRelease
```

On a watch or emulator, set the system language to the one you added and walk through onboarding, the home screen, hold to confirm, and Settings. The JVM tests do not render those screens.

## Add a car model

**Tesla 3D models, photos, logos, wordmarks and assets taken from the official app, the website or the configurator are forbidden.** [ADR-0002](design/adr/0002-tesla-material-only-in-personal-build.md) keeps all of that out of this repository and out of the Public build. Reference photos, if you use any to measure from, stay in the git-ignored `design/car-model/refs/` folder and are never committed, embedded in a `.blend`, or shipped. What you commit is the project's own geometry, under the Apache License 2.0, with no badge and no lettering. The wheels are a project design.

The pipeline is the one already used for Model 3 and Model Y. Read both READMEs before changing a file: [`design/car-model`](design/car-model/README.md) (shape data and `build_car.py`) and [`android/wear/tools/car_model`](android/wear/tools/car_model/README.md) (model contract and `render_car.py`).

1. Describe the car with the project's own numbers. Either add a JSON next to `design/car-model/model_y_2025.json` and build it with `design/car-model/build_car.py`, or start a scene with `android/wear/tools/car_model/new_scene.py` and model in Blender. Real scale in metres, wheels on the ground at `z = 0`, front facing −Y, everything that renders in a collection named `Car`, body paint on materials whose names start with `Paint`.
2. Render the two layers and copy them into the app (commands are in the car-model README; run them from the repository root):

    ```sh
    blender -b design/car-model/<model>.blend --python android/wear/tools/car_model/render_car.py -- out/
    cp out/paint_*.webp out/details_*.webp android/wear/src/main/assets/cars/<model>/
    ```

    Sixteen frames, frame 00 the front-left three-quarter. `--preview "#RRGGBB"` writes a composite you can check. That preview is the only image that may go on the site, and only if it is this render.

3. Wire the model through the code that already assumes every `CarModel` has both renders and a vector fallback:
    - Add a value to `CarModel` in `android/core/src/main/kotlin/dev/giack/teslawear/core/setup/Setup.kt`. Onboarding and Settings already cycle `CarModel.entries`.
    - Map it in `CarRenders` (`android/core/src/main/kotlin/dev/giack/teslawear/core/car/CarRenders.kt`) to `cars/<model>`. `CarRendersTest` fails if any model has no render set.
    - Add a two-layer vector fallback on the same 200×100 grid (`android/wear/ILLUSTRATIONS.md`) and a branch in `IllustrationLayers` (`android/wear/src/main/kotlin/dev/giack/teslawear/render/IllustratedCarImages.kt`). The `when` is exhaustive, so the project does not compile without it. Extend `IllustrationLayersTest`.
    - Add a `translatable="false"` string in `values/strings.xml` and return it from `modelName()` in `android/wear/src/main/kotlin/dev/giack/teslawear/ui/UiText.kt`. Today anything that is not Model 3 is labelled Model Y, so a new model with no branch shows the wrong name.
    - In `VehicleDataMapper` (`android/core/src/main/kotlin/dev/giack/teslawear/core/fleet/VehicleDataMapper.kt`), map `vehicle_config.car_type` only when you can point at the value the Fleet API actually returns. Otherwise leave the `else -> null` branch and say so in the pull request. Do not guess a code.

4. From `android/`, run `./gradlew test :wear:lintPublicRelease`. On a watch, open onboarding and confirm the new model appears and the render tints with the paint colour.

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
