/*
 * Theme.kt
 * VipersTheme wraps every screen so they all use the mockup colours, text styles and shapes.
 * Known issues: no dark theme yet. Dynamic colour is turned off on purpose so Android 12+
 * phones don't replace our green with wallpaper colours.
 */
package com.example.vipers.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = GreenFill,
    onPrimaryContainer = PrimaryDark,
    secondary = TextSecondary,
    onSecondary = Color.White,
    secondaryContainer = GreenFillStrong,
    onSecondaryContainer = PrimaryDark,
    background = Background,
    onBackground = TextMain,
    surface = Surface,
    onSurface = TextMain,
    onSurfaceVariant = TextMuted,
    outline = Border,
    outlineVariant = Divider,
    error = Error,
    onError = Color.White,
)

/** Applies the app's colours, typography  */
@Composable
fun VipersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
