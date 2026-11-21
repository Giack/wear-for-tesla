package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetCarConfig
import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.fleet.MemoryUsageStore
import dev.giack.teslawear.core.usage.UsageCounts
import dev.giack.teslawear.core.usage.UsageKind
import dev.giack.teslawear.core.usage.UsageMeter
import dev.giack.teslawear.protocol.VehicleCommand
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusRefresherTest {
    private val now = 1_792_238_400_000L
    private val meter = UsageMeter(MemoryUsageStore(), nowMs = { now })

    private fun refresher(ble: FakeBle, cloud: FakeCloud, store: MemorySnapshots, m: UsageMeter = meter): StatusRefresher {
        var clock = now
        return StatusRefresher(ble, cloud, m, store, nowMs = { clock }, sleep = { clock += it })
    }

    @Test fun tileRefreshNeverUsesCloud() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, updatedAtMs = now - 10 * 60_000))
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        val result = refresher(FakeBle(reachable = false), cloud, store).refresh(RefreshTrigger.TILE_VIEW)
        assertEquals(0, cloud.api.dataCalls)
        assertEquals(50, result!!.batteryPct)
    }

    @Test fun tileRefreshSkipsWhenFresh() = runTest {
        val ble = FakeBle(reachable = true)
        refresher(ble, FakeCloud(), MemorySnapshots(snap(updatedAtMs = now - 2 * 60_000))).refresh(RefreshTrigger.TILE_VIEW)
        assertEquals(0, ble.probes)
    }

    // The Tile runs as a service: Android rejects its scans (location is foreground-only), seen on the watch.
    @Test fun tileRefreshNeverScans() = runTest {
        val ble = FakeBle(reachable = true, status = snap(locked = true, updatedAtMs = now, channel = Channel.BLE))
        val stored = snap(locked = false, updatedAtMs = now - 10 * 60_000)
        val result = refresher(ble, FakeCloud(), MemorySnapshots(stored)).refresh(RefreshTrigger.TILE_VIEW)
        assertEquals(0, ble.probes)
        assertEquals(stored, result)
    }

    @Test fun tileRefreshMergesBleStatus() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, locked = false, updatedAtMs = now - 10 * 60_000))
        val ble = FakeBle(known = snap(locked = true, updatedAtMs = now, channel = Channel.BLE))
        val result = refresher(ble, FakeCloud(), store).refresh(RefreshTrigger.TILE_VIEW)!!
        assertEquals(true, result.locked)
        assertEquals(50, result.batteryPct)
        assertEquals(Channel.BLE, result.channel)
        assertEquals(result, store.snapshot)
    }

    @Test fun appOpenUsesCloudWhenBleAway() = runTest {
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        val result = refresher(FakeBle(), cloud, MemorySnapshots()).refresh(RefreshTrigger.APP_OPEN)
        assertEquals(80, result!!.batteryPct)
    }

    // The app comes to the foreground often: a state read a minute ago is not worth a billed call.
    @Test fun appOpenSkipsCloudWhenTheStateIsFresh() = runTest {
        val stored = snap(batteryPct = 50, updatedAtMs = now - 60_000)
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        val result = refresher(FakeBle(), cloud, MemorySnapshots(stored)).refresh(RefreshTrigger.APP_OPEN)
        assertEquals(0, cloud.api.dataCalls)
        assertEquals(stored, result)
    }

    @Test fun appOpenStillTriesBluetoothWhenTheStateIsFresh() = runTest {
        val ble = FakeBle(reachable = true, status = snap(locked = true, updatedAtMs = now, channel = Channel.BLE))
        val result = refresher(ble, FakeCloud(), MemorySnapshots(snap(locked = false, updatedAtMs = now - 60_000)))
            .refresh(RefreshTrigger.APP_OPEN)
        assertEquals(true, result!!.locked)
    }

    @Test fun afterCommandReadsCloudEvenWhenTheStateIsFresh() = runTest {
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        refresher(FakeBle(), cloud, MemorySnapshots(snap(batteryPct = 50, updatedAtMs = now - 60_000))).refresh(RefreshTrigger.AFTER_COMMAND)
        assertEquals(1, cloud.api.dataCalls)
    }

    // An asleep answer keeps the old data date, so the check time is what makes the state fresh.
    @Test fun appOpenDoesNotAskAgainRightAfterFindingTheCarAsleep() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, updatedAtMs = 1))
        val cloud = FakeCloud()
        val r = refresher(FakeBle(), cloud, store)
        r.refresh(RefreshTrigger.APP_OPEN)
        r.refresh(RefreshTrigger.APP_OPEN)
        assertEquals(1, cloud.api.dataCalls)
        assertEquals(1, store.snapshot!!.updatedAtMs)
    }

    @Test fun appOpenKeepsSnapshotWhenAsleep() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, updatedAtMs = 1))
        val cloud = FakeCloud()
        val result = refresher(FakeBle(), cloud, store).refresh(RefreshTrigger.APP_OPEN)
        // Fleet API has no data for a sleeping car: the stored data stays, with its age, marked asleep.
        assertEquals(snap(batteryPct = 50, updatedAtMs = 1).copy(asleep = true, checkedAtMs = now), result)
        assertEquals(result, store.snapshot)
        assertEquals(0, cloud.api.wakes)
    }

    @Test fun manualRefreshWakesAnAsleepCarThenReads() = runTest {
        val cloud = FakeCloud().apply {
            api.states += FleetResult.Ok("asleep"); api.states += FleetResult.Ok("online")
            api.dataAfterWake = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now))
        }
        val result = refresher(FakeBle(), cloud, MemorySnapshots(snap(batteryPct = 50, updatedAtMs = 1))).refresh(RefreshTrigger.MANUAL)
        assertEquals(1, cloud.api.wakes)
        assertEquals(80, result!!.batteryPct)
    }

    @Test fun manualRefreshGivesUpWhenTheCarDoesNotWake() = runTest {
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("asleep") }
        val store = MemorySnapshots(snap(batteryPct = 50, updatedAtMs = 1))
        val result = refresher(FakeBle(), cloud, store).refresh(RefreshTrigger.MANUAL)!!
        // Checked when the wake attempt gave up, after the waiting.
        assertEquals(snap(batteryPct = 50, updatedAtMs = 1).copy(asleep = true), result.copy(checkedAtMs = null))
        assertTrue(result.checkedAtMs!! > now)
        assertEquals(1, cloud.api.dataCalls)
    }

    @Test fun manualRefreshUsesBleWithoutWaking() = runTest {
        val cloud = FakeCloud()
        val ble = FakeBle(reachable = true, status = snap(batteryPct = 70, updatedAtMs = now, channel = Channel.BLE))
        val result = refresher(ble, cloud, MemorySnapshots()).refresh(RefreshTrigger.MANUAL)
        assertEquals(70, result!!.batteryPct)
        assertEquals(0, cloud.api.wakes)
    }

    @Test fun manualRefreshDoesNotWakeWhenBudgetExhausted() = runTest {
        val exhausted = UsageMeter(MemoryUsageStore(UsageCounts("2026-10", 0, 0, 500)), nowMs = { now })
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("online") }
        refresher(FakeBle(), cloud, MemorySnapshots(), exhausted).refresh(RefreshTrigger.MANUAL)
        assertEquals(0, cloud.api.wakes)
        assertEquals(0, cloud.api.dataCalls)
    }

    @Test fun appOpenSkipsCloudWhenBudgetExhausted() = runTest {
        val exhausted = UsageMeter(MemoryUsageStore(UsageCounts("2026-10", 0, 0, 500)), nowMs = { now })
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        refresher(FakeBle(), cloud, MemorySnapshots(), exhausted).refresh(RefreshTrigger.APP_OPEN)
        assertEquals(0, cloud.api.dataCalls)
    }

    @Test fun confirmScreenUsesBleOnly() = runTest {
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        refresher(FakeBle(), cloud, MemorySnapshots()).refresh(RefreshTrigger.CONFIRM_SCREEN)
        assertEquals(0, cloud.api.dataCalls)
    }

    @Test fun cloudReadHandsOverTheCarConfiguration() = runTest {
        val config = FleetCarConfig(CarModel.MODEL_Y, paint = "RED", wheels = "Helix20")
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now).copy(carConfig = config)) }
        val received = mutableListOf<FleetCarConfig>()
        StatusRefresher(FakeBle(reachable = false), cloud, meter, MemorySnapshots(), nowMs = { now }, onCarConfig = { received += it })
            .refresh(RefreshTrigger.APP_OPEN)
        assertEquals(listOf(config), received)
    }

    @Test fun failedCloudReadsAreReportedNotSwallowed() = runTest {
        val errors = mutableListOf<Exception>()
        fun refresherWith(cloud: FakeCloud) =
            StatusRefresher(FakeBle(reachable = false), cloud, meter, MemorySnapshots(), nowMs = { now }, onCloudError = { errors += it })

        refresherWith(FakeCloud().apply { api.failWith = java.io.IOException("region not found") }).refresh(RefreshTrigger.APP_OPEN)
        refresherWith(FakeCloud().apply { api.data = FleetResult.Error(403, "forbidden") }).refresh(RefreshTrigger.APP_OPEN)

        assertEquals(listOf("region not found", "Fleet API 403: forbidden"), errors.map { it.message })
    }

    // Background: no scan (Android blocks it there), a direct connection to the car seen before.
    @Test fun backgroundReadsTheKnownCarOverBluetoothWithoutScanning() = runTest {
        val ble = FakeBle(known = snap(locked = true, updatedAtMs = now, channel = Channel.BLE))
        val cloud = FakeCloud()
        val result = refresher(ble, cloud, MemorySnapshots(snap(locked = false, updatedAtMs = 1))).refresh(RefreshTrigger.BACKGROUND)
        assertEquals(true, result!!.locked)
        assertEquals(0, ble.probes)
        assertEquals(0, cloud.api.dataCalls)
    }

    @Test fun backgroundReadsTheCloudOnceWhenTheCarIsNotNear() = runTest {
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        val result = refresher(FakeBle(), cloud, MemorySnapshots(snap(batteryPct = 50, updatedAtMs = 1))).refresh(RefreshTrigger.BACKGROUND)
        assertEquals(80, result!!.batteryPct)
        assertEquals(1, cloud.api.dataCalls)
    }

    @Test fun backgroundNeverWakesASleepingCar() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, updatedAtMs = 1))
        val cloud = FakeCloud()
        val result = refresher(FakeBle(), cloud, store).refresh(RefreshTrigger.BACKGROUND)
        assertEquals(true, result!!.asleep)
        assertEquals(0, cloud.api.wakes)
        assertEquals(1, cloud.api.dataCalls)
    }

    // The app was opened (or the car checked) a few minutes ago: the background run adds nothing.
    @Test fun backgroundSkipsTheCloudWhenRecentlyChecked() = runTest {
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        refresher(FakeBle(), cloud, MemorySnapshots(snap(batteryPct = 50, updatedAtMs = now - 10 * 60_000))).refresh(RefreshTrigger.BACKGROUND)
        assertEquals(0, cloud.api.dataCalls)
    }

    @Test fun backgroundSkipsTheCloudWhenTheBudgetIsExhausted() = runTest {
        val exhausted = UsageMeter(MemoryUsageStore(UsageCounts("2026-10", 0, 0, 500)), nowMs = { now })
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        refresher(FakeBle(), cloud, MemorySnapshots(snap(updatedAtMs = 1)), exhausted).refresh(RefreshTrigger.BACKGROUND)
        assertEquals(0, cloud.api.dataCalls)
    }

    @Test fun backgroundInBluetoothModeOnlyTriesBluetooth() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, updatedAtMs = 1))
        val result = StatusRefresher(FakeBle(), null, meter, store, nowMs = { now }).refresh(RefreshTrigger.BACKGROUND)
        assertEquals(snap(batteryPct = 50, updatedAtMs = 1), result)
    }

    // A wake or a command makes the stored fields stale even when the last check was moments ago.
    @Test fun appOpenReadsAgainWithinTwoMinutesOfACommand() = runTest {
        val stored = snap(locked = true, batteryPct = 50, updatedAtMs = now - 30_000)
            .copy(checkedAtMs = now - 20_000)
            .afterCommand(VehicleCommand.Unlock, now - 10_000, Channel.CLOUD)
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(locked = false, batteryPct = 80, updatedAtMs = now)) }
        val ble = FakeBle(canWakeOverBle = true)
        val result = refresher(ble, cloud, MemorySnapshots(stored)).refresh(RefreshTrigger.APP_OPEN)!!
        assertEquals(1, cloud.api.dataCalls)
        assertEquals(0, cloud.api.wakes)
        assertEquals(0, ble.bleWakes)
        assertEquals(false, result.locked)
        assertEquals(null, result.pendingCommand)
        assertEquals(false, result.asleep)
    }

    @Test fun appOpenReadsAgainWithinTwoMinutesOfAWake() = runTest {
        val stored = snap(batteryPct = 50, updatedAtMs = now - 3_600_000).copy(
            asleep = false, checkedAtMs = now - 30_000, actedAtMs = now - 20_000,
        )
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 80, updatedAtMs = now)) }
        val result = refresher(FakeBle(canWakeOverBle = true), cloud, MemorySnapshots(stored)).refresh(RefreshTrigger.APP_OPEN)
        assertEquals(1, cloud.api.dataCalls)
        assertEquals(0, cloud.api.wakes)
        assertEquals(80, result!!.batteryPct)
        assertEquals(null, result.actedAtMs)
    }

    @Test fun appOpenSkipsOnceAReadHasLandedAfterTheWake() = runTest {
        val stored = snap(batteryPct = 80, updatedAtMs = now - 30_000).copy(actedAtMs = now - 40_000, asleep = false)
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(batteryPct = 10, updatedAtMs = now)) }
        refresher(FakeBle(), cloud, MemorySnapshots(stored)).refresh(RefreshTrigger.APP_OPEN)
        assertEquals(0, cloud.api.dataCalls)
        assertEquals(0, cloud.api.wakes)
    }

    @Test fun readsNeverWakeTheCar() = runTest {
        val triggers = listOf(
            RefreshTrigger.APP_OPEN, RefreshTrigger.AFTER_COMMAND, RefreshTrigger.TILE_VIEW,
            RefreshTrigger.BACKGROUND, RefreshTrigger.CONFIRM_SCREEN,
        )
        for (trigger in triggers) {
            val ble = FakeBle(canWakeOverBle = true)
            val cloud = FakeCloud()
            val store = MemorySnapshots(snap(locked = true, batteryPct = 50, updatedAtMs = 1).copy(asleep = true))
            refresher(ble, cloud, store).refresh(trigger, CommandFollowUp(VehicleCommand.Unlock, fleetOrWoken = true))
            assertEquals(0, cloud.api.wakes, trigger.name)
            assertEquals(0, ble.bleWakes, trigger.name)
        }
    }

    @Test fun followUpReplacesAStaleReadAndStops() = runTest {
        val sleeps = mutableListOf<Long>()
        val cloud = FakeCloud().apply {
            api.scripted += FleetResult.Ok(snap(locked = true, batteryPct = 50, updatedAtMs = now))
            api.scripted += FleetResult.Ok(snap(locked = false, batteryPct = 80, updatedAtMs = now + 3_000))
            api.scripted += FleetResult.Ok(snap(locked = false, batteryPct = 81, updatedAtMs = now + 11_000))
        }
        val ble = FakeBle(canWakeOverBle = true)
        val store = MemorySnapshots(snap(locked = true, batteryPct = 40, updatedAtMs = now - 60_000).copy(asleep = true)
            .afterCommand(VehicleCommand.Unlock, now - 1_000, Channel.CLOUD))
        val seen = mutableListOf<Snapshot>()
        val result = StatusRefresher(ble, cloud, meter, store, nowMs = { now }, sleep = { sleeps += it })
            .refresh(RefreshTrigger.AFTER_COMMAND, CommandFollowUp(VehicleCommand.Unlock, fleetOrWoken = true), seen::add)!!
        assertEquals(listOf(3_000L), sleeps)
        assertEquals(2, cloud.api.dataCalls)
        assertEquals(0, cloud.api.wakes)
        assertEquals(0, ble.bleWakes)
        assertEquals(false, result.locked)
        assertEquals(80, result.batteryPct)
        assertEquals(now + 3_000, result.updatedAtMs)
        assertEquals(null, result.pendingCommand)
        assertEquals(false, result.asleep)
        assertEquals(listOf(50, 80), seen.map { it.batteryPct })
        assertEquals(false, seen.first().locked)
    }

    @Test fun followUpStopsWhenTheFirstReadAlreadyMatches() = runTest {
        val sleeps = mutableListOf<Long>()
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(locked = false, batteryPct = 80, updatedAtMs = now)) }
        val store = MemorySnapshots(snap(locked = true, updatedAtMs = now - 60_000).afterCommand(VehicleCommand.Unlock, now, Channel.CLOUD))
        val result = StatusRefresher(FakeBle(), cloud, meter, store, nowMs = { now }, sleep = { sleeps += it })
            .refresh(RefreshTrigger.AFTER_COMMAND, CommandFollowUp(VehicleCommand.Unlock, fleetOrWoken = true))
        assertEquals(1, cloud.api.dataCalls)
        assertEquals(emptyList<Long>(), sleeps)
        assertEquals(0, cloud.api.wakes)
        assertEquals(false, result!!.locked)
        assertEquals(null, result.pendingCommand)
    }

    @Test fun followUpStopsWhenTheBudgetIsExhausted() = runTest {
        val tight = UsageMeter(MemoryUsageStore(), nowMs = { now }, budget = { UsageMeter.DATA_USD })
        val cloud = FakeCloud().apply {
            api.data = FleetResult.Ok(snap(locked = true, batteryPct = 55, updatedAtMs = now))
            api.onData = { tight.record(UsageKind.DATA) }
        }
        val store = MemorySnapshots(snap(locked = true, batteryPct = 40, updatedAtMs = now - 60_000)
            .afterCommand(VehicleCommand.Unlock, now, Channel.CLOUD))
        val result = StatusRefresher(FakeBle(canWakeOverBle = true), cloud, tight, store, nowMs = { now }, sleep = {})
            .refresh(RefreshTrigger.AFTER_COMMAND, CommandFollowUp(VehicleCommand.Unlock, fleetOrWoken = true))!!
        assertEquals(1, cloud.api.dataCalls)
        assertEquals(0, cloud.api.wakes)
        assertEquals(false, result.locked)
        assertEquals(55, result.batteryPct)
        assertEquals("unlock", result.pendingCommand)
        assertEquals(now, result.updatedAtMs)
    }

    @Test fun asleepDuringFollowUpDoesNotWake() = runTest {
        val ble = FakeBle(canWakeOverBle = true)
        val cloud = FakeCloud()
        val store = MemorySnapshots(snap(locked = true, batteryPct = 40, updatedAtMs = now - 60_000).copy(asleep = true)
            .afterCommand(VehicleCommand.Unlock, now, Channel.CLOUD))
        val result = refresher(ble, cloud, store)
            .refresh(RefreshTrigger.AFTER_COMMAND, CommandFollowUp(VehicleCommand.Unlock, fleetOrWoken = true))!!
        assertEquals(3, cloud.api.dataCalls)
        assertEquals(0, cloud.api.wakes)
        assertEquals(0, ble.bleWakes)
        assertEquals(false, result.locked)
        assertEquals(false, result.asleep)
        assertEquals("unlock", result.pendingCommand)
        assertEquals(now - 60_000, result.updatedAtMs)
    }

    @Test fun bluetoothCommandDoesNotChase() = runTest {
        val sleeps = mutableListOf<Long>()
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(locked = true, updatedAtMs = now)) }
        val store = MemorySnapshots(snap(locked = true, updatedAtMs = now - 60_000).afterCommand(VehicleCommand.Unlock, now, Channel.BLE))
        StatusRefresher(FakeBle(), cloud, meter, store, nowMs = { now }, sleep = { sleeps += it })
            .refresh(RefreshTrigger.AFTER_COMMAND, CommandFollowUp(VehicleCommand.Unlock, fleetOrWoken = false))
        assertEquals(1, cloud.api.dataCalls)
        assertEquals(emptyList<Long>(), sleeps)
        assertEquals(0, cloud.api.wakes)
    }

    @Test fun aStaleBluetoothReadDoesNotUndoTheCommandOrMarkTheCarAsleep() = runTest {
        val ble = FakeBle(
            reachable = true, canWakeOverBle = true,
            status = snap(locked = true, batteryPct = 40, updatedAtMs = now, channel = Channel.BLE).copy(asleep = true),
        )
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(locked = false, updatedAtMs = now)) }
        val store = MemorySnapshots(snap(locked = true, batteryPct = 40, updatedAtMs = now - 60_000).copy(asleep = true)
            .afterCommand(VehicleCommand.Unlock, now, Channel.CLOUD))
        val result = refresher(ble, cloud, store)
            .refresh(RefreshTrigger.AFTER_COMMAND, CommandFollowUp(VehicleCommand.Unlock, fleetOrWoken = true))!!
        assertEquals(0, cloud.api.dataCalls)
        assertEquals(0, cloud.api.wakes)
        assertEquals(0, ble.bleWakes)
        assertEquals(false, result.locked)
        assertEquals(false, result.asleep)
        assertEquals("unlock", result.pendingCommand)
    }

    @Test fun followUpPrefersBluetoothWhenTheCarIsInRange() = runTest {
        val ble = FakeBle(reachable = true, canWakeOverBle = true, status = snap(locked = false, batteryPct = 70, updatedAtMs = now, channel = Channel.BLE))
        val cloud = FakeCloud().apply { api.data = FleetResult.Ok(snap(locked = true, updatedAtMs = now)) }
        val store = MemorySnapshots(snap(locked = true, updatedAtMs = now - 60_000).afterCommand(VehicleCommand.Unlock, now, Channel.CLOUD))
        val result = refresher(ble, cloud, store)
            .refresh(RefreshTrigger.AFTER_COMMAND, CommandFollowUp(VehicleCommand.Unlock, fleetOrWoken = true))
        assertEquals(0, cloud.api.dataCalls)
        assertEquals(0, cloud.api.wakes)
        assertEquals(0, ble.bleWakes)
        assertEquals(false, result!!.locked)
        assertEquals(70, result.batteryPct)
        assertEquals(Channel.BLE, result.channel)
    }
}
