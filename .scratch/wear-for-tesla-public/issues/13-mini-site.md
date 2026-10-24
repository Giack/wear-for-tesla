# 13: Mini-site

**What to build:** A GitHub Pages mini-site built with MkDocs Material explains the project: what it is, installing the APK on a Wear OS watch, Bluetooth mode setup with the Keycard, Fleet mode setup with one's own Developer app and the helper script, budget, FAQ/troubleshooting (car not found, insufficient privileges, key not paired, out of range), privacy and the disclaimer. Internal design documents move out of the published folder.

**Blocked by:** 06, 07, 11

**Status:** done

- [x] Site published from the public repo on every push to main
- [x] Every page above present and linked from the README
- [x] Internal specs/plans not published

## Comments

- 2026-10-24: Built. MkDocs Material site in `docs/` (`mkdocs.yml`, pinned in `requirements-site.txt`; `mkdocs build --strict` clean): Home, Install (wireless adb, phone installer apps, from source), Bluetooth mode, Fleet mode (`setup.sh` keys → pages → portal → register → pair → push-watch, then sign in on the watch; `ble-check` marked as not needed), Fleet API budget, Troubleshooting (the app's own messages), Privacy, Disclaimer. Internal docs moved to `design/` (adr, agents, superpowers) and references updated. `.github/workflows/site.yml` deploys to Pages on push to main. README links every page. Pending: publish from the public repo (ticket 11) with Pages source set to "GitHub Actions"; URLs assume `Giack/wear-for-tesla` → `giack.github.io/wear-for-tesla`. Release asset name to check against ticket 12.

- 2026-10-24: Live at https://giack.github.io/wear-for-tesla/ (Pages source: GitHub Actions); `design/` not published (404).
