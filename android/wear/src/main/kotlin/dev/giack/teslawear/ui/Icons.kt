package dev.giack.teslawear.ui

import androidx.annotation.DrawableRes
import dev.giack.teslawear.R
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.Snapshot

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
}
