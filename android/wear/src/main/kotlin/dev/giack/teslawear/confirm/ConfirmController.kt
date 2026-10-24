package dev.giack.teslawear.confirm

import dev.giack.teslawear.R
import dev.giack.teslawear.core.Progress
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.RefreshTrigger
import dev.giack.teslawear.core.RouteOutcome
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.ToggleResolver
import dev.giack.teslawear.core.afterCommand
import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.VehicleCommand
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.ageText
import dev.giack.teslawear.ui.commandText
import dev.giack.teslawear.ui.outcomeText
import dev.giack.teslawear.ui.progressText
import dev.giack.teslawear.ui.text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** ERROR: the command certainly did not run, so it may be sent again. UNCERTAIN: it may have run; no retry. */
enum class Phase { RESOLVING, READY, SENDING, DONE, ERROR, UNCERTAIN }

data class ConfirmUi(val label: UiText?, val age: UiText?, val phase: Phase, val message: UiText?)

/** Confirmation flow for Tile/complication taps: resolve the toggle, wait for the hold, send once. */
class ConfirmController(
    private val action: QuickAction,
    private val snapshot: suspend () -> Snapshot?,
    private val refresh: suspend (RefreshTrigger) -> Snapshot?,
    private val execute: suspend (VehicleCommand, (Progress) -> Unit) -> RouteOutcome,
    private val afterCommand: () -> Unit,
    /** Persists the expected state right away, so the Tile is correct even if the screen closes first. */
    private val save: suspend (Snapshot) -> Unit,
    /** Runs work that must outlive the confirmation screen (app-scoped). */
    private val background: (suspend () -> Unit) -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val _ui = MutableStateFlow(ConfirmUi(null, null, Phase.RESOLVING, null))
    val ui: StateFlow<ConfirmUi> = _ui
    private var command: VehicleCommand? = null

    suspend fun resolve() {
        val current = try {
            refresh(RefreshTrigger.CONFIRM_SCREEN)
        } catch (_: AuthRequiredException) {
            snapshot()
        }
        val resolved = ToggleResolver.resolve(action, current, nowMs())
        command = resolved.command
        _ui.value = ConfirmUi(
            label = commandText(resolved.command),
            age = resolved.age?.let { text(R.string.confirm_state_age, ageText(it)) } ?: text(R.string.confirm_state_unknown),
            phase = Phase.READY,
            message = null,
        )
    }

    suspend fun confirm() {
        val cmd = command ?: return
        // Every command is explicit (Lock, not "toggle lock"), but the trunk one actuates: only a command
        // that certainly did not run is sent again.
        if (_ui.value.phase != Phase.READY && _ui.value.phase != Phase.ERROR) return
        _ui.value = _ui.value.copy(phase = Phase.SENDING, message = text(R.string.progress_sending))
        val outcome = execute(cmd) { progress -> _ui.value = _ui.value.copy(message = progressText(progress)) }
        val failure = outcomeText(outcome)
        val phase = when {
            failure == null -> Phase.DONE
            outcome is RouteOutcome.Done && outcome.result == CommandResult.Unknown -> Phase.UNCERTAIN
            else -> Phase.ERROR
        }
        _ui.value = _ui.value.copy(phase = phase, message = failure)
        if (outcome is RouteOutcome.Done && outcome.result == CommandResult.Ok) {
            snapshot()?.let { save(it.afterCommand(cmd, nowMs(), outcome.channel)) }
        }
        background {
            runCatching { refresh(RefreshTrigger.AFTER_COMMAND) }
            afterCommand()
        }
    }
}
