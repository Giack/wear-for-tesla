package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetCarConfig
import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.fleet.MemoryUsageStore
import dev.giack.teslawear.core.usage.UsageCounts
import dev.giack.teslawear.core.usage.UsageMeter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun tileRefreshMergesBleStatus() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, locked = false, updatedAtMs = now - 10 * 60_000))
        val ble = FakeBle(reachable = true, status = snap(locked = true, updatedAtMs = now, channel = Channel.BLE))
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

    @Test fun appOpenKeepsSnapshotWhenAsleep() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, updatedAtMs = 1))
        val cloud = FakeCloud()
        val result = refresher(FakeBle(), cloud, store).refresh(RefreshTrigger.APP_OPEN)
        // Fleet API has no data for a sleeping car: the stored data stays, with its age, marked asleep.
        assertEquals(snap(batteryPct = 50, updatedAtMs = 1).copy(asleep = true), result)
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
        val result = refresher(FakeBle(), cloud, store).refresh(RefreshTrigger.MANUAL)
        assertEquals(snap(batteryPct = 50, updatedAtMs = 1).copy(asleep = true), result)
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
}
