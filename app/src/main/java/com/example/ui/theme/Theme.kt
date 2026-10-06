package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ApexDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF001F26),
    primaryContainer = Color(0xFF003B47),
    onPrimaryContainer = Color(0xFFB3F6FF),
    secondary = VoltGreen,
    onSecondary = Color(0xFF002911),
    secondaryContainer = Color(0xFF004D25),
    onSecondaryContainer = Color(0xFFB8FFD8),
    tertiary = AmberWarn,
    onTertiary = Color(0xFF332200),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle,
    error = CoralError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        typography = Typography,
        content = content
    )
}
