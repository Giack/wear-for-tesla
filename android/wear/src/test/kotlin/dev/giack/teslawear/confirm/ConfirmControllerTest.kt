package dev.giack.teslawear.confirm

import dev.giack.teslawear.R
import dev.giack.teslawear.core.Progress
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.RefreshTrigger
import dev.giack.teslawear.core.RouteOutcome
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.VehicleCommand
import dev.giack.teslawear.ui.text
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmControllerTest {
    private val now = 10L * 3_600_000
    private val locked2hAgo = Snapshot(locked = true, updatedAtMs = now - 2 * 3_600_000, channel = Channel.CLOUD)

    private class Recorder {
        val refreshes = mutableListOf<RefreshTrigger>()
        val sent = mutableListOf<VehicleCommand>()
        var afterCommandCalls = 0
        val saved = mutableListOf<Snapshot>()
        val background = mutableListOf<suspend () -> Unit>()
    }

    private fun controller(
        action: QuickAction, stored: Snapshot?, outcome: RouteOutcome = RouteOutcome.Done(CommandResult.Ok, Channel.BLE),
        rec: Recorder = Recorder(),
    ) = ConfirmController(
        action = action,
        snapshot = { stored },
        refresh = { rec.refreshes += it; stored },
        execute = { cmd, progress -> rec.sent += cmd; progress(Progress.WAKING_CAR); outcome },
        afterCommand = { rec.afterCommandCalls++ },
        save = { rec.saved += it },
        background = { rec.background += it },
        nowMs = { now },
    )

    @Test fun confirmShowsResolvedLabelAndAge() = runTest {
        val c = controller(QuickAction.LOCK, locked2hAgo)
        c.resolve()
        assertEquals(
            ConfirmUi(text(R.string.command_unlock), text(R.string.confirm_state_age, text(R.string.age_hours, 2)), Phase.READY, null),
            c.ui.value,
        )
    }

    @Test fun unknownStateIsShown() = runTest {
        val c = controller(QuickAction.LOCK, null)
        c.resolve()
        assertEquals(ConfirmUi(text(R.string.command_lock), text(R.string.confirm_state_unknown), Phase.READY, null), c.ui.value)
    }

    @Test fun resolveRefreshesOverBleFirst() = runTest {
        val rec = Recorder()
        controller(QuickAction.LOCK, locked2hAgo, rec = rec).resolve()
        assertEquals(listOf(RefreshTrigger.CONFIRM_SCREEN), rec.refreshes)
    }

    @Test fun confirmSendsResolvedCommand() = runTest {
        val rec = Recorder()
        val c = controller(QuickAction.LOCK, locked2hAgo, rec = rec)
        c.resolve(); c.confirm()
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Unlock), rec.sent)
        assertEquals(Phase.DONE, c.ui.value.phase)
        rec.background.forEach { it() }
        assertEquals(listOf(RefreshTrigger.CONFIRM_SCREEN, RefreshTrigger.AFTER_COMMAND), rec.refreshes)
        assertEquals(1, rec.afterCommandCalls)
    }

    @Test fun successSavesExpectedStateBeforeScreenCloses() = runTest {
        val rec = Recorder()
        val c = controller(QuickAction.LOCK, locked2hAgo, rec = rec)
        c.resolve(); c.confirm()
        assertEquals(false, rec.saved.single().locked)
        assertEquals(now, rec.saved.single().updatedAtMs)
    }

    @Test fun refreshRunsOutsideTheScreenScope() = runTest {
        val rec = Recorder()
        val c = controller(QuickAction.LOCK, locked2hAgo, rec = rec)
        c.resolve(); c.confirm()
        assertEquals(listOf(RefreshTrigger.CONFIRM_SCREEN), rec.refreshes)
        assertEquals(1, rec.background.size)
    }

    @Test fun confirmMapsUnknownOutcome() = runTest {
        val rec = Recorder()
        val c = controller(QuickAction.TRUNK, locked2hAgo, RouteOutcome.Done(CommandResult.Unknown, Channel.CLOUD), rec)
        c.resolve(); c.confirm()
        assertEquals(Phase.UNCERTAIN, c.ui.value.phase)
        assertEquals(text(R.string.outcome_unknown), c.ui.value.message)
        assertTrue(rec.saved.isEmpty())
        rec.background.forEach { it() }
        assertEquals(RefreshTrigger.AFTER_COMMAND, rec.refreshes.last())
    }

    @Test fun errorMessages() = runTest {
        suspend fun messageFor(o: RouteOutcome) = controller(QuickAction.HORN, null, o).also { it.resolve(); it.confirm() }.ui.value.message
        assertEquals(text(R.string.outcome_no_connection), messageFor(RouteOutcome.NoConnection))
        assertEquals(text(R.string.outcome_unreachable), messageFor(RouteOutcome.Unreachable))
        assertEquals(text(R.string.outcome_sign_in_again), messageFor(RouteOutcome.AuthRequired))
        assertEquals(text(R.string.outcome_out_of_range), messageFor(RouteOutcome.OutOfRange))
        assertEquals(text(R.string.outcome_error, "not_charging"), messageFor(RouteOutcome.Done(CommandResult.Failed("not_charging"), Channel.BLE)))
    }

    @Test fun holdAfterAnErrorSendsTheSameCommandAgain() = runTest {
        val rec = Recorder()
        val c = controller(QuickAction.LOCK, locked2hAgo, RouteOutcome.NoConnection, rec)
        c.resolve(); c.confirm()
        assertEquals(Phase.ERROR, c.ui.value.phase)
        c.confirm()
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Unlock, VehicleCommand.Unlock), rec.sent)
    }

    // The trunk command toggles: after an unknown outcome a second hold could undo the first.
    @Test fun holdAfterAnUnknownOutcomeSendsNothing() = runTest {
        val rec = Recorder()
        val c = controller(QuickAction.TRUNK, null, RouteOutcome.Done(CommandResult.Unknown, Channel.BLE), rec)
        c.resolve(); c.confirm(); c.confirm()
        assertEquals(1, rec.sent.size)
    }

    @Test fun confirmTwiceSendsOnce() = runTest {
        val rec = Recorder()
        val c = controller(QuickAction.FRUNK, null, rec = rec)
        c.resolve(); c.confirm(); c.confirm()
        assertEquals(1, rec.sent.size)
    }
}
