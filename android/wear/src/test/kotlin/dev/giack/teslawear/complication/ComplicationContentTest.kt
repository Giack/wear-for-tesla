package dev.giack.teslawear.complication

import dev.giack.teslawear.R
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.text
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ComplicationContentTest {
    private fun snap(battery: Int? = null, range: Int? = null, climateOn: Boolean? = null, inside: Float? = null) =
        Snapshot(batteryPct = battery, rangeKm = range, climateOn = climateOn, insideTempC = inside, updatedAtMs = 0, channel = Channel.CLOUD)

    @Test fun batteryShowsOnlyPercent() =
        assertEquals(BatteryContent(78f, "78%"), batteryContent(snap(battery = 78, range = 312)))

    @Test fun batteryWithoutSnapshot() = assertEquals(BatteryContent(0f, "--"), batteryContent(null))

    @Test fun climateOnShowsCabinTemperature() =
        assertEquals(ClimateContent(UiText.Raw("21°"), on = true), climateContent(snap(climateOn = true, inside = 21.4f)))

    @Test fun climateOff() = assertEquals(ClimateContent(text(R.string.complication_off), on = false), climateContent(snap(climateOn = false, inside = 18f)))

    @Test fun climateUnknown() = assertEquals(ClimateContent(UiText.Raw("--"), on = null), climateContent(null))
}
