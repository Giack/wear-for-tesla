package dev.giack.teslawear.protocol

import com.tesla.generated.carserver.common.Common
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.vcsec.Vcsec

/**
 * Role of the key that signs Bluetooth commands. The watch enrolls its BLE key as [DRIVER].
 * A Fleet Manager key cannot send commands over Bluetooth at all (it is only used through the Fleet API).
 */
enum class BleKeyRole { DRIVER, OWNER }

/** Commands the app can send; payloads mirror vehicle-command's pkg/vehicle implementations. */
sealed interface VehicleCommand {
    val domain: Domain
    fun payload(): ByteArray

    /**
     * Whether [role] may authorize this command over Bluetooth.
     * A driver key can run the commands a phone key can (locks, climate, sentry, charging, and the rest).
     * Remote door unlatch is refused for that role (`MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES`);
     * an owner key can send it over Bluetooth, and Fleet mode sends it with the cloud key.
     */
    fun authorizedFor(role: BleKeyRole): Boolean = when (this) {
        UnlatchDriverDoor -> role == BleKeyRole.OWNER
        else -> true
    }

    sealed class Rke(private val action: Vcsec.RKEAction_E) : VehicleCommand {
        override val domain get() = Domain.VCSEC
        override fun payload(): ByteArray = Vcsec.UnsignedMessage.newBuilder().setRKEAction(action).build().toByteArray()
    }

    sealed class Closure(private val build: Vcsec.ClosureMoveRequest.Builder.() -> Unit) : VehicleCommand {
        override val domain get() = Domain.VCSEC
        override fun payload(): ByteArray = Vcsec.UnsignedMessage.newBuilder()
            .setClosureMoveRequest(Vcsec.ClosureMoveRequest.newBuilder().apply(build))
            .build().toByteArray()
    }

    sealed class Infotainment : VehicleCommand {
        override val domain get() = Domain.INFOTAINMENT
        protected abstract fun action(builder: CarServer.VehicleAction.Builder)
        override fun payload(): ByteArray = CarServer.Action.newBuilder()
            .setVehicleAction(CarServer.VehicleAction.newBuilder().also(::action))
            .build().toByteArray()
    }

    data object Lock : Rke(Vcsec.RKEAction_E.RKE_ACTION_LOCK)
    data object Unlock : Rke(Vcsec.RKEAction_E.RKE_ACTION_UNLOCK)
    /** BLE-only: wakes infotainment so it can take commands (Fleet API uses wake_up instead). */
    data object WakeVehicle : Rke(Vcsec.RKEAction_E.RKE_ACTION_WAKE_VEHICLE)
    data object OpenFrunk : Closure({ frontTrunk = Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_MOVE })
    data object OpenTrunk : Closure({ rearTrunk = Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_MOVE })
    data object CloseTrunk : Closure({ rearTrunk = Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_CLOSE })

    data object ClimateOn : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setHvacAutoAction(CarServer.HvacAutoAction.newBuilder().setPowerOn(true))
        }
    }

    data object ClimateOff : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setHvacAutoAction(CarServer.HvacAutoAction.newBuilder().setPowerOn(false))
        }
    }

    data class SetTemp(val celsius: Float) : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setHvacTemperatureAdjustmentAction(
                CarServer.HvacTemperatureAdjustmentAction.newBuilder()
                    .setDriverTempCelsius(celsius)
                    .setPassengerTempCelsius(celsius)
                    .setLevel(CarServer.HvacTemperatureAdjustmentAction.Temperature.newBuilder()
                        .setTEMPMAX(Common.Void.getDefaultInstance())),
            )
        }
    }

    data object ChargePortOpen : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setChargePortDoorOpen(CarServer.ChargePortDoorOpen.getDefaultInstance())
        }
    }

    data object ChargePortClose : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setChargePortDoorClose(CarServer.ChargePortDoorClose.getDefaultInstance())
        }
    }

    data object VentWindows : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setVehicleControlWindowAction(
                CarServer.VehicleControlWindowAction.newBuilder().setVent(Common.Void.getDefaultInstance()),
            )
        }
    }

    data object CloseWindows : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setVehicleControlWindowAction(
                CarServer.VehicleControlWindowAction.newBuilder().setClose(Common.Void.getDefaultInstance()),
            )
        }
    }

    data object FlashLights : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setVehicleControlFlashLightsAction(CarServer.VehicleControlFlashLightsAction.getDefaultInstance())
        }
    }

    data object Honk : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setVehicleControlHonkHornAction(CarServer.VehicleControlHonkHornAction.getDefaultInstance())
        }
    }

    data object SentryOn : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setVehicleControlSetSentryModeAction(
                CarServer.VehicleControlSetSentryModeAction.newBuilder().setOn(true),
            )
        }
    }

    data object SentryOff : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setVehicleControlSetSentryModeAction(
                CarServer.VehicleControlSetSentryModeAction.newBuilder().setOn(false),
            )
        }
    }

    data object ChargeStart : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setChargingStartStopAction(
                CarServer.ChargingStartStopAction.newBuilder().setStart(Common.Void.getDefaultInstance()),
            )
        }
    }

    data object ChargeStop : Infotainment() {
        override fun action(builder: CarServer.VehicleAction.Builder) {
            builder.setChargingStartStopAction(
                CarServer.ChargingStartStopAction.newBuilder().setStop(Common.Void.getDefaultInstance()),
            )
        }
    }

    /** VCSEC closure move on the front driver door. A driver-role BLE key cannot authorize it. */
    data object UnlatchDriverDoor : Closure({ frontDriverDoor = Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_OPEN })
}

/** The car refused the command because of the key's role (`MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES`). */
fun CommandResult.deniedForRole(): Boolean =
    this is CommandResult.Failed && "INSUFFICIENT_PRIVILEGES" in reason

/** Read requests used for status refreshes. */
object VehicleReads {
    /** Unsigned VCSEC status request; answered even while infotainment sleeps. */
    fun vcsecStatusRequest(): ByteArray = Vcsec.UnsignedMessage.newBuilder()
        .setInformationRequest(Vcsec.InformationRequest.newBuilder()
            .setInformationRequestType(Vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_STATUS))
        .build().toByteArray()

    /**
     * One infotainment read per category: over BLE the car refuses a combined reply as too long
     * (MESSAGEFAULT_ERROR_RESPONSE_MTU_EXCEEDED), as the official tool's one-category reads suggest.
     */
    fun carServerDataRequests(): List<ByteArray> = listOf<CarServer.GetVehicleData.Builder.() -> Unit>(
        { setGetChargeState(CarServer.GetChargeState.getDefaultInstance()) },
        { setGetClimateState(CarServer.GetClimateState.getDefaultInstance()) },
        { setGetClosuresState(CarServer.GetClosuresState.getDefaultInstance()) },
    ).map { category ->
        CarServer.Action.newBuilder()
            .setVehicleAction(CarServer.VehicleAction.newBuilder().setGetVehicleData(CarServer.GetVehicleData.newBuilder().apply(category)))
            .build().toByteArray()
    }
}
