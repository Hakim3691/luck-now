package com.lucknow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B1020)
val Gold = Color(0xFFF5C542)
val Cyan = Color(0xFF6EE7F9)
val Mint = Color(0xFF5BE49B)
val Danger = Color(0xFFFF6B6B)
val Amber = Color(0xFFFFB454)
val MutedText = Color(0xFFA7B3CC)

private val LuckNowScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = Cyan,
    onSecondary = Ink,
    tertiary = Mint,
    background = Ink,
    onBackground = Color(0xFFE7ECF7),
    surface = Color(0xFF141B31),
    onSurface = Color(0xFFE7ECF7),
    surfaceVariant = Color(0xFF1D2743),
    onSurfaceVariant = MutedText,
    error = Danger,
    onError = Ink,
    outline = Color(0xFF3A4767),
)

@Composable
fun LuckNowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LuckNowScheme, content = content)
}
