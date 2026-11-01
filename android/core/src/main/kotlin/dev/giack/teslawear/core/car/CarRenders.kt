package dev.giack.teslawear.core.car

import dev.giack.teslawear.core.setup.CarModel

/**
 * Car renders: frames of a project-owned 3D model (design/car-model), bundled as app assets in the two layers
 * of a Car illustration: `paint_NN` (multiplied by the paint colour) and `details_NN` (drawn on top).
 * Frame 0 is the three-quarter front; frames turn round the car in equal steps.
 */
data class RenderSet(val dir: String, val frames: Int, val sideFrame: Int) {
    fun paint(frame: Int) = "%s/paint_%02d.webp".format(dir, frame)
    fun details(frame: Int) = "%s/details_%02d.webp".format(dir, frame)
}

object CarRenders {
    private val sets = mapOf(
        CarModel.MODEL_Y to RenderSet("cars/model_y", frames = 16, sideFrame = 2),
        CarModel.MODEL_3 to RenderSet("cars/model_3", frames = 16, sideFrame = 2),
    )

    /** The render set for [model], or null when the model has only a Car illustration. */
    fun of(model: CarModel): RenderSet? = sets[model]
}
