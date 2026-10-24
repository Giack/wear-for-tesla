package dev.giack.teslawear.render

import dev.giack.teslawear.R
import dev.giack.teslawear.core.car.CarIllustration
import dev.giack.teslawear.core.car.IllustrationView
import dev.giack.teslawear.core.setup.CarModel
import org.junit.Assert.assertEquals
import org.junit.Test

class IllustrationLayersTest {
    private fun layers(model: CarModel, view: IllustrationView) = IllustrationLayers.of(CarIllustration(model, view, 0xFF000000))

    @Test fun eachModelAndViewHasItsOwnDrawing() {
        assertEquals(IllustrationLayers(R.drawable.car_y_3q_paint, R.drawable.car_y_3q_details), layers(CarModel.MODEL_Y, IllustrationView.THREE_QUARTER))
        assertEquals(IllustrationLayers(R.drawable.car_y_side_paint, R.drawable.car_y_side_details), layers(CarModel.MODEL_Y, IllustrationView.SIDE))
        assertEquals(IllustrationLayers(R.drawable.car_3_3q_paint, R.drawable.car_3_3q_details), layers(CarModel.MODEL_3, IllustrationView.THREE_QUARTER))
        assertEquals(IllustrationLayers(R.drawable.car_3_side_paint, R.drawable.car_3_side_details), layers(CarModel.MODEL_3, IllustrationView.SIDE))
    }

    @Test fun noTwoCombinationsShareADrawing() {
        val all = CarModel.entries.flatMap { m -> IllustrationView.entries.map { v -> layers(m, v) } }
        assertEquals(all.size, all.toSet().size)
    }
}
