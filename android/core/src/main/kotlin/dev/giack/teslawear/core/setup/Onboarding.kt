package dev.giack.teslawear.core.setup

/** Where a new user is in Bluetooth-mode onboarding; DONE leads to the home screen. */
enum class OnboardingStep { PROFILE, CAR, KEY, DONE }

/** Profile first (the car picture), then the car by VIN, then its BLE key. Fleet installs need no BLE key. */
val SetupState.onboardingStep: OnboardingStep get() = when {
    isConfigured -> OnboardingStep.DONE
    profile == null -> OnboardingStep.PROFILE
    car?.vin == null -> OnboardingStep.CAR
    else -> OnboardingStep.KEY
}

object Vin {
    private val VALID = Regex("[A-HJ-NPR-Z0-9]{17}")

    /** The VIN in what the user typed (case, spaces and dashes ignored), or null if it cannot be one. */
    fun parse(input: String): String? = input.uppercase().filter { it.isLetterOrDigit() }.takeIf(VALID::matches)
}

/** What a Bluetooth scan says about the VIN the user entered. */
sealed interface CarSearch {
    data class Found(val car: Car) : CarSearch
    /** Teslas are near but none advertises this VIN's name: most likely a typo. */
    data class OtherTeslas(val count: Int) : CarSearch
    data object NoTesla : CarSearch

    companion object {
        /** Each car advertises a name derived from its VIN, so a nearby car confirms the VIN. */
        fun match(vin: String, nearbyTeslaNames: Set<String>): CarSearch {
            val car = Car.fromVin(vin)
            return when {
                car.localName in nearbyTeslaNames -> Found(car)
                nearbyTeslaNames.isNotEmpty() -> OtherTeslas(nearbyTeslaNames.size)
                else -> NoTesla
            }
        }
    }
}
