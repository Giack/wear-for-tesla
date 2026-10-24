package dev.giack.teslawear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.giack.teslawear.R
import dev.giack.teslawear.confirm.ConfirmActivity
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.graph
import dev.giack.teslawear.ui.MainActivity

private fun text(s: String) = PlainComplicationText.Builder(s).build()

private fun icon(context: Context, res: Int) = MonochromaticImage.Builder(Icon.createWithResource(context, res)).build()

/** Battery: ranged value 0–100 with "78%"; tap opens the app. Push-updated only. */
class BatteryComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, applicationContext.graph.snapshots.load())

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Snapshot(batteryPct = 78, rangeKm = 312, updatedAtMs = 0, channel = dev.giack.teslawear.core.Channel.BLE))

    private fun build(type: ComplicationType, s: Snapshot?): ComplicationData? {
        val c = batteryContent(s)
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val description = text(getString(R.string.complication_battery_description, c.text))
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(c.value, 0f, 100f, description)
                .setText(text(c.text))
                // Car glyph tells it apart from the watch's own battery complication.
                .setMonochromaticImage(icon(this, R.drawable.ic_car))
                .setTapAction(tap)
                .build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(c.text), description)
                .setMonochromaticImage(icon(this, R.drawable.ic_car))
                .setTapAction(tap)
                .build()
            else -> null
        }
    }
}

/** Climate: status text with a fan icon; tap opens the hold-to-confirm toggle. */
class ClimateComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, applicationContext.graph.snapshots.load())

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Snapshot(climateOn = true, insideTempC = 21f, updatedAtMs = 0, channel = dev.giack.teslawear.core.Channel.BLE))

    private fun build(type: ComplicationType, s: Snapshot?): ComplicationData? {
        val c = climateContent(s)
        val tap = PendingIntent.getActivity(
            this, 1, ConfirmActivity.intent(this, QuickAction.CLIMATE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val shown = c.text.resolve(resources)
        val description = text(getString(R.string.complication_climate_description, shown))
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(shown), description)
                .setMonochromaticImage(icon(this, R.drawable.ic_fan))
                .setTapAction(tap)
                .build()
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(Icon.createWithResource(this, R.drawable.ic_fan), SmallImageType.ICON).build(), description,
            ).setTapAction(tap).build()
            else -> null
        }
    }
}

object ComplicationUpdates {
    fun requestAll(context: Context) {
        listOf(BatteryComplicationService::class.java, ClimateComplicationService::class.java).forEach {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, it)).requestUpdateAll()
        }
    }
}
