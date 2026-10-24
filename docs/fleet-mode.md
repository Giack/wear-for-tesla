# Fleet mode

Fleet mode adds remote control through Tesla's Fleet API. It is for advanced users: you register your own **Developer app** with Tesla, publish its public key on a website you control, and send its credentials to your watch with a helper script. It takes about half an hour, once.

Set up [Bluetooth mode](bluetooth-mode.md) first: Fleet mode adds to it, it does not replace it.

## What changes

- State and commands work from anywhere. When the car is near, commands still go over Bluetooth, which is faster and free.
- The app learns the car's real model, paint and wheels, replacing what you picked.
- The app finds your Fleet API region (North America, Europe, …) after you sign in.
- The app wakes a sleeping car **only to run a command you confirmed**, never just to read its state.
- Your usage is counted against a [monthly budget](budget.md).

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
