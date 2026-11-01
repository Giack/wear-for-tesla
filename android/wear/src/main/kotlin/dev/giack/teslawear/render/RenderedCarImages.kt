package dev.giack.teslawear.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.giack.teslawear.core.car.CarIllustrations
import dev.giack.teslawear.core.car.CarRenders
import dev.giack.teslawear.core.car.RenderSet
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.setup.CarProfile
import java.io.ByteArrayOutputStream

/**
 * Car renders bundled as assets (see [CarRenders]), composed like a Car illustration: the paint layer multiplied
 * by the profile's paint, the details layer on top. All frames rotate on the home screen; the Tile gets the side.
 * Models without renders report no frames, so a [FallbackCarImages] can draw the illustration instead.
 */
class RenderedCarImages(private val context: Context, private val profile: () -> CarProfile?) : CarImages {
    private fun set(): RenderSet? = CarRenders.of(profile()?.model ?: CarModel.MODEL_Y)
    private fun paintArgb(): Long = CarIllustrations.of(profile()).paintArgb

    override val hasFrames: Boolean get() = set() != null
    override val version: String get() = "render-${set()?.dir}-${paintArgb()}"

    override suspend fun prepare(renderOptions: String?) = hasFrames

    override fun frames(): List<ImageBitmap>? = set()?.let { s -> (0 until s.frames).map { compose(s, it).asImageBitmap() } }

    override fun tileFrame(): ByteArray? = set()?.let { s ->
        val side = compose(s, s.sideFrame)
        val tile = Bitmap.createScaledBitmap(side, TILE_WIDTH_PX, side.height * TILE_WIDTH_PX / side.width, true)
        ByteArrayOutputStream().also { tile.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun compose(s: RenderSet, frame: Int): Bitmap {
        val paintLayer = decode(s.paint(frame))
        val details = decode(s.details(frame))
        val out = Bitmap.createBitmap(paintLayer.width, paintLayer.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawBitmap(paintLayer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(paintArgb().toInt(), PorterDuff.Mode.MULTIPLY)
            })
            drawBitmap(details, 0f, 0f, null)
        }
        paintLayer.recycle()
        details.recycle()
        return out
    }

    private fun decode(path: String): Bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it)!! }

    private companion object {
        const val TILE_WIDTH_PX = 200
    }
}
