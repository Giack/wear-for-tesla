package dev.giack.teslawear.render

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Pictures of the car. The Public build draws Car illustrations from the car profile; the Personal build
 * plugs in Official renders (ADR-0002). Each build flavor provides `carImages(context, http, profile)`.
 */
interface CarImages {
    val hasFrames: Boolean

    /** Changes whenever the pictures change (the Tile reloads its image on a new version). */
    val version: String

    /** Fetches pictures if the source needs to; true when frames are available afterwards. */
    suspend fun prepare(renderOptions: String?): Boolean

    /** Frames for the home screen, or null to use the built-in drawing. */
    fun frames(): List<ImageBitmap>?

    /** PNG for the Tile, or null to use the built-in drawing. */
    fun tileFrame(): ByteArray?
}
