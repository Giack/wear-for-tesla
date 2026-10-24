package dev.giack.teslawear.protocol

import com.tesla.generated.carserver.common.Common
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.vcsec.Vcsec

/** Commands the app can send; payloads mirror vehicle-command's pkg/vehicle implementations. */
sealed interface VehicleCommand {
    val domain: Domain
    fun payload(): ByteArray

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
}

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
