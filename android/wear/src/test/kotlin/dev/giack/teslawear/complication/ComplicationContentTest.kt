package dev.giack.teslawear.complication

import dev.giack.teslawear.R
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ComplicationContentTest {
    private fun snap(
        battery: Int? = null,
        range: Int? = null,
        locked: Boolean? = null,
        climateOn: Boolean? = null,
        sentryOn: Boolean? = null,
        inside: Float? = null,
        asleep: Boolean? = null,
    ) = Snapshot(
        batteryPct = battery, rangeKm = range, locked = locked, climateOn = climateOn, sentryOn = sentryOn,
        insideTempC = inside, asleep = asleep, updatedAtMs = 0, channel = Channel.CLOUD,
    )

    private val dash = UiText.Raw("--")

    @Test fun batteryShowsOnlyPercent() =
        assertEquals(BatteryContent(78f, "78%"), batteryContent(snap(battery = 78, range = 312)))

    @Test fun batteryWithoutSnapshot() = assertEquals(BatteryContent(0f, "--"), batteryContent(null))

    @Test fun climateOnShowsCabinTemperature() =
        assertEquals(ClimateContent(UiText.Raw("21°"), on = true), climateContent(snap(climateOn = true, inside = 21.4f)))

    @Test fun climateOff() = assertEquals(ClimateContent(text(R.string.complication_off), on = false), climateContent(snap(climateOn = false, inside = 18f)))

    @Test fun climateUnknown() = assertEquals(ClimateContent(dash, on = null), climateContent(null))

    @Test fun climateAsleepDoesNotShowTheOldTemperature() {
        val shown = climateComplication(snap(climateOn = true, inside = 21.4f, asleep = true))
        assertEquals(dash, shown.shortText)
        assertEquals(StatusGlyph.UNKNOWN, shown.glyph)
        assertEquals(text(R.string.complication_asleep), shown.spoken)
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.CLIMATE), shown.tap)
    }

    @Test fun climateTapOpensHoldToConfirm() =
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.CLIMATE), climateComplication(snap(climateOn = true, inside = 21f)).tap)

    @Test fun lockStatusShowsLockedAndUnlocked() {
        val locked = lockStatusContent(snap(locked = true))
        assertEquals(text(R.string.complication_locked), locked.shortText)
        assertEquals(StatusGlyph.LOCKED, locked.glyph)
        assertEquals(R.drawable.ic_lock, glyphDrawable(locked.glyph))
        assertEquals(ComplicationTap.OpenApp, locked.tap)

        val unlocked = lockStatusContent(snap(locked = false))
        assertEquals(text(R.string.complication_unlocked), unlocked.shortText)
        assertEquals(StatusGlyph.UNLOCKED, unlocked.glyph)
        assertEquals(R.drawable.ic_unlock, glyphDrawable(unlocked.glyph))
        assertEquals(ComplicationTap.OpenApp, unlocked.tap)
    }

    @Test fun lockStatusWithNoDataIsNeutral() {
        val shown = lockStatusContent(null)
        assertEquals(dash, shown.shortText)
        assertEquals(StatusGlyph.UNKNOWN, shown.glyph)
        assertEquals(text(R.string.complication_no_data), shown.spoken)
        assertEquals(R.drawable.ic_unknown, glyphDrawable(shown.glyph))
        assertNotEquals(R.drawable.ic_lock, glyphDrawable(shown.glyph))
        assertEquals(ComplicationTap.OpenApp, shown.tap)
    }

    @Test fun lockStatusAsleepHidesTheOldLock() {
        val shown = lockStatusContent(snap(locked = false, asleep = true))
        assertEquals(dash, shown.shortText)
        assertEquals(StatusGlyph.UNKNOWN, shown.glyph)
        assertEquals(text(R.string.complication_asleep), shown.spoken)
        assertEquals(ComplicationTap.OpenApp, shown.tap)
    }

    @Test fun lockStatusWithUnknownFieldIsNeutral() {
        val shown = lockStatusContent(snap())
        assertEquals(dash, shown.shortText)
        assertEquals(text(R.string.complication_no_data), shown.spoken)
        assertEquals(StatusGlyph.UNKNOWN, shown.glyph)
    }

    @Test fun lockCommandShowsStateAndConfirmsLock() {
        val locked = commandComplicationContent(QuickAction.LOCK, snap(locked = true))
        assertEquals(text(R.string.complication_locked), locked.shortText)
        assertEquals(StatusGlyph.LOCKED, locked.glyph)
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.LOCK), locked.tap)

        val unlocked = commandComplicationContent(QuickAction.LOCK, snap(locked = false))
        assertEquals(text(R.string.complication_unlocked), unlocked.shortText)
        assertEquals(StatusGlyph.UNLOCKED, unlocked.glyph)
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.LOCK), unlocked.tap)
    }

    @Test fun climateCommandShowsOnOffNotTheTemperature() {
        val on = commandComplicationContent(QuickAction.CLIMATE, snap(climateOn = true, inside = 21.4f))
        assertEquals(text(R.string.complication_on), on.shortText)
        assertEquals(StatusGlyph.FAN, on.glyph)
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.CLIMATE), on.tap)

        val off = commandComplicationContent(QuickAction.CLIMATE, snap(climateOn = false, inside = 18f))
        assertEquals(text(R.string.complication_off), off.shortText)
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.CLIMATE), off.tap)
    }

    @Test fun sentryCommandShowsOnOffAndConfirmsSentry() {
        val armed = commandComplicationContent(QuickAction.SENTRY, snap(sentryOn = true))
        assertEquals(text(R.string.complication_on), armed.shortText)
        assertEquals(StatusGlyph.SENTRY, armed.glyph)
        assertEquals(R.drawable.ic_sentry, glyphDrawable(armed.glyph))
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.SENTRY), armed.tap)

        val idle = commandComplicationContent(QuickAction.SENTRY, snap(sentryOn = false))
        assertEquals(text(R.string.complication_off), idle.shortText)
        assertEquals(StatusGlyph.SENTRY, idle.glyph)
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.SENTRY), idle.tap)
    }

    @Test fun commandComplicationsAreNeutralWithoutDataOrWhileAsleep() {
        listOf(QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.SENTRY).forEach { action ->
            val missing = commandComplicationContent(action, null)
            assertEquals(dash, missing.shortText)
            assertEquals(StatusGlyph.UNKNOWN, missing.glyph)
            assertEquals(text(R.string.complication_no_data), missing.spoken)
            assertEquals(ComplicationTap.HoldToConfirm(action), missing.tap)

            val asleep = commandComplicationContent(action, snap(locked = false, climateOn = true, sentryOn = true, asleep = true))
            assertEquals(dash, asleep.shortText)
            assertEquals(StatusGlyph.UNKNOWN, asleep.glyph)
            assertEquals(text(R.string.complication_asleep), asleep.spoken)
            assertEquals(ComplicationTap.HoldToConfirm(action), asleep.tap)

            val unknownField = commandComplicationContent(action, snap())
            assertEquals(dash, unknownField.shortText)
            assertEquals(text(R.string.complication_no_data), unknownField.spoken)
            assertEquals(ComplicationTap.HoldToConfirm(action), unknownField.tap)
        }
    }

    @Test fun aCommandOutsideTheThreeDoesNotConfirm() {
        val shown = commandComplicationContent(QuickAction.FRUNK, snap(locked = true))
        assertEquals(ComplicationTap.OpenApp, shown.tap)
        assertEquals(dash, shown.shortText)
    }

    @Test fun eachSlotTapIsTheAppOrHoldToConfirm() {
        assertEquals(ComplicationTap.OpenApp, complicationTap(ComplicationSlot.BATTERY))
        assertEquals(ComplicationTap.OpenApp, complicationTap(ComplicationSlot.LOCK_STATUS))
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.CLIMATE), complicationTap(ComplicationSlot.CLIMATE))
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.LOCK), complicationTap(ComplicationSlot.LOCK_COMMAND))
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.CLIMATE), complicationTap(ComplicationSlot.CLIMATE_COMMAND))
        assertEquals(ComplicationTap.HoldToConfirm(QuickAction.SENTRY), complicationTap(ComplicationSlot.SENTRY_COMMAND))
    }
}

