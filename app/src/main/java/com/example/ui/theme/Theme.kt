package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonInk,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainerDark,
    onPrimaryContainer = OnCrimsonContainerDark,
    secondary = ElectricCyan,
    onSecondary = InkObsidian,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = OnCyanContainerDark,
    tertiary = VioletGlow,
    onTertiary = InkObsidian,
    tertiaryContainer = VioletContainerDark,
    onTertiaryContainer = IvoryInk,
    background = InkObsidian,
    onBackground = IvoryInk,
    surface = PanelCharcoal,
    onSurface = IvoryInk,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = MutedSilver,
    outline = BorderSlate,
    outlineVariant = Color(0xFF262A3D)
)

private val LightColorScheme = lightColorScheme(
    primary = CrimsonLight,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainerLight,
    onPrimaryContainer = OnCrimsonContainerLight,
    secondary = TealLight,
    onSecondary = Color.White,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = OnTealContainerLight,
    tertiary = Color(0xFF5D46E8),
    onTertiary = Color.White,
    background = ParchmentLight,
    onBackground = DeepInkText,
    surface = CardPaperLight,
    onSurface = DeepInkText,
    surfaceVariant = SoftTintLight,
    onSurfaceVariant = MutedInkText,
    outline = BorderLight,
    outlineVariant = Color(0xFFEBE8F2)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ManhwaRecapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
