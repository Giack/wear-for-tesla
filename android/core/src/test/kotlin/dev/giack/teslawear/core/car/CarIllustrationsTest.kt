package dev.giack.teslawear.core.car

import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.setup.CarProfile
import dev.giack.teslawear.core.setup.ProfileSource
import kotlin.test.Test
import kotlin.test.assertEquals

class CarIllustrationsTest {
    @Test fun redModelYIsDrawnInRed() {
        val profile = CarProfile(CarModel.MODEL_Y, paint = "RED", wheels = "STANDARD", source = ProfileSource.USER)
        assertEquals(
            CarIllustration(CarModel.MODEL_Y, IllustrationView.THREE_QUARTER, paintArgb = 0xFFB3141C),
            CarIllustrations.of(profile),
        )
    }

    @Test fun unknownPaintFallsBackToNeutralGrey() {
        val profile = CarProfile(CarModel.MODEL_Y, paint = "Midnight Cherry", wheels = "STANDARD", source = ProfileSource.FLEET)
        assertEquals(0xFF8A8F98, CarIllustrations.of(profile).paintArgb)
    }

    @Test fun noProfileShowsANeutralModelY() {
        assertEquals(
            CarIllustration(CarModel.MODEL_Y, IllustrationView.THREE_QUARTER, paintArgb = 0xFF8A8F98),
            CarIllustrations.of(null),
        )
    }

    @Test fun everyModelHasBothViews() {
        val white3 = CarProfile(CarModel.MODEL_3, paint = "WHITE", wheels = "STANDARD", source = ProfileSource.USER)
        assertEquals(
            CarIllustration(CarModel.MODEL_3, IllustrationView.SIDE, paintArgb = 0xFFE6E7E9),
            CarIllustrations.of(white3, IllustrationView.SIDE),
        )
        assertEquals(CarModel.MODEL_3, CarIllustrations.of(white3).model)
        assertEquals(IllustrationView.THREE_QUARTER, CarIllustrations.of(white3).view)
    }
}
