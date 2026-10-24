package dev.giack.teslawear.ui

import dev.giack.teslawear.R
import dev.giack.teslawear.core.Age
import dev.giack.teslawear.core.Progress
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.RefreshTrigger
import dev.giack.teslawear.core.RouteOutcome
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.ToggleResolver
import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.core.usage.BudgetLevel
import dev.giack.teslawear.protocol.VehicleCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUi(
    val snapshot: Snapshot? = null,
    /** e.g. LOCKED · CHARGING; null when nothing is known. */
    val statusLine: UiText? = null,
    val updated: UiText? = null,
    /** Null in Bluetooth mode. */
    val usageText: UiText? = null,
    val usageTone: BudgetLevel = BudgetLevel.OK,
    val busy: Boolean = false,
    val message: UiText? = null,
    /** The message reports a failure (drawn in red) rather than progress. */
    val messageIsError: Boolean = false,
    val pendingTemp: Float? = null,
    val authRequired: Boolean = false,
)

/** Fleet API spending this month against the user's budget. */
data class UsageInfo(val usd: Double, val budgetUsd: Double, val level: BudgetLevel)

/** State and actions of the app's home screen (UI-framework free for testing). */
class HomeController(
    private val refresh: suspend (RefreshTrigger) -> Snapshot?,
    private val snapshot: suspend () -> Snapshot?,
    private val execute: suspend (VehicleCommand, (Progress) -> Unit) -> RouteOutcome,
    /** Null in Bluetooth mode, where nothing is spent on the Fleet API. */
    private val usage: () -> UsageInfo?,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 1_500,
) {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui
    private var tempJob: Job? = null

    /** Shows the stored state at once, then tries to update it without waking the car. */
    suspend fun open() {
        show(snapshot())
        _ui.value = _ui.value.copy(busy = true)
        val fresh = try {
            refresh(RefreshTrigger.APP_OPEN)
        } catch (_: AuthRequiredException) {
            _ui.value = _ui.value.copy(authRequired = true, busy = false)
            return
        }
        show(fresh ?: snapshot())
        _ui.value = _ui.value.copy(busy = false, authRequired = false)
    }

    /** "Refresh": the one refresh allowed to wake the car (over Fleet API, ~30 s). */
    suspend fun refreshNow() {
        val before = snapshot()
        _ui.value = _ui.value.copy(busy = true, message = text(R.string.home_refreshing), messageIsError = false)
        val fresh = try {
            refresh(RefreshTrigger.MANUAL)
        } catch (_: AuthRequiredException) {
            _ui.value = _ui.value.copy(authRequired = true, busy = false, message = null)
            return
        }
        show(fresh ?: snapshot())
        val newer = isNewer(fresh, before)
        _ui.value = _ui.value.copy(busy = false, message = if (newer) null else text(R.string.home_unreachable_now), messageIsError = !newer)
    }

    private fun isNewer(fresh: Snapshot?, before: Snapshot?) =
        fresh != null && (before == null || fresh.updatedAtMs > before.updatedAtMs)

    suspend fun send(action: QuickAction) {
        val resolved = ToggleResolver.resolve(action, _ui.value.snapshot, nowMs())
        run(resolved.command)
    }

    /** ±0.5 °C steps, sent once the user stops tapping for [debounceMs]. */
    fun adjustTemp(delta: Float) {
        val base = _ui.value.pendingTemp ?: _ui.value.snapshot?.setTempC ?: DEFAULT_TEMP
        val target = (base + delta).coerceIn(MIN_TEMP, MAX_TEMP)
        _ui.value = _ui.value.copy(pendingTemp = target)
        // A tap cancels only the wait: a set point already on its way to the car is never interrupted.
        tempJob?.cancel()
        tempJob = scope.launch {
            delay(debounceMs)
            scope.launch { run(VehicleCommand.SetTemp(target)) }
        }
    }

    private suspend fun run(command: VehicleCommand) {
        _ui.value = _ui.value.copy(busy = true, message = text(R.string.progress_running, commandText(command)), messageIsError = false)
        val outcome = execute(command) { p -> _ui.value = _ui.value.copy(message = progressText(p)) }
        val message = outcomeText(outcome)
        val fresh = runCatching { refresh(RefreshTrigger.AFTER_COMMAND) }.getOrNull()
        show(fresh ?: snapshot())
        // A newer set point tapped while this command ran stays on screen until its own send.
        val pending = _ui.value.pendingTemp.takeUnless { command is VehicleCommand.SetTemp && it == command.celsius }
        _ui.value = _ui.value.copy(busy = false, message = message, messageIsError = message != null, pendingTemp = pending,
            authRequired = _ui.value.authRequired || outcome == RouteOutcome.AuthRequired)
    }

    private fun show(s: Snapshot?) {
        val u = usage()
        _ui.value = _ui.value.copy(
            snapshot = s,
            statusLine = statusLine(s),
            updated = s?.let {
                val age = ageText(Age.of(it.updatedAtMs, nowMs()))
                text(if (it.asleep == true) R.string.home_asleep else R.string.home_updated, age)
            },
            usageText = u?.let { text(R.string.home_usage, it.usd, it.budgetUsd) },
            usageTone = u?.level ?: BudgetLevel.OK,
        )
    }

    private fun statusLine(s: Snapshot?): UiText? = listOfNotNull(
        when (s?.locked) { true -> R.string.status_locked; false -> R.string.status_unlocked; null -> null },
        if (s?.charging == true) R.string.status_charging else null,
        if (s?.climateOn == true) R.string.status_climate_on else null,
    ).map { text(it) }.takeIf { it.isNotEmpty() }?.let { UiText.Joined(it, " · ") }

    companion object {
        const val DEFAULT_TEMP = 21.0f
        const val MIN_TEMP = 15.0f
        const val MAX_TEMP = 28.0f
    }
}
