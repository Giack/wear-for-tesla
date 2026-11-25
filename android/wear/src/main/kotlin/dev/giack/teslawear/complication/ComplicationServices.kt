package dev.giack.teslawear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.StringRes
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
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
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.readComplicationSnapshot
import dev.giack.teslawear.graph
import dev.giack.teslawear.ui.MainActivity

private fun text(s: String) = PlainComplicationText.Builder(s).build()

private fun icon(context: Context, res: Int) = MonochromaticImage.Builder(Icon.createWithResource(context, res)).build()

private fun pendingTap(context: Context, tap: ComplicationTap, requestCode: Int): PendingIntent = when (tap) {
    ComplicationTap.OpenApp -> PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )
    is ComplicationTap.HoldToConfirm -> PendingIntent.getActivity(
        context, requestCode,
        ConfirmActivity.intent(context, tap.action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Short text, a single-colour icon, and a small icon. Those are the slots common watch faces offer. */
internal fun renderStatus(
    context: Context,
    type: ComplicationType,
    content: StatusComplication,
    @StringRes descriptionRes: Int,
): ComplicationData? {
    val shown = content.shortText.resolve(context.resources)
    val description = text(context.getString(descriptionRes, content.spoken.resolve(context.resources)))
    val image = icon(context, glyphDrawable(content.glyph))
    val tap = pendingTap(context, content.tap, content.tap.requestCode())
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(shown), description)
            .setMonochromaticImage(image)
            .setTapAction(tap)
            .build()
        ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(image, description)
            .setTapAction(tap)
            .build()
        ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
            SmallImage.Builder(Icon.createWithResource(context, glyphDrawable(content.glyph)), SmallImageType.ICON).build(),
            description,
        ).setTapAction(tap).build()
        else -> null
    }
}

private fun ComplicationTap.requestCode(): Int = when (this) {
    ComplicationTap.OpenApp -> 0
    is ComplicationTap.HoldToConfirm -> when (action) {
        QuickAction.CLIMATE -> 1
        QuickAction.LOCK -> 3
        QuickAction.SENTRY -> 5
        else -> 6
    }
}

private fun preview(locked: Boolean? = null, climateOn: Boolean? = null, sentryOn: Boolean? = null, insideTempC: Float? = null) =
    Snapshot(locked = locked, climateOn = climateOn, sentryOn = sentryOn, insideTempC = insideTempC, updatedAtMs = 0, channel = Channel.BLE)

/** Battery: ranged value 0–100 with "78%"; tap opens the app. Push-updated only. */
class BatteryComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, readComplicationSnapshot(applicationContext.graph.snapshots))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, Snapshot(batteryPct = 78, rangeKm = 312, updatedAtMs = 0, channel = Channel.BLE))

    private fun build(type: ComplicationType, s: Snapshot?): ComplicationData? {
        val c = batteryContent(s)
        val tap = pendingTap(this, complicationTap(ComplicationSlot.BATTERY), 0)
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

/** Climate: cabin temperature when it is on; tap opens the hold-to-confirm toggle. */
class ClimateComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        render(request.complicationType, readComplicationSnapshot(applicationContext.graph.snapshots))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        render(type, preview(climateOn = true, insideTempC = 21f))

    private fun render(type: ComplicationType, s: Snapshot?): ComplicationData? =
        renderStatus(this, type, climateComplication(s), R.string.complication_climate_description)
}

/** Locked or unlocked. A tap opens the app and does not start a command. */
class LockStatusComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        render(request.complicationType, readComplicationSnapshot(applicationContext.graph.snapshots))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        render(type, preview(locked = true))

    private fun render(type: ComplicationType, s: Snapshot?): ComplicationData? =
        renderStatus(this, type, lockStatusContent(s), R.string.complication_lock_status_description)
}

/** Lock, climate and Sentry shortcuts. Each tap opens hold-to-confirm for that [QuickAction] only. */
abstract class QuickCommandComplicationService : SuspendingComplicationDataSourceService() {
    protected abstract val action: QuickAction

    @get:StringRes
    protected abstract val descriptionRes: Int

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        render(request.complicationType, readComplicationSnapshot(applicationContext.graph.snapshots))

    override fun getPreviewData(type: ComplicationType): ComplicationData? = render(type, previewFor(action))

    private fun render(type: ComplicationType, s: Snapshot?): ComplicationData? =
        renderStatus(this, type, commandComplicationContent(action, s), descriptionRes)

    private fun previewFor(action: QuickAction): Snapshot = when (action) {
        QuickAction.LOCK -> preview(locked = true)
        QuickAction.CLIMATE -> preview(climateOn = true)
        QuickAction.SENTRY -> preview(sentryOn = true)
        else -> preview()
    }
}

class LockCommandComplicationService : QuickCommandComplicationService() {
    override val action = QuickAction.LOCK
    override val descriptionRes = R.string.complication_command_lock_description
}

class ClimateCommandComplicationService : QuickCommandComplicationService() {
    override val action = QuickAction.CLIMATE
    override val descriptionRes = R.string.complication_command_climate_description
}

class SentryCommandComplicationService : QuickCommandComplicationService() {
    override val action = QuickAction.SENTRY
    override val descriptionRes = R.string.complication_command_sentry_description
}

object ComplicationUpdates {
    val services: List<Class<out SuspendingComplicationDataSourceService>> = listOf(
        BatteryComplicationService::class.java,
        ClimateComplicationService::class.java,
        LockStatusComplicationService::class.java,
        LockCommandComplicationService::class.java,
        ClimateCommandComplicationService::class.java,
        SentryCommandComplicationService::class.java,
    )

    fun requestAll(context: Context) {
        services.forEach {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, it)).requestUpdateAll()
        }
    }
}
