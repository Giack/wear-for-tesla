package dev.giack.teslawear.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import dev.giack.teslawear.confirm.HoldTimer
import dev.giack.teslawear.ui.theme.TeslaColors

/** Round action button that fires only after a continuous 600 ms press; a blue ring shows progress. */
@Composable
fun HoldToConfirmButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    size: Dp = 46.dp,
    enabled: Boolean = true,
    onConfirmed: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val description = androidx.compose.ui.res.stringResource(dev.giack.teslawear.R.string.hold_to_confirm_description, label)
    val timer = remember { HoldTimer(600) }
    var pressed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var fired by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        while (pressed && !fired) {
            progress = withFrameMillis { timer.progress(System.currentTimeMillis()) }
            if (progress >= 1f) {
                fired = true
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onConfirmed()
            }
        }
        if (!pressed) progress = 0f
    }

    Box(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    fired = false
                    timer.press(System.currentTimeMillis())
                    pressed = true
                    tryAwaitRelease()
                    timer.release(System.currentTimeMillis())
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).background(if (active) TeslaColors.Accent else TeslaColors.Chip, CircleShape),
        )
        // Fill rising from the bottom while held, so progress is obvious even under the finger.
        Canvas(Modifier.size(size).clip(CircleShape)) {
            if (progress > 0f) {
                val top = this.size.height * (1f - progress)
                drawRect(
                    color = if (active) TeslaColors.Ink.copy(alpha = 0.35f) else TeslaColors.Accent,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(this.size.width, this.size.height - top),
                )
            }
        }
        Canvas(Modifier.size(size + 8.dp).padding(1.dp)) {
            if (progress > 0f) {
                drawArc(
                    color = TeslaColors.Accent, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Image(
            painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(size * 0.48f),
            colorFilter = if (enabled) null else ColorFilter.tint(TeslaColors.Muted),
        )
    }
}
