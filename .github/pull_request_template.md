## What and why

<!-- What does this change do, and which issue does it address? (Fixes #…) -->

## How it was verified

- [ ] `cd android && ./gradlew test :wear:lintPublicRelease :wear:assemblePublicRelease` passes
- [ ] New or changed logic has JVM tests
- [ ] Behaviour that only a watch or a car can show was checked on a device (say which watch and what you did), or is listed below as not verified

## Checklist

- [ ] No Tesla-owned material (logos, renders, 3D models, assets extracted from Tesla's apps)
- [ ] No personal data in code, tests, screenshots or logs (VIN, keys, tokens, locations)
- [ ] Commands still need hold to confirm, and nothing new wakes the car or calls the Fleet API in the background
- [ ] User-visible text in English and Italian (`values/` and `values-it/`)
- [ ] Docs in `docs/` updated if behaviour changed
