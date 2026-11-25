---
title: Free Tesla app for Wear OS and Galaxy Watch
description: A free Tesla app for Wear OS and Galaxy Watch, compared with Tessie, Teswear (Weartes) and Voltdeck. Price, open source, Bluetooth key, cloud, Tile, complications, climate, Sentry and supported watches.
---

# Free Tesla app for Wear OS and Galaxy Watch

Wear for Tesla is a free, open-source Tesla app for Wear OS, including Galaxy Watch. This page compares it with Tessie, Teswear (now listed as Weartes) and Voltdeck: price, whether the source is published, Bluetooth key or cloud, Tile, complications, climate, Sentry, more than one car, and which watches are supported.

The Tesla support page and Model 3 owner's manual checked for this page document a phone app and an Apple Watch app. They do not mention Wear OS. The [r/GalaxyWatch thread](https://www.reddit.com/r/GalaxyWatch/comments/1w7b4sn/tesla_app_alternative_on_galaxy_watch_9/) is one place people ask for a Galaxy Watch alternative, and the [Voltdeck post on r/WearOS](https://www.reddit.com/r/WearOS/comments/1wv42cc/voltdeck_tesla_control_for_wear_os_own_button/) is the developer's own description, quoted below. Comments under those posts are not used as product facts.

**unknown** means the cited page does not say, so the cell stays open. Every competitor fact links to a source that was checked on **23 November 2026**. Names are written out. This page has no logos, screenshots or other assets from Tesla or from the other apps.

## Comparison

