package dev.giack.teslawear.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.giack.teslawear.R
import dev.giack.teslawear.core.Age
import dev.giack.teslawear.core.Progress
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.RouteOutcome
import dev.giack.teslawear.core.car.Paint
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.VehicleCommand

/**
 * Text chosen by controllers and resolved against the current locale only when drawn, so the
 * controllers stay testable on the JVM. Arguments that are themselves [UiText] are resolved first.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int) : UiText
    /** Text that is not translated: values from the car or the system, such as fault names. */
    data class Raw(val text: String) : UiText
    data class Joined(val parts: List<UiText>, val separator: String) : UiText

    fun resolve(res: Resources): String = when (this) {
        is Res -> res.getString(id, *args.map { if (it is UiText) it.resolve(res) else it }.toTypedArray())
        is Plural -> res.getQuantityString(id, count, count)
        is Raw -> text
        is Joined -> parts.joinToString(separator) { it.resolve(res) }
    }
}

fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun lines(vararg parts: UiText?): UiText = UiText.Joined(parts.filterNotNull(), "\n")

@Composable
fun UiText.string(): String {
    LocalConfiguration.current // re-resolve when the locale changes
    return resolve(LocalContext.current.resources)
}

fun ageText(age: Age): UiText = when (age) {
    Age.Now -> text(R.string.age_now)
    is Age.Minutes -> text(R.string.age_minutes, age.count)
    is Age.Hours -> text(R.string.age_hours, age.count)
    Age.Yesterday -> text(R.string.age_yesterday)
    is Age.Days -> text(R.string.age_days, age.count)
}

/** The exact action a command performs, as the confirmation states it. */
fun commandText(command: VehicleCommand): UiText = when (command) {
    VehicleCommand.Lock -> text(R.string.command_lock)
    VehicleCommand.Unlock -> text(R.string.command_unlock)
    VehicleCommand.WakeVehicle -> text(R.string.command_wake)
    VehicleCommand.OpenFrunk -> text(R.string.command_open_frunk)
    VehicleCommand.OpenTrunk -> text(R.string.command_open_trunk)
    VehicleCommand.CloseTrunk -> text(R.string.command_close_trunk)
    VehicleCommand.ClimateOn -> text(R.string.command_climate_on)
    VehicleCommand.ClimateOff -> text(R.string.command_climate_off)
    is VehicleCommand.SetTemp -> text(R.string.command_set_temp, temperatureText(command.celsius))
    VehicleCommand.ChargePortOpen -> text(R.string.command_open_charge_port)
    VehicleCommand.ChargePortClose -> text(R.string.command_close_charge_port)
    VehicleCommand.VentWindows -> text(R.string.command_vent_windows)
    VehicleCommand.CloseWindows -> text(R.string.command_close_windows)
    VehicleCommand.FlashLights -> text(R.string.command_flash_lights)
    VehicleCommand.Honk -> text(R.string.command_honk)
}

fun temperatureText(celsius: Float): UiText = text(R.string.temperature, celsius)

fun progressText(progress: Progress): UiText = when (progress) {
    Progress.WAKING_CAR -> text(R.string.progress_waking_car)
}

/** What to tell the user after a command; null when it simply worked. */
fun outcomeText(outcome: RouteOutcome): UiText? = when (outcome) {
    is RouteOutcome.Done -> resultText(outcome.result)
    RouteOutcome.NoConnection -> text(R.string.outcome_no_connection)
    RouteOutcome.Unreachable -> text(R.string.outcome_unreachable)
    RouteOutcome.AuthRequired -> text(R.string.outcome_sign_in_again)
    RouteOutcome.OutOfRange -> text(R.string.outcome_out_of_range)
}

fun resultText(result: CommandResult): UiText? = when (result) {
    CommandResult.Ok -> null
    // The car's own reason (a fault name) is shown as it comes.
    is CommandResult.Failed -> text(R.string.outcome_error, result.reason)
    CommandResult.Unknown -> text(R.string.outcome_unknown)
}

@StringRes
fun actionName(action: QuickAction): Int = when (action) {
    QuickAction.LOCK -> R.string.action_lock
    QuickAction.CLIMATE -> R.string.action_climate
    QuickAction.FRUNK -> R.string.action_frunk
    QuickAction.CHARGE_PORT -> R.string.action_charge_port
    QuickAction.TRUNK -> R.string.action_trunk
    QuickAction.WINDOWS -> R.string.action_windows
    QuickAction.LIGHTS -> R.string.action_lights
    QuickAction.HORN -> R.string.action_horn
}

@StringRes
fun modelName(model: CarModel?): Int = if (model == CarModel.MODEL_3) R.string.model_3 else R.string.model_y

@StringRes
fun paintName(paint: Paint?): Int = when (paint) {
    Paint.WHITE -> R.string.paint_white
    Paint.BLACK -> R.string.paint_black
    Paint.GREY -> R.string.paint_grey
    Paint.SILVER -> R.string.paint_silver
    Paint.BLUE -> R.string.paint_blue
    Paint.RED -> R.string.paint_red
    null -> R.string.paint_unset
}

@StringRes
fun wheelsName(wheels: String?): Int = if (wheels == "SPORT") R.string.wheels_sport else R.string.wheels_standard
