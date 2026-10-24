# Troubleshooting

## Pairing

### "No Tesla nearby: move closer to the car and try again"

The watch saw no Tesla over Bluetooth.

- Sit inside the car, or stand next to it.
- Make sure the watch's Bluetooth is on and the app has the Bluetooth permissions (**Settings → Bluetooth permissions**).
- If the watch asks for location, turn it on: some watches need it to scan.
- Wake the car by opening a door or touching its screen; a deeply asleep car advertises less often.

### "I see N Teslas but none with this VIN: check it"

The watch found Teslas, but none whose Bluetooth name matches the VIN you typed. The VIN is almost certainly mistyped: tap **Change VIN** and compare it with **Controls → Software** on the car's screen.

### "Key not paired yet: try again and place the keycard right away"

The car received the request but nobody confirmed it with the Keycard in time. Tap **Pair** again and put the Keycard on the centre console as soon as the watch asks, then confirm on the car's screen. Remember that no request appears in the Tesla phone app.

### "The car knows the key but gives it no permissions"

The car knows the key but does not let it send commands over Bluetooth (Tesla calls this *insufficient privileges*). This happens with a key added through the Tesla app link (`tesla.com/_ak`), such as the Fleet mode Cloud key: the car accepts it only for Fleet API commands. Pair a BLE key from the watch with **Settings → Pair Bluetooth key** and the Keycard.

### "The car isn't answering: try again"

The car stopped replying in the middle of pairing, usually because the Bluetooth link dropped. Stay close and try again.

## Everyday use

### "Car out of Bluetooth range"

The car is too far for Bluetooth. In Bluetooth mode, commands and fresh state need you to be near the car. With [Fleet mode](fleet-mode.md) the app uses the cloud instead.

### The state is old ("car asleep · data from …")

In Fleet mode a sleeping car is not woken just to read its state, so the app shows the last known state and its age. It wakes the car only to run a command you confirm.

### "Car unreachable" or "No connection"

Neither Bluetooth nor the Fleet API reached the car: the car may have no mobile signal (an underground car park), or the watch may be offline.

### "Sign in again"

The Tesla sign-in has expired or was revoked. Open **Settings → Sign in to Tesla**.

### State reads stopped but Bluetooth works

The [monthly budget](budget.md) is used up. Raise it in **Settings → API budget** or wait for next month.

## Installation

### `adb` can't find or keeps losing the watch

Watches turn Wi-Fi off to save battery. Keep the watch on its charger, reopen **Developer options → Wireless debugging** and connect again: the port changes each time.

### "INSTALL_FAILED_UPDATE_INCOMPATIBLE"

The installed copy was signed with a different key (for example, one you built yourself). Uninstall it first with `adb uninstall io.github.giack.wearfortesla`, then install and pair again.

## Diagnostics

**Settings → Diagnostics** has tools for reporting a problem: **BLE status** looks for the car and reads its lock state, **Flash lights (BLE)** sends a harmless command, and **BLE log** shows the last Bluetooth exchanges. If you [open an issue](https://github.com/Giack/wear-for-tesla/issues), describe what you saw and leave out your VIN.
