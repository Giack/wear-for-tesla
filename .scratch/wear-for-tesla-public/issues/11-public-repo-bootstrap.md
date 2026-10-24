# 11: Public repository bootstrap

**What to build:** Create the public `wear-for-tesla` repository from a clean snapshot (single initial commit) containing no Tesla-owned material, with the Apache-2.0 licence, README (with disclaimer), PRIVACY.md stating that keys, tokens and the VIN stay on the watch and nothing reaches the project, and a CI workflow that builds and runs the JVM tests on every pull request.

**Blocked by:** 03

**Status:** needs-archive

- [x] Public repo exists with clean history; personal folders absent and git-ignored
- [x] LICENSE, README, PRIVACY.md present
- [x] CI green on a test pull request
- [ ] This private repository archived as the build history

## Comments

- 2026-10-24: Created github.com/Giack/wear-for-tesla from a `git archive` of private `7f90660`, single commit authored with the GitHub noreply address; `design/superpowers` (personal-app spec and plan, compositor notes) left out. Audited: no VIN, serials, client credentials, real keys (test PEMs differ), images or Tesla assets. `ci.yml` (test, lint, Public APK) green on push and on test PR #1 (closed). Pending: archive this private repository (it has no remote yet).
