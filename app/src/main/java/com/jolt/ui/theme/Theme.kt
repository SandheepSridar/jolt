package com.jolt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFFFB020)
val OnAmber = Color(0xFF1C1300)
private val Ink = Color(0xFF0E0F12)
private val Paper = Color(0xFFF6F5F2)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    primaryContainer = Color(0xFF3A2A00),
    onPrimaryContainer = Color(0xFFFFDDA0),
    secondaryContainer = Color(0xFF3A2A00),
    onSecondaryContainer = Color(0xFFFFDDA0),
    background = Ink,
    onBackground = Color(0xFFF2F1EE),
    surface = Ink,
    onSurface = Color(0xFFF2F1EE),
    surfaceContainer = Color(0xFF16181D),
    surfaceContainerHigh = Color(0xFF1D2026),
    surfaceContainerHighest = Color(0xFF252830),
    surfaceVariant = Color(0xFF1D2026),
    onSurfaceVariant = Color(0xFF8E929B),
    outline = Color(0xFF3A3E47),
    outlineVariant = Color(0xFF262930),
    error = Color(0xFFFF7A6B),
    errorContainer = Color(0xFF3B1612),
    onErrorContainer = Color(0xFFFFC9C1),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    primaryContainer = Amber,
    onPrimaryContainer = OnAmber,
    secondaryContainer = Color(0xFFFFE4B0),
    onSecondaryContainer = OnAmber,
    background = Paper,
    onBackground = Color(0xFF14151A),
    surface = Paper,
    onSurface = Color(0xFF14151A),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFEDE8),
    surfaceContainerHighest = Color(0xFFE6E3DD),
    surfaceVariant = Color(0xFFEFEDE8),
    onSurfaceVariant = Color(0xFF6A6D75),
    outline = Color(0xFFC9C6BF),
    outlineVariant = Color(0xFFE2DFD9),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFCE3DF),
    onErrorContainer = Color(0xFF5C1410),
)

/** Tabular figures keep the time from jiggling as digits change. */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

private val JoltTypography = Typography().run {
    copy(
        displayLarge = displayLarge.merge(Tabular).copy(fontSize = 88.sp, lineHeight = 92.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
        displayMedium = displayMedium.merge(Tabular).copy(fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
        displaySmall = displaySmall.merge(Tabular).copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val JoltShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun JoltTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JoltTypography,
        shapes = JoltShapes,
        content = content,
    )
}
