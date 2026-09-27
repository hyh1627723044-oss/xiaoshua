package io.github.hyh1627723044.shortvideokws.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Forest green on a pale mint background, following the design mockup.
object Palette {
    val Primary = Color(0xFF1E4D3B)
    val Background = Color(0xFFF4F7F4)
    val Surface = Color.White
    val HeroTint = Color(0xFFE6F0E8)
    val Border = Color(0xFFE2E8E3)
    val Track = Color(0xFFEDF1EE)
    val Text = Color(0xFF16211C)
    val Muted = Color(0xFF6E7A73)
    val Success = Color(0xFF2E7D4F)
    val SuccessBg = Color(0xFFDDEFE2)
    val Warning = Color(0xFF9A6A14)
    val WarningBg = Color(0xFFFBF1DA)
    val Danger = Color(0xFFB3261E)
    val DangerBg = Color(0xFFFBE4E1)
    val Info = Color(0xFF2F5F8A)
    val InfoBg = Color(0xFFE2ECF5)
}

@Composable
fun XiaoshuaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Primary,
            onPrimary = Color.White,
            background = Palette.Background,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Track,
            onSurfaceVariant = Palette.Muted,
            outline = Palette.Border,
            secondaryContainer = Palette.HeroTint,
            onSecondaryContainer = Palette.Primary,
            error = Palette.Danger,
        ),
        content = content,
    )
}
