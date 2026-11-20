# Fleet mode

Fleet mode adds remote control through Tesla's Fleet API. It is for advanced users: you register your own **Developer app** with Tesla, publish its public key on a website you control, and send its credentials to your watch with a helper script. It takes about half an hour, once.

Set up [Bluetooth mode](bluetooth-mode.md) first: Fleet mode adds to it, it does not replace it.

## What changes

- State and commands work from anywhere. When the car is near, commands still go over Bluetooth, which is faster and free. Sentry and start/stop charging are among them.
- Unlatching the driver door always uses the Fleet API, even next to the car. The Bluetooth key has the driver role, and the car answers `MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES` for that command. With Fleet mode off, the watch says the command needs Fleet mode.
- The app learns the car's real model, paint and wheels, replacing what you picked.
- The app finds your Fleet API region (North America, Europe, …) after you sign in.
- The app wakes a sleeping car **only to run a command you confirmed**, never just to read its state. See [A sleeping car](#a-sleeping-car).
- Your usage is counted against a [monthly budget](budget.md).

## A sleeping car

A confirmed command is the only thing that wakes the car. Tesla will not run a command while the vehicle's `state` is `asleep` or `offline`. Those calls come back as HTTP **408** (`vehicle unavailable`), and after a wake the car can take **10–60 seconds** to come online. Checking `state` on `GET /api/1/vehicles/{vin}` before the command, and treating a 408 as "not online yet", is what Tesla asks for in [Best practices](https://developer.tesla.com/docs/fleet-api/getting-started/best-practices) and [Conventions](https://developer.tesla.com/docs/fleet-api/getting-started/conventions). `POST /api/1/vehicles/{vin}/wake_up` is the Fleet API wake ([Vehicle endpoints](https://developer.tesla.com/docs/fleet-api/endpoints/vehicle-endpoints)).

The watch does it in this order:

1. If the watch is in Bluetooth range, it wakes the car over Bluetooth (the VCSEC wake). That costs nothing.
2. Otherwise it calls Fleet `wake_up`. That call, and every later check of the vehicle's `state`, is counted in the [budget](budget.md).
3. It then checks `state` with increasing pauses (1s, 2s, 4s, then 8s) until it is `online`, for at most **45 seconds**. The screen says **Waking the car…** the whole time.
4. It sends the command **once**. If Tesla answers that command with 408, the watch wakes the car once more and sends the command a single time again. It never sends it a third time.
5. If the car is still not online, the watch says **The car did not wake in time**. It does not keep spinning.

A client-side timeout is different from a 408: the command may already have reached the car, so the watch does not send it again and reports an unknown result instead.

## Why your own Developer app?

The project does not run a shared Developer app. With a shared app the maintainer would pay everyone's Fleet API usage, host everyone's key and stand between strangers and their cars. With your own, the access is yours alone and you can revoke it at any time.

## What you need

- A computer with macOS or Linux and: `bash`, `openssl`, `python3`, `curl`, `git`, `adb` (see [Install](install.md)) and the [GitHub CLI](https://cli.github.com/) signed in with `gh auth login`.
- A **GitHub account**. The script publishes your public key on `https://<your-user>.github.io` with GitHub Pages.
- An account on [developer.tesla.com](https://developer.tesla.com). Tesla asks for a payment method and bills Fleet API usage beyond its monthly free credit.
- The watch connected with `adb` and the app already paired over Bluetooth.

!!! warning "Existing GitHub Pages site"
    If you already have a site at `<your-user>.github.io`, the script adds the key under `.well-known/` but also replaces the site's `index.html`. Back it up first.

The helper script is `setup/setup.sh` in the repository. Its prompts are currently in Italian; the steps below explain each one. Every step can be run again safely.

```sh
git clone https://github.com/Giack/wear-for-tesla.git
cd wear-for-tesla
```

## 1. Create the key pair

```sh
./setup/setup.sh keys
```

It asks for your GitHub username and the app package (press Enter for the default, `io.github.giack.wearfortesla`), then creates the **Cloud key** pair in `secrets/`. The private key never leaves your computer and your watch; `secrets/` is ignored by git.

## 2. Publish the public key

```sh
./setup/setup.sh pages
```

It creates (or updates) the public repository `<your-user>/<your-user>.github.io`, which holds only the public key, enables GitHub Pages and waits until the key is reachable at `https://<your-user>.github.io/.well-known/appspecific/com.tesla.3p.public-key.pem`. This can take up to ten minutes.

## 3. Register the Developer app

```sh
./setup/setup.sh portal
```

The script prints the values to enter on [developer.tesla.com](https://developer.tesla.com) under **Dashboard → Create application**:

- **Allowed origin:** `https://<your-user>.github.io`
- **Redirect URI:** `https://wear.googleapis.com/3p_auth/io.github.giack.wearfortesla`
- **Scopes:** Vehicle Information, Vehicle Commands, Vehicle Charging Management

Then paste the app's **Client ID** and **Client Secret** (the secret is not echoed) and the car's **VIN**. They are saved in `secrets/config.env`.

## 4. Register your domain with the Fleet API

```sh
./setup/setup.sh register
```

It registers your domain in every Fleet API region and checks that the key Tesla holds matches yours.

## 5. Approve the Cloud key in the car

```sh
./setup/setup.sh pair
```

On your phone, with the car selected in the Tesla app, open the link the script prints (`https://tesla.com/_ak/<your-user>.github.io`) and approve the key. You can also do this later from the watch: **Settings → Pair the cloud key**.

Skip `setup.sh ble-check`: it is for the maintainer's tools. In the app the watch has its own BLE key from Bluetooth mode, and the Cloud key cannot send commands over Bluetooth anyway.

## 6. Send the credentials to the watch

With the watch connected (`adb devices` lists it):

```sh
./setup/setup.sh push-watch <ip>:<port>
```

The script streams the key and the Developer app credentials straight into the app's private storage, where the app imports them and deletes the file. The watch shows **Secrets imported**.

## 7. Sign in on the watch

On the watch, open **Settings → Sign in to Tesla**. The sign-in continues on your phone; approve it there. Then tap **Test Fleet API**: it reads the car's state through the cloud and shows the battery level.

## Turning Fleet mode off

**Settings → Turn off Fleet mode** deletes the credentials and tokens from the watch. Bluetooth mode keeps working, and the car profile stays as it was. To revoke the access on Tesla's side as well, remove the key in the car under **Controls → Locks** and delete the app on developer.tesla.com.
