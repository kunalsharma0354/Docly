package com.nexora.docly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Monochrome branding — forced black & white on every device & Android version.
private val DoclyColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = CardBlack,
    onPrimaryContainer = PureWhite,
    secondary = CloudWhite,
    onSecondary = PureBlack,
    tertiary = SilverGrey,
    onTertiary = PureBlack,
    background = InkBlack,
    onBackground = CloudWhite,
    surface = InkBlack,
    onSurface = CloudWhite,
    surfaceVariant = CardBlack,
    onSurfaceVariant = SilverGrey,
    outline = BorderWhite,
    error = PureWhite,
    onError = PureBlack,
    surfaceContainer = CardBlack,
    surfaceContainerHigh = CardBlack,
    surfaceContainerHighest = SmokeGrey,
    surfaceContainerLow = InkBlack,
    surfaceContainerLowest = PureBlack
)

@Composable
fun DoclyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DoclyColorScheme,
        typography = Typography,
        content = content
    )
}