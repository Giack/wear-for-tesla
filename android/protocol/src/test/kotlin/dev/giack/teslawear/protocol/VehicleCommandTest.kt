package dev.giack.teslawear.protocol

import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.vcsec.Vcsec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test fun sentryOnAndOff() {
        assertEquals(Domain.INFOTAINMENT, VehicleCommand.SentryOn.domain)
        assertEquals(Domain.INFOTAINMENT, VehicleCommand.SentryOff.domain)
        assertEquals(true, action(VehicleCommand.SentryOn).vehicleControlSetSentryModeAction.on)
        assertEquals(false, action(VehicleCommand.SentryOff).vehicleControlSetSentryModeAction.on)
        assertTrue(action(VehicleCommand.SentryOn).hasVehicleControlSetSentryModeAction())
    }

    @Test fun chargeStartAndStop() {
        assertEquals(Domain.INFOTAINMENT, VehicleCommand.ChargeStart.domain)
        assertEquals(Domain.INFOTAINMENT, VehicleCommand.ChargeStop.domain)
        assertTrue(action(VehicleCommand.ChargeStart).chargingStartStopAction.hasStart())
        assertFalse(action(VehicleCommand.ChargeStart).chargingStartStopAction.hasStop())
        assertTrue(action(VehicleCommand.ChargeStop).chargingStartStopAction.hasStop())
        assertFalse(action(VehicleCommand.ChargeStop).chargingStartStopAction.hasStart())
    }

    @Test fun unlatchDriverDoorOpensTheFrontDriverDoor() {
        assertEquals(Domain.VCSEC, VehicleCommand.UnlatchDriverDoor.domain)
        val move = vcsec(VehicleCommand.UnlatchDriverDoor).closureMoveRequest
        assertEquals(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_OPEN, move.frontDriverDoor)
        assertEquals(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_NONE, move.rearTrunk)
        assertEquals(Vcsec.ClosureMoveType_E.CLOSURE_MOVE_TYPE_NONE, move.frontTrunk)
    }

    @Test fun driverKeyCannotUnlatchOverBluetooth() {
        assertFalse(VehicleCommand.UnlatchDriverDoor.authorizedFor(BleKeyRole.DRIVER))
        assertTrue(VehicleCommand.UnlatchDriverDoor.authorizedFor(BleKeyRole.OWNER))
        listOf(VehicleCommand.SentryOn, VehicleCommand.SentryOff, VehicleCommand.ChargeStart, VehicleCommand.ChargeStop, VehicleCommand.Lock)
            .forEach { assertTrue(it.authorizedFor(BleKeyRole.DRIVER), it.toString()) }
    }

    @Test fun roleRefusalIsThePrivilegesFault() {
        assertTrue(CommandResult.Failed("MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES").deniedForRole())
        assertFalse(CommandResult.Failed("not_charging").deniedForRole())
        assertFalse(CommandResult.Ok.deniedForRole())
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
