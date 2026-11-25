package dev.giack.teslawear.tile

import android.content.Context
import androidx.annotation.StringRes
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import dev.giack.teslawear.R
import dev.giack.teslawear.confirm.ConfirmActivity
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.usage.BudgetLevel
import dev.giack.teslawear.core.TileCommands
import dev.giack.teslawear.ui.MainActivity
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.actionActive
import dev.giack.teslawear.ui.actionName
import dev.giack.teslawear.ui.modelName
import dev.giack.teslawear.ui.text
import kotlin.math.roundToInt

data class TileButton(
    val action: QuickAction,
    @StringRes val label: Int,
    val active: Boolean,
    /** Driver-door unlatch: the Tile marks it, and the command still goes through the usual confirmation. */
    val fleetOnly: Boolean = false,
)

/** [chip] is the channel of the last reading, null before the first one. */
data class TileModel(@StringRes val title: Int, @StringRes val chip: Int?, val line: UiText, val buttons: List<TileButton>)

/**
 * What the Tile shows; reads only the stored snapshot (never the network, never a wake).
 * [commands] is the user's order. Omitted, it is today's default, so existing callers stay put.
 * More than four, or a repeat, is dropped here as well as at save time.
 */
fun buildTileModel(
    snapshot: Snapshot?,
    nowMs: Long,
    meterLevel: BudgetLevel,
    model: CarModel? = null,
    commands: List<QuickAction> = TileCommands.DEFAULT,
): TileModel {
    val s = snapshot
    val line = if (s == null) {
        text(R.string.tile_open_app)
    } else {
        UiText.Raw(listOfNotNull(
            s.batteryPct?.let { "$it%" },
            s.rangeKm?.let { "$it km" },
            s.insideTempC?.let { "${it.roundToInt()}°" },
        ).joinToString(" · "))
    }
    return TileModel(
        title = modelName(model),
        chip = when (s?.channel) { Channel.BLE -> R.string.channel_ble; Channel.CLOUD -> R.string.channel_cloud; null -> null },
        line = line,
        buttons = TileCommands.sanitize(commands).map { action ->
            TileButton(action, tileLabel(action, s), actionActive(action, s), action.requiresFleet)
        },
    )
}

private fun tileLabel(action: QuickAction, snapshot: Snapshot?): Int = when (action) {
    QuickAction.LOCK -> when (snapshot?.locked) {
        true -> R.string.tile_locked
        false -> R.string.tile_unlocked
        null -> actionName(QuickAction.LOCK)
    }
    QuickAction.CHARGE_PORT -> R.string.tile_port
    else -> actionName(action)
}

internal object TileIds {
    const val CAR = "car"
    fun icon(action: QuickAction, locked: Boolean?) = when (action) {
        QuickAction.LOCK -> if (locked == false) "ic_unlock" else "ic_lock"
        QuickAction.CLIMATE -> "ic_fan"
        QuickAction.FRUNK -> "ic_frunk"
        QuickAction.CHARGE_PORT -> "ic_plug"
        QuickAction.TRUNK -> "ic_trunk"
        QuickAction.WINDOWS -> "ic_vent"
        QuickAction.LIGHTS -> "ic_light"
        QuickAction.HORN -> "ic_horn"
        QuickAction.SENTRY -> "ic_sentry"
        QuickAction.CHARGING -> "ic_battery"
        QuickAction.UNLATCH_DOOR -> "ic_door"
    }

    /** Every id [icon] can return, so the Tile service registers the matching drawable. */
    val icons: List<Pair<String, Int>> = listOf(
        "ic_lock" to R.drawable.ic_lock,
        "ic_unlock" to R.drawable.ic_unlock,
        "ic_fan" to R.drawable.ic_fan,
        "ic_frunk" to R.drawable.ic_frunk,
        "ic_plug" to R.drawable.ic_plug,
        "ic_trunk" to R.drawable.ic_trunk,
        "ic_vent" to R.drawable.ic_vent,
        "ic_light" to R.drawable.ic_light,
        "ic_horn" to R.drawable.ic_horn,
        "ic_sentry" to R.drawable.ic_sentry,
        "ic_battery" to R.drawable.ic_battery,
        "ic_door" to R.drawable.ic_door,
    )
}

