package dev.giack.teslawear.protocol

import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.vcsec.Vcsec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleCommandTest {
    private fun action(cmd: VehicleCommand) = CarServer.Action.parseFrom(cmd.payload()).vehicleAction
    private fun vcsec(cmd: VehicleCommand) = Vcsec.UnsignedMessage.parseFrom(cmd.payload())

    @Test fun climateOnPayloadMatchesDocExample() = assertEquals("120452020801", VehicleCommand.ClimateOn.payload().hex())

    @Test fun lockIsVcsecRke() {
        assertEquals(Domain.VCSEC, VehicleCommand.Lock.domain)
        assertEquals(Vcsec.RKEAction_E.RKE_ACTION_LOCK, vcsec(VehicleCommand.Lock).rkeAction)
    }

    @Test fun unlockEncodesZeroEnumInsideOneof() {
        val msg = vcsec(VehicleCommand.Unlock)
        assertEquals(Vcsec.UnsignedMessage.SubMessageCase.RKEACTION, msg.subMessageCase)
        assertEquals(Vcsec.RKEAction_E.RKE_ACTION_UNLOCK, msg.rkeAction)
    }

    @Test fun frunkIsClosureMove() =
        assertEquals(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_MOVE, vcsec(VehicleCommand.OpenFrunk).closureMoveRequest.frontTrunk)

    @Test fun trunkOpenAndClose() {
        assertEquals(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_MOVE, vcsec(VehicleCommand.OpenTrunk).closureMoveRequest.rearTrunk)
        assertEquals(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_CLOSE, vcsec(VehicleCommand.CloseTrunk).closureMoveRequest.rearTrunk)
    }

    @Test fun setTempCarriesBothSeats() {
        val adj = action(VehicleCommand.SetTemp(21.5f)).hvacTemperatureAdjustmentAction
        assertEquals(21.5f, adj.driverTempCelsius)
        assertEquals(21.5f, adj.passengerTempCelsius)
    }

    @Test fun infotainmentCommandsUseInfotainmentDomain() {
        listOf(VehicleCommand.ClimateOff, VehicleCommand.ChargePortOpen, VehicleCommand.ChargePortClose, VehicleCommand.VentWindows,
            VehicleCommand.CloseWindows, VehicleCommand.FlashLights, VehicleCommand.Honk, VehicleCommand.SetTemp(20f))
            .forEach { assertEquals(Domain.INFOTAINMENT, it.domain, it.toString()) }
        assertTrue(action(VehicleCommand.VentWindows).vehicleControlWindowAction.hasVent())
        assertTrue(action(VehicleCommand.CloseWindows).vehicleControlWindowAction.hasClose())
        assertTrue(action(VehicleCommand.ChargePortOpen).hasChargePortDoorOpen())
        assertTrue(action(VehicleCommand.Honk).hasVehicleControlHonkHornAction())
        assertTrue(action(VehicleCommand.FlashLights).hasVehicleControlFlashLightsAction())
        assertEquals(false, action(VehicleCommand.ClimateOff).hvacAutoAction.powerOn)
    }

    @Test fun readsRequestStatusAndData() {
        assertEquals(Vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_STATUS,
            Vcsec.UnsignedMessage.parseFrom(VehicleReads.vcsecStatusRequest()).informationRequest.informationRequestType)
        val gets = VehicleReads.carServerDataRequests().map { CarServer.Action.parseFrom(it).vehicleAction.getVehicleData }
        assertEquals(listOf(true, false, false), gets.map { it.hasGetChargeState() })
        assertEquals(listOf(false, true, false), gets.map { it.hasGetClimateState() })
        assertEquals(listOf(false, false, true), gets.map { it.hasGetClosuresState() })
    }
}
