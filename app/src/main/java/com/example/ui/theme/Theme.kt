package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val XaviourDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = StarlightWhite,
    primaryContainer = NebulaElevated,
    onPrimaryContainer = ElectricBlueLight,
    secondary = MetallicGold,
    onSecondary = CosmicVoid,
    secondaryContainer = ColorTokens.GoldContainer,
    onSecondaryContainer = ChampagneGold,
    tertiary = CyanSpark,
    onTertiary = CosmicVoid,
    background = CosmicVoid,
    onBackground = StarlightWhite,
    surface = DeepNavy,
    onSurface = StarlightWhite,
    surfaceVariant = NebulaSurface,
    onSurfaceVariant = NebulaTextSecondary,
    outline = NebulaBorder,
    error = CoralError,
    onError = StarlightWhite
)

private object ColorTokens {
    val GoldContainer = androidx.compose.ui.graphics.Color(0xFF2D2410)
}

val XaviourShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun XaviourStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = XaviourDarkColorScheme,
        typography = XaviourTypography,
        shapes = XaviourShapes,
        content = content
    )
}
