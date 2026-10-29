package dev.giack.teslawear.ble

import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.vcsec.Vcsec
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.Snapshot
import kotlin.math.roundToInt

/** Maps BLE status replies to snapshots (fields not reported stay null). */
object BleStatusMapper {
    private const val KM_PER_MILE = 1.609344

    fun fromVcsec(status: Vcsec.VehicleStatus, nowMs: Long): Snapshot {
        val closures = status.closureStatuses
        fun open(state: Vcsec.ClosureState_E) = when (state) {
            Vcsec.ClosureState_E.CLOSURESTATE_CLOSED -> false
            Vcsec.ClosureState_E.CLOSURESTATE_UNKNOWN -> null
            else -> true
        }
        return Snapshot(
            locked = when (status.vehicleLockState) {
                Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED, Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_INTERNAL_LOCKED -> true
                Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_UNLOCKED, Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_SELECTIVE_UNLOCKED -> false
                else -> null
            },
            trunkOpen = if (status.hasClosureStatuses()) open(closures.rearTrunk) else null,
            frunkOpen = if (status.hasClosureStatuses()) open(closures.frontTrunk) else null,
            chargePortOpen = if (status.hasClosureStatuses()) open(closures.chargePort) else null,
            asleep = isAsleep(status),
            updatedAtMs = nowMs,
            channel = Channel.BLE,
        )
    }

    fun isAsleep(status: Vcsec.VehicleStatus): Boolean =
        status.vehicleSleepStatus == Vcsec.VehicleSleepStatus_E.VEHICLE_SLEEP_STATUS_ASLEEP

    fun fromCarServer(response: CarServer.Response, nowMs: Long): Snapshot? {
        if (!response.hasVehicleData()) return null
        val data = response.vehicleData
        val charge = data.chargeState
        val climate = data.climateState
        val closures = data.closuresState
        val windows = listOf(
            closures.hasWindowOpenDriverFront() to closures.windowOpenDriverFront,
            closures.hasWindowOpenPassengerFront() to closures.windowOpenPassengerFront,
            closures.hasWindowOpenDriverRear() to closures.windowOpenDriverRear,
            closures.hasWindowOpenPassengerRear() to closures.windowOpenPassengerRear,
        ).filter { it.first }.map { it.second }
        return Snapshot(
            batteryPct = if (charge.hasBatteryLevel()) charge.batteryLevel else null,
            rangeKm = if (charge.hasBatteryRange()) (charge.batteryRange * KM_PER_MILE).roundToInt() else null,
            locked = if (closures.hasLocked()) closures.locked else null,
            climateOn = if (climate.hasIsClimateOn()) climate.isClimateOn else null,
            insideTempC = if (climate.hasInsideTempCelsius()) climate.insideTempCelsius else null,
            setTempC = if (climate.hasDriverTempSetting()) climate.driverTempSetting else null,
            chargePortOpen = if (charge.hasChargePortDoorOpen()) charge.chargePortDoorOpen else null,
            charging = if (charge.hasChargingState()) charge.chargingState.hasCharging() else null,
            windowsOpen = if (windows.isEmpty()) null else windows.any { it },
            trunkOpen = if (closures.hasDoorOpenTrunkRear()) closures.doorOpenTrunkRear else null,
            frunkOpen = if (closures.hasDoorOpenTrunkFront()) closures.doorOpenTrunkFront else null,
            updatedAtMs = nowMs,
            channel = Channel.BLE,
        )
    }
}
