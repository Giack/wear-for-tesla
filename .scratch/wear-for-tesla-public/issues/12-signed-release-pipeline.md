# 12: Signed release pipeline

**What to build:** Pushing a `v*` tag builds the Public APK, signs it with the project's release key and attaches it to a GitHub Release. The keystore lives only in encrypted repository secrets plus an offline backup kept by the maintainer. The Personal build is never built in CI.

**Blocked by:** 11

**Status:** needs-device-check

- [x] Tag push produces a signed APK on a GitHub Release
- [ ] Installing a later release over an earlier one keeps the watch's pairing
- [ ] Keystore backup confirmed by the maintainer

## Comments

- 2026-10-24: `release.yml`: `v*` tag → tests, Public APK signed from `WEAR_KEYSTORE*` secrets, refuses a debug-signed APK, versionCode = major·10⁶ + minor·10³ + patch, suffix tag → pre-release, asset `wear-for-tesla-<version>.apk`. Release key: EC P-256 PKCS12, cert SHA-256 528ab5ca…785df0, at `secrets/release.jks` + `secrets/release.env` (git-ignored), uploaded as repo secrets. `v0.1-rc1` pre-release published and verified signed with it. Pending: offline backup of the keystore (maintainer); upgrade-keeps-pairing check on the watch with a second tag.
