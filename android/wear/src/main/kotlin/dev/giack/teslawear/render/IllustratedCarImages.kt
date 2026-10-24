package dev.giack.teslawear.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import dev.giack.teslawear.R
import dev.giack.teslawear.core.car.CarIllustration
import dev.giack.teslawear.core.car.CarIllustrations
import dev.giack.teslawear.core.car.IllustrationView
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.setup.CarProfile
import java.io.ByteArrayOutputStream

/** The two drawables of a Car illustration (see ILLUSTRATIONS.md). */
data class IllustrationLayers(@DrawableRes val paint: Int, @DrawableRes val details: Int) {
    companion object {
        fun of(illustration: CarIllustration): IllustrationLayers = when (illustration.model) {
            CarModel.MODEL_Y -> when (illustration.view) {
                IllustrationView.THREE_QUARTER -> IllustrationLayers(R.drawable.car_y_3q_paint, R.drawable.car_y_3q_details)
                IllustrationView.SIDE -> IllustrationLayers(R.drawable.car_y_side_paint, R.drawable.car_y_side_details)
            }
            CarModel.MODEL_3 -> when (illustration.view) {
                IllustrationView.THREE_QUARTER -> IllustrationLayers(R.drawable.car_3_3q_paint, R.drawable.car_3_3q_details)
                IllustrationView.SIDE -> IllustrationLayers(R.drawable.car_3_side_paint, R.drawable.car_3_side_details)
            }
        }
    }
}

/**
 * Car illustration drawn from the car profile: the paint layer multiplied by the paint, details on top.
 * The home screen shows the three-quarter view, the Tile's wide and short slot the side view.
 */
class IllustratedCarImages(private val context: Context, private val profile: () -> CarProfile?) : CarImages {
    override val hasFrames = true
    override val version: String get() = current(IllustrationView.THREE_QUARTER).let { "illustration-${it.model}-${it.paintArgb}" }

    override suspend fun prepare(renderOptions: String?) = true

    override fun frames(): List<ImageBitmap> = listOf(render(IllustrationView.THREE_QUARTER, FRAME_WIDTH_PX).asImageBitmap())

    override fun tileFrame(): ByteArray =
        ByteArrayOutputStream().also { render(IllustrationView.SIDE, TILE_WIDTH_PX).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun current(view: IllustrationView): CarIllustration = CarIllustrations.of(profile(), view)

    private fun render(view: IllustrationView, widthPx: Int): Bitmap {
        val illustration = current(view)
        val layers = IllustrationLayers.of(illustration)
        val paint = ContextCompat.getDrawable(context, layers.paint)!!.mutate()
        val details = ContextCompat.getDrawable(context, layers.details)!!
        val bitmap = Bitmap.createBitmap(widthPx, widthPx / 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        paint.colorFilter = PorterDuffColorFilter(illustration.paintArgb.toInt(), PorterDuff.Mode.MULTIPLY)
        paint.setBounds(0, 0, bitmap.width, bitmap.height)
        paint.draw(canvas)
        details.setBounds(0, 0, bitmap.width, bitmap.height)
        details.draw(canvas)
        return bitmap
    }

    private companion object {
        const val FRAME_WIDTH_PX = 400
        const val TILE_WIDTH_PX = 200
    }
}

/** Uses [primary] when it has pictures, otherwise [fallback]. */
class FallbackCarImages(private val primary: CarImages, private val fallback: CarImages) : CarImages {
    private fun active() = if (primary.hasFrames) primary else fallback
    override val hasFrames: Boolean get() = active().hasFrames
    override val version: String get() = active().version
    override suspend fun prepare(renderOptions: String?) = primary.prepare(renderOptions) || fallback.prepare(renderOptions)
    override fun frames() = active().frames()
    override fun tileFrame() = active().tileFrame()
}
