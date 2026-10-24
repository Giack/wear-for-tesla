# Bluetooth mode by default; Fleet mode only through the user's own Developer app

The public app works out of the box in Bluetooth mode: the watch creates its own BLE key, the user confirms it with the Keycard, and no Tesla account, server or domain is involved. Remote control through the Fleet API is an advanced, opt-in Fleet mode in which every user registers their own Developer app (client credentials plus a domain publishing their Cloud key). We chose this so the project never operates a shared Fleet API app: a shared app would make the maintainer pay every user's Fleet API usage, host everyone's Cloud key, and hold responsibility for other people's vehicle access.

## Considered Options

- **One shared Developer app run by the maintainer**: simplest for users (log in, approve the key), rejected for cost, liability and the single point of failure if Tesla revokes the app.
- **Own Developer app as the only mode**: rejected because the setup (developer.tesla.com, domain, partner registration) is too hard for most users.

## Consequences

- Without Fleet mode the app cannot read the car's configuration (the Bluetooth protocol exposes state, not paint/wheels/model), so the user picks it during setup and Fleet mode overrides it.
- Remote commands (e.g. climate from home) exist only in Fleet mode.
- The Cloud key cannot command over Bluetooth (`MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES`, verified 2026-10-18), so Bluetooth always needs its own BLE key, in both modes.