| | [Wear for Tesla](#wear-for-tesla) | [Tessie](#tessie) | [Teswear (Weartes)](#teswear-weartes) | [Voltdeck](#voltdeck) |
|---|---|---|---|---|
| Price | Free app. Bluetooth mode makes no Fleet API calls. Optional Fleet mode is billed by Tesla to your own Developer app; the in-app budget defaults to $10 a month. | Subscription or one-time payment. On the Play listing, Tessie says plans start at $6.99/month. | A premium tier adds climate, charging and location. The Play listing does not state the price. The amount is unknown. | unknown. The developer calls the near-car Bluetooth path free, and does not state an app price. |
| Open source | Yes. [Apache-2.0](https://github.com/Giack/wear-for-tesla/blob/main/LICENSE). | unknown | unknown | unknown |
| Bluetooth key or cloud | Bluetooth key by default, with no Tesla account. Optional cloud access through your own Fleet API Developer app. | Both. Bluetooth pairing for offline controls and Auto Unlock. Full control is described for a Wi-Fi or cellular watch. | The listing says remote control. Whether that is a Bluetooth key, a cloud API, or both is unknown. | Developer: commands go over Bluetooth first when the car is near. A separate cloud path is unknown. |
| Tile | Yes. Battery, range, and lock, climate, frunk and charge port. | Yes. View and control the car from a tile. | unknown | Yes. The developer says the tile runs commands without opening the app. |
| Complications | Battery (tap opens the app) and climate (tap opens hold to confirm). | Battery for a primary and a secondary vehicle, lock/unlock, climate, Sentry, front trunk, rear trunk, charge port, start, HomeLink and flash. | unknown | Battery, temperature and lock status, as described by the developer. |
| Climate | Yes. Over Bluetooth when the car is near. | Yes. A Climate complication. | Yes, in the premium version. | A temperature complication is described. Whether the watch app itself turns climate on is unknown. The developer describes AC and heat through Tasker. |
| Sentry | Yes. Over Bluetooth when the car is near. | Yes. A Sentry Mode complication. | unknown | unknown |
| Multiple cars | One car per watch. | At least two. The watch has battery complications for a primary and a secondary vehicle. A maximum is not stated on the Wear OS pages. | unknown | unknown |
| Supported watches | Wear OS 4 or later. The install guide is written for Galaxy Watch and Pixel Watch. The app is not on the Play Store. | A Wear OS watch that can install Tessie from the Play Store on the watch. No minimum version is named. Full control is described for any Wi-Fi or cellular Wear OS watch. | A Wear OS smartwatch. No version is named. | Closed test on Google Play. The developer asked for Wear OS testers and mentioned a TicWatch Pro 5. A minimum version is unknown. |

The sections below are the same facts, each with its link.

## Wear for Tesla

This column is taken from this repository and from the guides on this site, read on 23 November 2026.

The app is free and licensed [Apache-2.0](https://github.com/Giack/wear-for-tesla/blob/main/LICENSE). [Bluetooth mode](bluetooth-mode.md) talks to the car directly with a BLE key stored on the watch. It needs no Tesla account, and it [makes no Fleet API calls](budget.md). [Fleet mode](fleet-mode.md) is optional remote control through a Developer app you register yourself. Tesla bills that app's owner. The in-app budget defaults to [$10 a month](budget.md), the same as Tesla's monthly developer credit, so with the default you should not pay anything. The count in the app is an estimate, not Tesla's invoice.

There is a [Tile](index.md) with battery, range and four commands: lock, climate, frunk and charge port. Watch-face complications show the battery and the climate. Tapping the battery complication opens the app. Tapping the climate complication opens hold to confirm for the climate command. Both behaviours are in [`ComplicationServices.kt`](https://github.com/Giack/wear-for-tesla/blob/main/android/wear/src/main/kotlin/dev/giack/teslawear/complication/ComplicationServices.kt).

Climate and Sentry are in the command list, and both go over Bluetooth when the car is near. The same list includes lock, frunk, trunk, charge port, start and stop charging, windows, lights and horn. Unlatching the driver door needs Fleet mode: the Bluetooth key is a driver key, and the car refuses that command for the role. Every command, from the app or the Tile, waits until you hold to confirm.

The watch stores one car. [Settings → Remove car](bluetooth-mode.md#removing-the-car) deletes that car and its key and starts setup again. [`Setup`](https://github.com/Giack/wear-for-tesla/blob/main/android/core/src/main/kotlin/dev/giack/teslawear/core/setup/Setup.kt) keeps a single `car`.

[Install](install.md) needs Wear OS 4 or later (Android 13, API 33). The steps cover a Galaxy Watch and a Pixel Watch. The app is not on the Play Store and not on F-Droid: you sideload the APK. A Model 3 or Model Y has a project-owned illustration. Other models work over Bluetooth and have no illustration yet.

## Tessie

Tessie is a companion app with a Wear OS app, a tile and complications. The phone product also covers drives, charging history and automations. This table only scores the watch points above.

Checked on 23 November 2026:

- [Google Play listing](https://play.google.com/store/apps/details?id=io.tessie.app&hl=en_US) (listing updated 9 November 2026). The text says "Try it free! Requires a subscription or one-time payment" and "Use your Wear OS to control your Tesla using an app, tile and complications." In a reply on that listing dated 18 October 2026, Tessie writes that plans start at $6.99/month. The listing does not say the source is published, so open source is unknown.
- [Wear OS help](https://help.tessie.com/article/99-use-wear-os-watch-to-control-your-tesla) (page last updated 16 November 2025). Install from the Play Store on the watch, or from the phone app under Integrations → Wear OS. If the watch has no Play Store app, or Tessie does not appear there, the watch does not meet the hardware and software requirements. The page also describes complications and a tile. It does not name a Wear OS version.
- [Wear OS complications](https://help.tessie.com/article/100-wear-os-complications) (page last updated 16 November 2025). The list is Battery #1 (Primary Vehicle), Battery #2 (Secondary Vehicle), Lock/Unlock, Climate, Sentry Mode, Front Trunk, Rear Trunk, Charge Port, Start, HomeLink and Flash. The [Wear OS help page](https://help.tessie.com/article/99-use-wear-os-watch-to-control-your-tesla) says these complications let you control the car from any watch face. The complications page says Wear OS updates them about every 5 minutes. Two battery complications are the source for "at least two" cars. That page does not state a maximum.
- [Tessie + Wear OS](https://www.tessie.com/integrations/wear-os). Real-time battery, location and charging status. "Fully control your vehicle using any WiFi or cellular Wear OS smartwatch." Bluetooth pairing for offline controls and Auto Unlock. Battery and control complications on any Wear OS watch face.
- [Phone and Watch Key](https://help.tessie.com/article/108-phone-watch-key) (page last updated 1 October 2025). Pairing enables Auto Unlock, Walk Away Lock and Bluetooth controls on Wear OS, among other devices. The supported vehicles on that page are Model 3, Model Y, 2021 and later Model S and Model X, Cybertruck and Semi.

## Teswear (Weartes)

The Wear OS app is on Google Play as **Weartes: Watch app for Tesla**, package `app.navigationwear.teswear`. The listing's "What's new" says Teswear is now Weartes, and that premium access is unchanged.

Checked on 23 November 2026, [Google Play listing](https://play.google.com/store/apps/details?id=app.navigationwear.teswear&hl=en_US) (listing updated 13 October 2026):

- "Weartes allows you to remotely control your Tesla vehicle and execute advanced commands using a Wear OS-enabled smartwatch."
- "The premium version of Weartes enables you to control the climate, charging and to view location of your Tesla car directly from your Wear OS smartwatch."
- Supported models on the listing: Roadster, Model S, Model 3, Model X, Model Y and Cybertruck.
- The listing does not state the premium price, so the amount is unknown. It does not mention a Tile, complications, Sentry, more than one car, a Bluetooth key, or a Wear OS version. Those cells are unknown. It does not say the source is published, so open source is unknown.

A separate [App Store listing](https://apps.apple.com/us/app/teswear-watch-app-for-tesla/id6739069498), still named Teswear, is the Apple Watch app (checked the same day). It says the app is free with in-app purchases, and names one purchase, "Premium version", at $4.99. That figure is the Apple Watch listing's price. The Wear OS price on Google Play remains unknown.

## Voltdeck

Voltdeck is in closed testing. The public Play page `https://play.google.com/store/apps/details?id=com.arlithtechnologies.voltdeck` returned **404** on 23 November 2026, which fits a closed test and says nothing about features. What follows is the developer's own description, not an independent review.

Checked on 23 November 2026, the developer post [on r/WearOS](https://www.reddit.com/r/WearOS/comments/1wv42cc/voltdeck_tesla_control_for_wear_os_own_button/) (u/bokharij, 1 October 2026). The same description is on [Tesla Motors Club](https://teslamotorsclub.com/tmc/threads/built-a-tesla-command-app-with-tasker-and-wearos-support-looking-for-testers-android-wearos.359067/) (2 October 2026):

- A Wear OS piece with your own button layout and icons, a tile that runs commands without opening the app, and complications for battery, temperature and lock status.
- It pairs with a phone app and a Tasker plugin, and can be used without Tasker.
- "Commands go over Bluetooth first when you're near the car — free, instant, no signal needed." That sentence describes the near-car path. The post does not state an app price, so price is unknown.
- Closed testing on Google Play, package `com.arlithtechnologies.voltdeck`. The developer asked for Wear OS testers and described using a TicWatch Pro 5. No minimum Wear OS version is given.
- The developer describes a Tasker flow that turns on the AC or the heat from the temperature, and a tile action that opens the trunk once the watch is near the car. The post does not say the watch app itself has a climate command, so that cell stays unknown.
- Sentry, more than one car, and whether a cloud API is used away from the car are not stated. Those cells are unknown. The post does not say the source is published, so open source is unknown.
- The post says the project is not affiliated with Tesla, Inc.

## Tesla's own app

Checked on 23 November 2026. Text only, from Tesla's pages:

- [Tesla App Support](https://www.tesla.com/support/tesla-app) says to download the iOS or Android app. It does not mention Wear OS or Galaxy Watch.
- The [Model 3 owner's manual](https://www.tesla.com/ownersmanual/model3/en_us/GUID-F6E2CD5E-F226-4167-AC48-BD021D1FFDAB.html) documents a mobile app for iPhone and Android, and a mobile app for Apple Watch (Apple Watch Series 6, SE 2, or Ultra 1 or newer, watchOS 11 or newer) that can lock, unlock, open the trunk and open the frunk.
- [Vehicle Products on Your Apple Watch](https://www.tesla.com/support/tesla-app/apple-watch) adds watch key, charge status, charge port and climate, and requires watchOS 11 or later, Tesla app 4.39.5 or later, and vehicle software 2024.44.25 or later.

## Star the repo, or contribute

The app is free, the source is public, and the project runs no server. If this comparison is the page you wanted, [star the repository](https://github.com/Giack/wear-for-tesla). If you want to improve the app, read [CONTRIBUTING.md](https://github.com/Giack/wear-for-tesla/blob/main/CONTRIBUTING.md) and look at the [good first issues](https://github.com/Giack/wear-for-tesla/labels/good%20first%20issue).

A correction to this page is welcome. Open an issue with the source you checked.

[Install Wear for Tesla](install.md) · [Bluetooth mode](bluetooth-mode.md) · [Fleet mode](fleet-mode.md)

!!! warning "Not affiliated with Tesla, Inc. or Google LLC"
    Wear for Tesla is an independent project. It is not affiliated with, endorsed by or sponsored by Tesla, Inc., Google LLC, Tessie, Teswear, Weartes or Voltdeck. Tesla, Model 3 and Model Y are trademarks of Tesla, Inc. Wear OS is a trademark of Google LLC. Other names belong to their owners. [Full disclaimer](disclaimer.md).
