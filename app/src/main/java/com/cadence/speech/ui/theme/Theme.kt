package com.cadence.speech.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B1220)
val Surface = Color(0xFF131C2E)
val SurfaceVariant = Color(0xFF1C2740)
val Primary = Color(0xFF7C9CFF)
val Accent = Color(0xFFF0B429)
val TextPrimary = Color(0xFFE6ECFF)
val TextSecondary = Color(0xFF9AA8C7)

private val CadenceColors = darkColorScheme(
    primary = Primary,
    onPrimary = Background,
    secondary = Accent,
    onSecondary = Background,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF2A3855)
)

@Composable
fun CadenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CadenceColors,
        typography = Typography(),
        content = content
    )
}
