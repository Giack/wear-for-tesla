package dev.giack.teslawear.core.car

import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.setup.CarProfile

/** Generic paint colours offered in the car profile (not Tesla's trade names). */
enum class Paint(val argb: Long) {
    WHITE(0xFFE6E7E9),
    BLACK(0xFF1D1F23),
    GREY(0xFF5A5E66),
    SILVER(0xFFA8ACB2),
    BLUE(0xFF1E4C9A),
    RED(0xFFB3141C),
    ;

    companion object {
        fun fromName(name: String?): Paint? = entries.firstOrNull { it.name == name }
    }
}

/** Three-quarter front for the home screen; side profile where the space is wide and short (the Tile). */
enum class IllustrationView { THREE_QUARTER, SIDE }

/** Which Car illustration to draw and the colour of its paint layer. */
data class CarIllustration(val model: CarModel, val view: IllustrationView, val paintArgb: Long)

object CarIllustrations {
    /** Paint layer colour when the paint is unknown (no profile yet, or a Fleet colour not mapped). */
    const val NEUTRAL_ARGB = 0xFF8A8F98

    fun of(profile: CarProfile?, view: IllustrationView = IllustrationView.THREE_QUARTER): CarIllustration =
        CarIllustration(profile?.model ?: CarModel.MODEL_Y, view, Paint.fromName(profile?.paint)?.argb ?: NEUTRAL_ARGB)
}
