package dev.giack.teslawear.tile

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.giack.teslawear.R
import dev.giack.teslawear.core.RefreshTrigger
import dev.giack.teslawear.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick-command Tile: renders the stored snapshot, then refreshes over BLE (free) in the background. */
class CarTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val graph = applicationContext.graph
            val snapshot = graph.snapshots.load()
            val model = buildTileModel(snapshot, System.currentTimeMillis(), graph.meter.level(), graph.setup.state.profile?.model)
            scope.launch {
                val before = graph.snapshots.load()
                val after = runCatching { graph.provisioned()?.refresher?.refresh(RefreshTrigger.TILE_VIEW) }.getOrNull()
                if (after != null && after != before) requestRefresh(applicationContext)
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(resourcesVersion())
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(carTileLayout(this@CarTileService, model, snapshot?.locked)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val render = applicationContext.graph.carImages.tileFrame()
            ResourceBuilders.Resources.Builder()
                .setVersion(resourcesVersion())
                .addIdToImageMapping(TileIds.CAR, render?.let(::inlineImage) ?: image(R.drawable.car_silhouette))
                .addIdToImageMapping("ic_lock", image(R.drawable.ic_lock))
                .addIdToImageMapping("ic_unlock", image(R.drawable.ic_unlock))
                .addIdToImageMapping("ic_fan", image(R.drawable.ic_fan))
                .addIdToImageMapping("ic_frunk", image(R.drawable.ic_frunk))
                .addIdToImageMapping("ic_plug", image(R.drawable.ic_plug))
                .build()
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun resourcesVersion() =
        if (applicationContext.graph.carImages.hasFrames) applicationContext.graph.carImages.version else RESOURCES_VERSION

    private fun inlineImage(png: ByteArray) = ResourceBuilders.ImageResource.Builder()
        .setInlineResource(
            ResourceBuilders.InlineImageResource.Builder()
                .setData(png).setWidthPx(200).setHeightPx(png.pngHeight())
                .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                .build(),
        )
        .build()

    private fun ByteArray.pngHeight(): Int =
        ((this[20].toInt() and 0xFF) shl 24) or ((this[21].toInt() and 0xFF) shl 16) or ((this[22].toInt() and 0xFF) shl 8) or (this[23].toInt() and 0xFF)

    private fun image(res: Int) = ResourceBuilders.ImageResource.Builder()
        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(res).build())
        .build()

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MS = 15 * 60_000L

        fun requestRefresh(context: Context) = getUpdater(context).requestUpdate(CarTileService::class.java)
    }
}
