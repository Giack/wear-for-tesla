package dev.giack.teslawear.core.setup

import dev.giack.teslawear.core.car.Paint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnboardingTest {
    private val store = MemorySetupStore()
    private val vin = "LRWYGCEK1PC123456"
    private val name = Car.fromVin(vin).localName

    @Test fun freshInstallStartsWithTheCarProfile() = assertEquals(OnboardingStep.PROFILE, Setup(store).state.onboardingStep)

    @Test fun stepsFollowProfileThenCarThenKey() {
        val setup = Setup(store)
        setup.pickProfile(CarModel.MODEL_Y, Paint.RED.name, "STANDARD")
        assertEquals(OnboardingStep.CAR, setup.state.onboardingStep)
        setup.chooseCar(Car.fromVin(vin))
        assertEquals(OnboardingStep.KEY, setup.state.onboardingStep)
        setup.bleKeyRequested()
        assertEquals(OnboardingStep.KEY, setup.state.onboardingStep)
        setup.bleKeyPaired()
        assertEquals(OnboardingStep.DONE, setup.state.onboardingStep)
    }

    @Test fun fleetInstallsSkipTheBleKey() {
        val setup = Setup(store)
        setup.adoptImportedSecrets(vin, hasBleKey = false)
        assertEquals(OnboardingStep.DONE, setup.state.onboardingStep)
    }

    @Test fun removingTheCarStartsOver() {
        val setup = Setup(store)
        setup.pickProfile(CarModel.MODEL_Y, Paint.RED.name, "STANDARD")
        setup.chooseCar(Car.fromVin(vin))
        setup.bleKeyPaired()
        setup.removeCar()
        assertEquals(SetupState(), setup.state)
        assertEquals(OnboardingStep.PROFILE, setup.state.onboardingStep)
    }

    @Test fun vinIsNormalisedFromWhatTheUserTyped() = assertEquals(vin, Vin.parse(" lrwy gcek1pc12345-6 "))

    @Test fun vinWithWrongLengthIsRejected() = assertNull(Vin.parse("LRWYGCEK1PC12345"))

    @Test fun vinWithLettersNeverUsedInVinsIsRejected() {
        assertNull(Vin.parse("LRWYGCEK1PC12345O")) // O, I and Q are never used: they read as 0 and 1
        assertNull(Vin.parse("LRWYGCEKIPC123456"))
    }

    @Test fun theCarWithThisVinIsFoundAmongNearbyTeslas() =
        assertEquals(CarSearch.Found(Car.fromVin(vin)), CarSearch.match(vin, setOf("S0123456789abcdefC", name)))

    @Test fun teslasNearbyButNoneWithThisVinPointsAtATypo() =
        assertEquals(CarSearch.OtherTeslas(2), CarSearch.match(vin, setOf("S0123456789abcdefC", "Sfedcba9876543210C")))

    @Test fun noTeslaNearbyAsksToGetCloser() = assertEquals(CarSearch.NoTesla, CarSearch.match(vin, emptySet()))
}