private const val INK = 0xFFF4F4F5.toInt()
private const val MUTED = 0xFF8B8B92.toInt()
private const val CHIP = 0xFF1D1D21.toInt()
private const val ACCENT = 0xFF3E6AE1.toInt()
private const val ACCENT_SOFT = 0xFF8FB0FF.toInt()

/** Renders [model] as in the approved mockup (screen 2). */
fun carTileLayout(context: Context, model: TileModel, locked: Boolean?): LayoutElement {
    val header = LayoutElementBuilders.Row.Builder()
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .addContent(text(context.getString(model.title), 13f, INK))
        .apply {
            model.chip?.let {
                addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
                addContent(chip(context.getString(it), highlighted = it == R.string.channel_ble))
            }
        }
        .build()
    val car = LayoutElementBuilders.Image.Builder()
        .setResourceId(TileIds.CAR).setWidth(dp(100f)).setHeight(dp(34f))
        .setModifiers(clickable("open_app", launch(context, MainActivity::class.java.name, null)))
        .build()
    val buttons = LayoutElementBuilders.Row.Builder().apply {
        model.buttons.forEachIndexed { i, b ->
            if (i > 0) addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).build())
            addContent(button(context, b, TileIds.icon(b.action, locked)))
        }
    }.build()
    val column = LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(header)
        .addContent(car)
        .addContent(text(model.line.resolve(context.resources), 12f, INK))
        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
        .addContent(buttons)
        // Keeps the button row above centre-bottom, where the round screen is still wide enough.
        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(18f)).build())
        .build()
    return LayoutElementBuilders.Box.Builder()
        .setWidth(expand()).setHeight(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .addContent(column)
        .build()
}

private fun button(context: Context, b: TileButton, iconId: String): LayoutElement {
    val name = context.getString(b.label)
    val label = if (b.fleetOnly) context.getString(R.string.tile_fleet_suffix, name) else name
    val circle = LayoutElementBuilders.Box.Builder()
        .setWidth(dp(48f)).setHeight(dp(48f))
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(if (b.active) ACCENT else CHIP))
                        .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(24f)).build())
                        .build(),
                )
                .setClickable(
                    ModifiersBuilders.Clickable.Builder().setId(b.action.name)
                        .setOnClick(launch(context, ConfirmActivity::class.java.name, b.action)).build(),
                )
                .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(label).build())
                .build(),
        )
        .addContent(LayoutElementBuilders.Image.Builder().setResourceId(iconId).setWidth(dp(24f)).setHeight(dp(24f)).build())
        .build()
    return LayoutElementBuilders.Column.Builder()
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .addContent(circle)
        .addContent(text(label, 9f, MUTED))
        .build()
}

private fun chip(label: String, highlighted: Boolean): LayoutElement = LayoutElementBuilders.Box.Builder()
    .setWidth(wrap()).setHeight(wrap())
    .setModifiers(
        ModifiersBuilders.Modifiers.Builder()
            .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(CHIP))
                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(8f)).build()).build())
            .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(6f)).setEnd(dp(6f)).setTop(dp(1f)).setBottom(dp(1f)).build())
            .build(),
    )
    .addContent(text(label, 9f, if (highlighted) ACCENT_SOFT else MUTED))
    .build()

private fun text(value: String, size: Float, color: Int): LayoutElement = LayoutElementBuilders.Text.Builder()
    .setText(value)
    .setMaxLines(1)
    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color)).build())
    .build()

private fun clickable(id: String, action: ActionBuilders.Action) = ModifiersBuilders.Modifiers.Builder()
    .setClickable(ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(action).build())
    .build()

private fun launch(context: Context, className: String, action: QuickAction?): ActionBuilders.LaunchAction {
    val activity = ActionBuilders.AndroidActivity.Builder().setPackageName(context.packageName).setClassName(className)
    if (action != null) {
        activity.addKeyToExtraMapping(ConfirmActivity.EXTRA_ACTION, ActionBuilders.AndroidStringExtra.Builder().setValue(action.name).build())
    }
    return ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build()
}
