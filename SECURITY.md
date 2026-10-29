# Security policy

Wear for Tesla can lock, unlock and open a car, so security reports are taken seriously.

## Reporting a vulnerability

Please report it **privately** through GitHub: [Report a vulnerability](https://github.com/Giack/wear-for-tesla/security/advisories/new). Do not open a public issue, and do not include your own VIN, keys or tokens.

Useful to include: what an attacker can do, what they need (Bluetooth range, a malicious app on the watch, network position…), steps to reproduce and the affected version.

You should get a first answer within a week. This is a spare-time project, so a fix may take longer; you will be kept informed and credited in the release notes unless you prefer otherwise.

## Supported versions

Only the latest release gets fixes.

## Scope

In scope: the app's own code (command signing, Bluetooth and Fleet API handling, key and token storage, the confirmation flow, Tile and complications) and the helper script in `setup/`.

Out of scope: vulnerabilities in Tesla's vehicles, apps or Fleet API (report those to [Tesla's bug bounty](https://bugcrowd.com/tesla)), and in Wear OS or Android.
