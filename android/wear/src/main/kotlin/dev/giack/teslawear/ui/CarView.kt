package dev.giack.teslawear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.giack.teslawear.R

/** Car image: rotating render frames when available (drag horizontally), otherwise the vector silhouette. */
@Composable
fun CarView(frames: List<ImageBitmap>?, modifier: Modifier = Modifier, rotatable: Boolean = true) {
    if (frames.isNullOrEmpty()) {
        Image(painterResource(R.drawable.car_silhouette), contentDescription = stringResource(R.string.car_image_description), modifier = modifier, contentScale = ContentScale.Fit)
        return
    }
    var index by remember { mutableIntStateOf(0) } // frame 0 = three-quarter front
    var accumulated by remember { mutableFloatStateOf(0f) }
    val gesture = if (rotatable) {
        Modifier.pointerInput(frames.size) {
            detectHorizontalDragGestures { _, dx ->
                accumulated += dx
                while (accumulated >= PX_PER_FRAME) { index = (index + 1) % frames.size; accumulated -= PX_PER_FRAME }
                while (accumulated <= -PX_PER_FRAME) { index = (index - 1 + frames.size) % frames.size; accumulated += PX_PER_FRAME }
            }
        }
    } else {
        Modifier
    }
    Image(frames[index], contentDescription = stringResource(R.string.car_image_description), modifier = modifier.then(gesture), contentScale = ContentScale.Fit)
}

private const val PX_PER_FRAME = 20f
