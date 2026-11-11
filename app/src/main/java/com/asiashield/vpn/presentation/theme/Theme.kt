package com.asiashield.vpn.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy900 = Color(0xFF0B1220)
val Navy800 = Color(0xFF121B2E)
val Navy700 = Color(0xFF1B2740)
val Navy600 = Color(0xFF2A3958)
val Teal = Color(0xFF22D3A6)
val TealDark = Color(0xFF0E8F70)
val Amber = Color(0xFFFBBF24)
val Red = Color(0xFFF87171)
val TextPrimary = Color(0xFFE6ECF7)
val TextSecondary = Color(0xFF93A1BC)

val PingGood = Color(0xFF34D399)
val PingFair = Color(0xFFFACC15)
val PingPoor = Color(0xFFF87171)
val PingUnknown = Color(0xFF64748B)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Navy900,
    primaryContainer = TealDark,
    onPrimaryContainer = Color.White,
    secondary = Amber,
    onSecondary = Navy900,
    secondaryContainer = Navy600,
    onSecondaryContainer = TextPrimary,
    background = Navy900,
    onBackground = TextPrimary,
    surface = Navy800,
    onSurface = TextPrimary,
    surfaceVariant = Navy700,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Navy800,
    surfaceContainerHigh = Navy700,
    surfaceContainerHighest = Navy700,
    outline = Navy600,
    outlineVariant = Navy700,
    error = Red,
    onError = Navy900,
)

/** AsiaShield is dark-only by design. */
@Composable
fun AsiaShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
