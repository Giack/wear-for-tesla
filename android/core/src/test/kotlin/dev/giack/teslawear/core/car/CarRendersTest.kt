package dev.giack.teslawear.core.car

import dev.giack.teslawear.core.setup.CarModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarRendersTest {
    @Test fun modelYHasSixteenFramesInTwoLayers() {
        val set = CarRenders.of(CarModel.MODEL_Y)!!
        assertEquals(16, set.frames)
        assertEquals("cars/model_y/paint_00.webp", set.paint(0))
        assertEquals("cars/model_y/details_15.webp", set.details(15))
    }

    @Test fun sideFrameIsTheQuarterTurnFromTheThreeQuarterFront() {
        // Frame 0 looks at the front three-quarter (45°); 16 frames step 22.5°, so 90° (side) is frame 2.
        assertEquals(2, CarRenders.of(CarModel.MODEL_Y)!!.sideFrame)
    }

    @Test fun modelsWithoutAModelFallBackToTheIllustration() {
        assertNull(CarRenders.of(CarModel.MODEL_3))
    }
}
