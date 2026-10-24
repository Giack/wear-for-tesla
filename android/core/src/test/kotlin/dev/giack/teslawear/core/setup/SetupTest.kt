package dev.giack.teslawear.core.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetupTest {
    private val store = MemorySetupStore()

    @Test fun freshInstallIsUnconfigured() {
        val state = Setup(store).state
        assertNull(state.car)
        assertNull(state.profile)
        assertEquals(BleKeyStatus.NONE, state.bleKey)
        assertEquals(false, state.fleetEnabled)
        assertEquals(false, state.isConfigured)
    }

    @Test fun bluetoothModeIsConfiguredOnlyOnceTheBleKeyIsPaired() {
        val setup = Setup(store)
        setup.chooseCar(Car(localName = "S594b5b8d14ca042bC"))
        assertEquals(false, setup.state.isConfigured)
        setup.bleKeyRequested()
        assertEquals(BleKeyStatus.PENDING, setup.state.bleKey)
        assertEquals(false, setup.state.isConfigured)
        setup.bleKeyPaired()

        val afterRestart = Setup(store).state
        assertEquals("S594b5b8d14ca042bC", afterRestart.car?.localName)
        assertEquals(BleKeyStatus.PAIRED, afterRestart.bleKey)
        assertEquals(true, afterRestart.isConfigured)
    }

    @Test fun choosingAnotherCarForgetsTheBleKey() {
        val setup = Setup(store)
        setup.chooseCar(Car(localName = "S594b5b8d14ca042bC"))
        setup.bleKeyRequested()
        setup.bleKeyPaired()
        setup.chooseCar(Car(localName = "S0123456789abcdefC"))
        assertEquals(BleKeyStatus.NONE, setup.state.bleKey)
        assertEquals(false, setup.state.isConfigured)
    }

    @Test fun fleetProfileReplacesThePickedOne() {
        val setup = Setup(store)
        setup.pickProfile(CarModel.MODEL_Y, paint = "Pearl White", wheels = "Gemini")
        assertEquals(CarProfile(CarModel.MODEL_Y, "Pearl White", "Gemini", ProfileSource.USER), setup.state.profile)

        setup.applyFleetProfile(CarModel.MODEL_Y, paint = "Ultra Red", wheels = "Helix 2")
        assertEquals(CarProfile(CarModel.MODEL_Y, "Ultra Red", "Helix 2", ProfileSource.FLEET), setup.state.profile)
    }

    @Test fun fleetModeAloneConfiguresTheCarFromItsVin() {
        val setup = Setup(store)
        setup.enableFleet(vin = "5YJS0000000000000")
        val state = setup.state
        assertEquals(true, state.fleetEnabled)
        assertEquals("5YJS0000000000000", state.car?.vin)
        assertEquals("S1a87a5a75f3df858C", state.car?.localName)
        assertEquals(true, state.isConfigured)
    }

    @Test fun disablingFleetModeKeepsTheLastKnownProfile() {
        val setup = Setup(store)
        setup.enableFleet(vin = "5YJS0000000000000")
        setup.applyFleetProfile(CarModel.MODEL_3, paint = "Ultra Red", wheels = "Photon")
        setup.disableFleet()
        val state = setup.state
        assertEquals(false, state.fleetEnabled)
        assertEquals("Ultra Red", state.profile?.paint)
        assertEquals(false, state.isConfigured)
    }

    @Test fun resetForgetsEverything() {
        val setup = Setup(store)
        setup.enableFleet(vin = "5YJS0000000000000")
        setup.pickProfile(CarModel.MODEL_Y, paint = "Ultra Red", wheels = "Helix 2")
        setup.bleKeyRequested()
        setup.bleKeyPaired()
        setup.reset()
        assertEquals(SetupState(), Setup(store).state)
    }

    @Test fun existingInstallWithImportedSecretsMigratesToFleetMode() {
        val setup = Setup(store)
        setup.adoptImportedSecrets(vin = "5YJS0000000000000", hasBleKey = false)
        val state = setup.state
        assertEquals(true, state.fleetEnabled)
        assertEquals("5YJS0000000000000", state.car?.vin)
        assertEquals(BleKeyStatus.NONE, state.bleKey)
        assertEquals(true, state.isConfigured)
    }

    @Test fun importedBleKeyCountsAsPaired() {
        val setup = Setup(store)
        setup.adoptImportedSecrets(vin = "5YJS0000000000000", hasBleKey = true)
        assertEquals(BleKeyStatus.PAIRED, setup.state.bleKey)
    }

    @Test fun migrationNeverOverwritesAnExistingSetup() {
        val setup = Setup(store)
        setup.chooseCar(Car(localName = "S0123456789abcdefC"))
        setup.adoptImportedSecrets(vin = "5YJS0000000000000", hasBleKey = true)
        assertEquals("S0123456789abcdefC", setup.state.car?.localName)
        assertEquals(false, setup.state.fleetEnabled)
    }
}

class MemorySetupStore(var saved: SetupState? = null) : SetupStore {
    override fun load(): SetupState? = saved
    override fun save(state: SetupState) { saved = state }
}
