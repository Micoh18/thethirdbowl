package com.micoh.thethirdbowl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BowlGreenDark,
    onPrimary = Color(0xFF063822),
    primaryContainer = Color(0xFF0F4F37),
    onPrimaryContainer = Color(0xFFE5FFF0),
    secondary = Color(0xFFE1C17A),
    onSecondary = Color(0xFF3D2D00),
    secondaryContainer = Color(0xFF46381A),
    onSecondaryContainer = Color(0xFFFFF2CC),
    tertiary = Color(0xFFFFB49C),
    onTertiary = Color(0xFF56230E),
    tertiaryContainer = Color(0xFF6A3320),
    onTertiaryContainer = Color(0xFFFFE2D6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF151814),
    onBackground = Color(0xFFE6E3DB),
    surface = Color(0xFF1D211C),
    onSurface = Color(0xFFE6E3DB),
    surfaceVariant = Color(0xFF454B42),
    onSurfaceVariant = Color(0xFFC6CBC0),
    outline = Color(0xFF90988C),
    outlineVariant = Color(0xFF454B42),
)

private val LightColorScheme = lightColorScheme(
    primary = BowlGreen,
    onPrimary = Color.White,
    primaryContainer = BowlGreenContainer,
    onPrimaryContainer = Color(0xFF093B27),
    secondary = BowlAmber,
    onSecondary = Color.White,
    secondaryContainer = BowlAmberContainer,
    onSecondaryContainer = Color(0xFF3A2800),
    tertiary = BowlClay,
    onTertiary = Color.White,
    tertiaryContainer = BowlClayContainer,
    onTertiaryContainer = Color(0xFF3B1608),
    error = BowlRed,
    onError = Color.White,
    errorContainer = BowlRedContainer,
    onErrorContainer = Color(0xFF5C120D),
    background = BowlCream,
    onBackground = BowlInk,
    surface = BowlSurface,
    onSurface = BowlInk,
    surfaceVariant = BowlSurfaceWarm,
    onSurfaceVariant = BowlInkSoft,
    outline = BowlOutline,
    outlineVariant = Color(0xFFE7DED1),
)

@Composable
fun TheThirdBowlTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
