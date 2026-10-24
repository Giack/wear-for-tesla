# 12: Signed release pipeline

**What to build:** Pushing a `v*` tag builds the Public APK, signs it with the project's release key and attaches it to a GitHub Release. The keystore lives only in encrypted repository secrets plus an offline backup kept by the maintainer. The Personal build is never built in CI.

**Blocked by:** 11

**Status:** ready-for-human

- [ ] Tag push produces a signed APK on a GitHub Release
- [ ] Installing a later release over an earlier one keeps the watch's pairing
- [ ] Keystore backup confirmed by the maintainer
