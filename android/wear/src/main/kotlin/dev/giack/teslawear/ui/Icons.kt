package dev.giack.teslawear.ui

import androidx.annotation.DrawableRes
import dev.giack.teslawear.R
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.Snapshot

/** Whether the button shows the "on" colour. Lights, horn and the door have no lasting state. */
fun actionActive(action: QuickAction, snapshot: Snapshot?): Boolean = when (action) {
    QuickAction.LOCK -> snapshot?.locked == true
    QuickAction.CLIMATE -> snapshot?.climateOn == true
    QuickAction.CHARGE_PORT -> snapshot?.chargePortOpen == true
    QuickAction.TRUNK -> snapshot?.trunkOpen == true
    QuickAction.FRUNK -> snapshot?.frunkOpen == true
    QuickAction.WINDOWS -> snapshot?.windowsOpen == true
    QuickAction.LIGHTS -> false
    QuickAction.HORN -> false
    QuickAction.SENTRY -> snapshot?.sentryOn == true
    QuickAction.CHARGING -> snapshot?.charging == true
    QuickAction.UNLATCH_DOOR -> false
}

/** Line icon for an action; the lock icon follows the current state. */
@DrawableRes
fun iconFor(action: QuickAction, snapshot: Snapshot? = null): Int = when (action) {
    QuickAction.LOCK -> if (snapshot?.locked == false) R.drawable.ic_unlock else R.drawable.ic_lock
    QuickAction.CLIMATE -> R.drawable.ic_fan
    QuickAction.FRUNK -> R.drawable.ic_frunk
    QuickAction.CHARGE_PORT -> R.drawable.ic_plug
    QuickAction.TRUNK -> R.drawable.ic_trunk
    QuickAction.WINDOWS -> R.drawable.ic_vent
    QuickAction.LIGHTS -> R.drawable.ic_light
    QuickAction.HORN -> R.drawable.ic_horn
    QuickAction.SENTRY -> R.drawable.ic_sentry
    QuickAction.CHARGING -> R.drawable.ic_battery
    QuickAction.UNLATCH_DOOR -> R.drawable.ic_door
}
