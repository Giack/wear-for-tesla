package dev.giack.teslawear.complication

import dev.giack.teslawear.R
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.text
import kotlin.math.roundToInt

data class BatteryContent(val value: Float, val text: String)

data class ClimateContent(val text: UiText, val on: Boolean?)

fun batteryContent(s: Snapshot?): BatteryContent {
    val pct = s?.batteryPct ?: return BatteryContent(0f, "--")
    // Percentage only: watch-face slots are too small for the range as well.
    return BatteryContent(pct.toFloat(), "$pct%")
}

fun climateContent(s: Snapshot?): ClimateContent = when (s?.climateOn) {
    true -> ClimateContent((s.insideTempC ?: s.setTempC)?.let { UiText.Raw("${it.roundToInt()}°") } ?: text(R.string.complication_on), on = true)
    false -> ClimateContent(text(R.string.complication_off), on = false)
    null -> ClimateContent(UiText.Raw("--"), on = null)
}
