package dev.giack.teslawear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Tesla-like palette from the approved mockup. */
object TeslaColors {
    val Background = Color(0xFF000000)
    val Ink = Color(0xFFF4F4F5)
    val Muted = Color(0xFF8B8B92)
    val Chip = Color(0xFF1D1D21)
    val Accent = Color(0xFF3E6AE1)
    val Green = Color(0xFF3FBF6F)
    val Red = Color(0xFFE3483A)
    val Amber = Color(0xFFE0A33A)
}

@Composable
fun TeslaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = TeslaColors.Accent,
            onPrimary = TeslaColors.Ink,
            background = TeslaColors.Background,
            onBackground = TeslaColors.Ink,
            onSurface = TeslaColors.Ink,
            onSurfaceVariant = TeslaColors.Muted,
            error = TeslaColors.Red,
        ),
        content = content,
    )
}
