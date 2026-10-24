package dev.giack.teslawear.core.setup

import dev.giack.teslawear.protocol.BleFraming

enum class BleKeyStatus { NONE, PENDING, PAIRED }

data class SetupState(
    val car: Car? = null,
    val profile: CarProfile? = null,
    val bleKey: BleKeyStatus = BleKeyStatus.NONE,
    val fleetEnabled: Boolean = false,
) {
    /** Usable: Fleet mode reaches the car by VIN, otherwise a paired BLE key is needed. */
    val isConfigured: Boolean get() = car != null && (bleKey == BleKeyStatus.PAIRED || (fleetEnabled && car.vin != null))
}

/** The car the watch drives: found by its advertised local name. Commands also need the VIN (it is part of every signature). */
data class Car(val localName: String, val vin: String? = null) {
    companion object {
        fun fromVin(vin: String) = Car(BleFraming.vehicleLocalName(vin), vin)
    }
}

enum class CarModel { MODEL_3, MODEL_Y }

/** Where the car profile came from: picked by the user, or read through Fleet mode. */
enum class ProfileSource { USER, FLEET }

/** What the car looks like; drives the Car illustration. */
data class CarProfile(val model: CarModel, val paint: String, val wheels: String, val source: ProfileSource)

interface SetupStore {
    fun load(): SetupState?
    fun save(state: SetupState)
}

/** Single source of truth for which car the watch drives and how it reaches it. */
class Setup(private val store: SetupStore) {
    val state: SetupState get() = store.load() ?: SetupState()

    /** A BLE key belongs to one car: another car starts unpaired. */
    fun chooseCar(car: Car) = update {
        if (it.car?.localName == car.localName) it.copy(car = car) else it.copy(car = car, bleKey = BleKeyStatus.NONE)
    }

    fun pickProfile(model: CarModel, paint: String, wheels: String) =
        update { it.copy(profile = CarProfile(model, paint, wheels, ProfileSource.USER)) }

    fun applyFleetProfile(model: CarModel, paint: String, wheels: String) =
        update { it.copy(profile = CarProfile(model, paint, wheels, ProfileSource.FLEET)) }

    fun enableFleet(vin: String) = update {
        val car = Car.fromVin(vin)
        if (it.car?.localName == car.localName) it.copy(car = car, fleetEnabled = true)
        else it.copy(car = car, bleKey = BleKeyStatus.NONE, fleetEnabled = true)
    }

    fun disableFleet() = update { it.copy(fleetEnabled = false) }

    fun bleKeyRequested() = update { it.copy(bleKey = BleKeyStatus.PENDING) }

    fun bleKeyPaired() = update { it.copy(bleKey = BleKeyStatus.PAIRED) }

    fun reset() = store.save(SetupState())

    /** Forgets the car, its profile and key status: the next launch starts onboarding again. */
    fun removeCar() = reset()

    /**
     * Migrates an install provisioned by `setup.sh push-watch` (Fleet secrets, optional BLE key that the
     * script imports only after a successful Bluetooth command). Never touches an existing Setup state.
     */
    fun adoptImportedSecrets(vin: String, hasBleKey: Boolean) {
        if (store.load() != null) return
        store.save(
            SetupState(
                car = Car.fromVin(vin),
                bleKey = if (hasBleKey) BleKeyStatus.PAIRED else BleKeyStatus.NONE,
                fleetEnabled = true,
            ),
        )
    }

    private fun update(change: (SetupState) -> SetupState) = store.save(change(state))
}
