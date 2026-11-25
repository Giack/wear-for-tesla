package dev.giack.teslawear.complication

import androidx.annotation.DrawableRes
import dev.giack.teslawear.R
import dev.giack.teslawear.core.COMPLICATION_COMMANDS
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.text
import kotlin.math.roundToInt

data class BatteryContent(val value: Float, val text: String)

data class ClimateContent(val text: UiText, val on: Boolean?)

/** Which complication slot. The tap is fixed per slot: status opens the app, a command opens hold-to-confirm. */
enum class ComplicationSlot {
    BATTERY,
    CLIMATE,
    LOCK_STATUS,
    LOCK_COMMAND,
    CLIMATE_COMMAND,
    SENTRY_COMMAND,
}

/** What a tap does. Neither value sends a command. */
sealed class ComplicationTap {
    data object OpenApp : ComplicationTap()
    data class HoldToConfirm(val action: QuickAction) : ComplicationTap()
}

/** Icon for a status or command complication. [UNKNOWN] is a dash, not a lock, fan or shield. */
enum class StatusGlyph { LOCKED, UNLOCKED, FAN, SENTRY, UNKNOWN }

/**
 * Short text is what the watch face draws. [spoken] is the longer phrase in the content description,
 * so "Asleep" can be announced while the face still shows a dash.
 */
data class StatusComplication(
    val shortText: UiText,
    val spoken: UiText,
    val glyph: StatusGlyph,
    val tap: ComplicationTap,
)

fun complicationTap(slot: ComplicationSlot): ComplicationTap = when (slot) {
    ComplicationSlot.BATTERY, ComplicationSlot.LOCK_STATUS -> ComplicationTap.OpenApp
    ComplicationSlot.CLIMATE -> ComplicationTap.HoldToConfirm(QuickAction.CLIMATE)
    ComplicationSlot.LOCK_COMMAND -> ComplicationTap.HoldToConfirm(QuickAction.LOCK)
    ComplicationSlot.CLIMATE_COMMAND -> ComplicationTap.HoldToConfirm(QuickAction.CLIMATE)
    ComplicationSlot.SENTRY_COMMAND -> ComplicationTap.HoldToConfirm(QuickAction.SENTRY)
}

@DrawableRes
fun glyphDrawable(glyph: StatusGlyph): Int = when (glyph) {
    StatusGlyph.LOCKED -> R.drawable.ic_lock
    StatusGlyph.UNLOCKED -> R.drawable.ic_unlock
    StatusGlyph.FAN -> R.drawable.ic_fan
    StatusGlyph.SENTRY -> R.drawable.ic_sentry
    StatusGlyph.UNKNOWN -> R.drawable.ic_unknown
}

fun batteryContent(s: Snapshot?): BatteryContent {
    val pct = s?.batteryPct ?: return BatteryContent(0f, "--")
    // Percentage only: watch-face slots are too small for the range as well.
    return BatteryContent(pct.toFloat(), "$pct%")
}

fun climateContent(s: Snapshot?): ClimateContent {
    // Asleep data is from before the car slept, so a temperature here would be a claim we cannot make.
    if (s == null || s.asleep == true || s.climateOn == null) return ClimateContent(UiText.Raw("--"), on = null)
    return if (s.climateOn == true) {
        ClimateContent(
            (s.insideTempC ?: s.setTempC)?.let { UiText.Raw("${it.roundToInt()}°") } ?: text(R.string.complication_on),
            on = true,
        )
    } else {
        ClimateContent(text(R.string.complication_off), on = false)
    }
}

/** Car climate: the temperature when it is on, and a tap that opens hold-to-confirm. */
fun climateComplication(s: Snapshot?): StatusComplication {
    val content = climateContent(s)
    val glyph = if (content.on == null) StatusGlyph.UNKNOWN else StatusGlyph.FAN
    return StatusComplication(content.text, spoken(s, content.text, content.on != null), glyph, complicationTap(ComplicationSlot.CLIMATE))
}

/** Lock status. A tap opens the app; the Lock command complication is the one that confirms. */
fun lockStatusContent(s: Snapshot?): StatusComplication {
    val tap = complicationTap(ComplicationSlot.LOCK_STATUS)
    if (s == null || s.asleep == true || s.locked == null) return neutral(s, tap)
    val text = if (s.locked == true) text(R.string.complication_locked) else text(R.string.complication_unlocked)
    val glyph = if (s.locked == true) StatusGlyph.LOCKED else StatusGlyph.UNLOCKED
    return StatusComplication(text, text, glyph, tap)
}

/**
 * Lock, climate or Sentry as a shortcut. The text is the known state (locked, on, off), never the
 * command that a tap would send: that name is on the hold-to-confirm screen.
 * Climate here is on/off. The cabin temperature stays on [climateComplication].
 */
fun commandComplicationContent(action: QuickAction, s: Snapshot?): StatusComplication {
    val tap = if (action in COMPLICATION_COMMANDS) ComplicationTap.HoldToConfirm(action) else ComplicationTap.OpenApp
    if (s == null || s.asleep == true) return neutral(s, tap)
    return when (action) {
        QuickAction.LOCK -> state(s.locked, text(R.string.complication_locked), text(R.string.complication_unlocked), StatusGlyph.LOCKED, StatusGlyph.UNLOCKED, s, tap)
        QuickAction.CLIMATE -> state(s.climateOn, text(R.string.complication_on), text(R.string.complication_off), StatusGlyph.FAN, StatusGlyph.FAN, s, tap)
        QuickAction.SENTRY -> state(s.sentryOn, text(R.string.complication_on), text(R.string.complication_off), StatusGlyph.SENTRY, StatusGlyph.SENTRY, s, tap)
        else -> neutral(s, tap)
    }
}

private fun state(
    value: Boolean?,
    onText: UiText,
    offText: UiText,
    onGlyph: StatusGlyph,
    offGlyph: StatusGlyph,
    snapshot: Snapshot,
    tap: ComplicationTap,
): StatusComplication = when (value) {
    true -> StatusComplication(onText, onText, onGlyph, tap)
    false -> StatusComplication(offText, offText, offGlyph, tap)
    null -> neutral(snapshot, tap)
}

/** A dash on the face. [spoken] says why, so the face does not show a lock, a fan or a shield as if it were current. */
private fun neutral(s: Snapshot?, tap: ComplicationTap) =
    StatusComplication(UiText.Raw("--"), spoken(s, UiText.Raw("--"), known = false), StatusGlyph.UNKNOWN, tap)

private fun spoken(s: Snapshot?, value: UiText, known: Boolean): UiText = when {
    known -> value
    s?.asleep == true -> text(R.string.complication_asleep)
    else -> text(R.string.complication_no_data)
}
