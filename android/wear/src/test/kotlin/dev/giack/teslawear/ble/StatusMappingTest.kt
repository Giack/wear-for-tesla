package dev.giack.teslawear.ble

import com.tesla.generated.carserver.common.Common
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.carserver.vehicle.Vehicle
import com.tesla.generated.vcsec.Vcsec
import dev.giack.teslawear.core.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusMappingTest {
    private val vcsec = Vcsec.VehicleStatus.newBuilder()
        .setVehicleLockState(Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED)
        .setVehicleSleepStatus(Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP)
        .setClosureStatuses(Vcsec.ClosureStatuses.newBuilder()
            .setRearTrunk(Vcsec.ClosureState_E.CLOSURESTATE_OPEN)
            .setFrontTrunk(Vcsec.ClosureState_E.CLOSURESTATE_OPEN)
            .setChargePort(Vcsec.ClosureState_E.CLOSURESTATE_CLOSED))
        .build()

    @Test fun vcsecStatusMapsLocksAndClosures() {
        val s = BleStatusMapper.fromVcsec(vcsec, nowMs = 9)
        assertEquals(true, s.locked)
        assertEquals(true, s.trunkOpen)
        assertEquals(true, s.frunkOpen)
        assertEquals(false, s.chargePortOpen)
        assertNull(s.batteryPct)
        assertEquals(9, s.updatedAtMs)
        assertEquals(Channel.BLE, s.channel)
    }

    @Test fun unlockedStateMapsToFalse() {
        val unlocked = vcsec.toBuilder().setVehicleLockState(Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_UNLOCKED).build()
        assertEquals(false, BleStatusMapper.fromVcsec(unlocked, 0).locked)
    }

    @Test fun asleepIsDetected() {
        assertEquals(true, BleStatusMapper.isAsleep(vcsec))
        assertEquals(true, BleStatusMapper.fromVcsec(vcsec, 0).asleep)
    }

    @Test fun carServerDataMapsChargeAndClimate() {
        val data = Vehicle.VehicleData.newBuilder()
            .setChargeState(Vehicle.ChargeState.newBuilder()
                .setBatteryLevel(78).setBatteryRange(193.9f).setChargePortDoorOpen(true)
                .setChargingState(Vehicle.ChargeState.ChargingState.newBuilder().setCharging(Common.Void.getDefaultInstance())))
            .setClimateState(Vehicle.ClimateState.newBuilder()
                .setInsideTempCelsius(19.5f).setDriverTempSetting(21.5f).setIsClimateOn(true))
            .setClosuresState(Vehicle.ClosuresState.newBuilder()
                .setWindowOpenPassengerFront(true).setLocked(false).setDoorOpenTrunkFront(true))
            .build()
        val response = CarServer.Response.newBuilder().setVehicleData(data).build()
        val s = BleStatusMapper.fromCarServer(response, nowMs = 3)!!
        assertEquals(78, s.batteryPct)
        assertEquals(312, s.rangeKm)
        assertEquals(true, s.chargePortOpen)
        assertEquals(true, s.charging)
        assertEquals(true, s.climateOn)
        assertEquals(19.5f, s.insideTempC)
        assertEquals(21.5f, s.setTempC)
        assertEquals(true, s.windowsOpen)
        assertEquals(false, s.locked)
        assertEquals(true, s.frunkOpen)
    }

    @Test fun carServerWithoutDataIsNull() = assertNull(BleStatusMapper.fromCarServer(CarServer.Response.getDefaultInstance(), 0))
}
