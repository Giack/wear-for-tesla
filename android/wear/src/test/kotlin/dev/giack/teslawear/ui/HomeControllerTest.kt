package dev.giack.teslawear.ui

import dev.giack.teslawear.R
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.RefreshTrigger
import dev.giack.teslawear.core.RouteOutcome
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.core.usage.BudgetLevel
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.VehicleCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeControllerTest {
    private val now = 1_792_238_400_000L
    private val snap = Snapshot(batteryPct = 78, rangeKm = 312, locked = true, setTempC = 20.5f, updatedAtMs = now - 3 * 60_000,
        channel = Channel.BLE)

    private class Rec {
        val refreshes = mutableListOf<RefreshTrigger>()
        val sent = mutableListOf<VehicleCommand>()
    }

    private fun TestScope.controller(
        rec: Rec = Rec(), stored: Snapshot? = snap, usd: Double = 2.4, budget: Double = 10.0, level: BudgetLevel = BudgetLevel.OK,
        refreshError: Exception? = null, fresh: Snapshot? = stored,
    ) = HomeController(
        refresh = { rec.refreshes += it; refreshError?.let { e -> throw e }; fresh },
        snapshot = { stored },
        execute = { cmd, _ -> rec.sent += cmd; RouteOutcome.Done(CommandResult.Ok, Channel.BLE) },
        usage = { UsageInfo(usd, budget, level) },
        scope = this,
        nowMs = { now },
    )

    @Test fun homeRefreshesOnOpen() = runTest {
        val rec = Rec()
        val c = controller(rec)
        c.open()
        assertEquals(listOf(RefreshTrigger.APP_OPEN), rec.refreshes)
        assertEquals(78, c.ui.value.snapshot!!.batteryPct)
        assertEquals(UiText.Joined(listOf(text(R.string.status_locked)), " · "), c.ui.value.statusLine)
        assertEquals(text(R.string.home_updated, text(R.string.age_minutes, 3)), c.ui.value.updated)
    }

    @Test fun openWithoutNewDataShowsTheStoredStateQuietly() = runTest {
        val c = controller()
        c.open()
        assertEquals(78, c.ui.value.snapshot!!.batteryPct)
        assertEquals(null, c.ui.value.message)
        assertFalse(c.ui.value.busy)
    }

    @Test fun anAsleepCarShowsTheAgeOfItsData() = runTest {
        val c = controller(fresh = snap.copy(asleep = true))
        c.open()
        assertEquals(text(R.string.home_asleep, text(R.string.age_minutes, 3)), c.ui.value.updated)
    }

    @Test fun refreshNowAsksForAManualRefresh() = runTest {
        val rec = Rec()
        val c = controller(rec, fresh = snap.copy(batteryPct = 80, updatedAtMs = now))
        c.refreshNow()
        assertEquals(listOf(RefreshTrigger.MANUAL), rec.refreshes)
        assertEquals(80, c.ui.value.snapshot!!.batteryPct)
        assertEquals(null, c.ui.value.message)
        assertFalse(c.ui.value.busy)
    }

    @Test fun refreshNowThatGetsNothingSaysSo() = runTest {
        val c = controller()
        c.refreshNow()
        assertEquals(text(R.string.home_unreachable_now), c.ui.value.message)
        assertTrue(c.ui.value.messageIsError)
    }

    @Test fun homeShowsBudgetWarn() = runTest {
        val c = controller(usd = 4.25, budget = 5.0, level = BudgetLevel.WARN)
        c.open()
        assertEquals(BudgetLevel.WARN, c.ui.value.usageTone)
        assertEquals(text(R.string.home_usage, 4.25, 5.0), c.ui.value.usageText)
    }

    @Test fun bluetoothModeShowsNoApiUsage() = runTest {
        val c = HomeController(
            refresh = { snap }, snapshot = { snap }, execute = { _, _ -> RouteOutcome.Unreachable }, usage = { null },
            scope = this, nowMs = { now },
        )
        c.open()
        assertEquals(null, c.ui.value.usageText)
    }

    @Test fun authRequiredIsFlagged() = runTest {
        val c = controller(refreshError = AuthRequiredException())
        c.open()
        assertTrue(c.ui.value.authRequired)
    }

    @Test fun sendResolvesToggleAndRefreshes() = runTest {
        val rec = Rec()
        val c = controller(rec)
        c.open()
        c.send(QuickAction.LOCK)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Unlock), rec.sent)
        assertEquals(RefreshTrigger.AFTER_COMMAND, rec.refreshes.last())
    }

    @Test fun tempChangesAreDebounced() = runTest {
        val rec = Rec()
        val c = controller(rec)
        c.open()
        repeat(3) { c.adjustTemp(+0.5f); advanceTimeBy(300) }
        assertEquals(22.0f, c.ui.value.pendingTemp)
        assertTrue(rec.sent.isEmpty())
        advanceTimeBy(1_600); runCurrent()
        assertEquals(listOf<VehicleCommand>(VehicleCommand.SetTemp(22.0f)), rec.sent)
    }

    @Test fun aSetPointTappedDuringTheSendStaysOnScreen() = runTest {
        val rec = Rec()
        lateinit var c: HomeController
        c = HomeController(
            refresh = { snap }, snapshot = { snap },
            // The user taps + again while the first set point is on its way to the car.
            execute = { cmd, _ -> rec.sent += cmd; if (rec.sent.size == 1) c.adjustTemp(+0.5f); RouteOutcome.Done(CommandResult.Ok, Channel.BLE) },
            usage = { UsageInfo(0.0, 10.0, BudgetLevel.OK) }, scope = this, nowMs = { now },
        )
        c.adjustTemp(+0.5f)
        advanceTimeBy(1_600); runCurrent()
        assertEquals(listOf<VehicleCommand>(VehicleCommand.SetTemp(21.5f)), rec.sent)
        assertEquals(22.0f, c.ui.value.pendingTemp)
        advanceTimeBy(1_600); runCurrent()
        assertEquals(listOf<VehicleCommand>(VehicleCommand.SetTemp(21.5f), VehicleCommand.SetTemp(22.0f)), rec.sent)
        assertEquals(null, c.ui.value.pendingTemp)
    }

    @Test fun tempIsClampedToTeslaRange() = runTest {
        val c = controller(stored = snap.copy(setTempC = 27.5f))
        c.open()
        repeat(4) { c.adjustTemp(+0.5f) }
        assertEquals(28.0f, c.ui.value.pendingTemp)
    }

    @Test fun authRequiredClearsAfterSuccessfulOpen() = runTest {
        var fail = true
        val c = HomeController(
            refresh = { if (fail) throw AuthRequiredException() else snap },
            snapshot = { snap }, execute = { _, _ -> RouteOutcome.AuthRequired }, usage = { UsageInfo(0.0, 10.0, BudgetLevel.OK) },
            scope = this, nowMs = { now },
        )
        c.open()
        assertTrue(c.ui.value.authRequired)
        fail = false
        c.open()
        assertFalse(c.ui.value.authRequired)
    }

    @Test fun aFailedCommandIsShownAsAnError() = runTest {
        val c = HomeController(
            refresh = { snap }, snapshot = { snap }, execute = { _, _ -> RouteOutcome.OutOfRange }, usage = { null },
            scope = this, nowMs = { now },
        )
        c.open()
        c.send(QuickAction.LOCK)
        assertEquals(text(R.string.outcome_out_of_range), c.ui.value.message)
        assertTrue(c.ui.value.messageIsError)
    }

    @Test fun aCommandInFlightNamesItsActionAndProgress() = runTest {
        val messages = mutableListOf<UiText?>()
        lateinit var c: HomeController
        c = HomeController(
            refresh = { snap }, snapshot = { snap },
            execute = { _, progress ->
                messages += c.ui.value.message
                progress(dev.giack.teslawear.core.Progress.WAKING_CAR)
                messages += c.ui.value.message
                RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD)
            },
            usage = { null }, scope = this, nowMs = { now },
        )
        c.open()
        c.send(QuickAction.LOCK)
        assertEquals(listOf(text(R.string.progress_running, text(R.string.command_unlock)), text(R.string.progress_waking_car)), messages)
        assertEquals(null, c.ui.value.message)
        assertFalse(c.ui.value.messageIsError)
    }
}
