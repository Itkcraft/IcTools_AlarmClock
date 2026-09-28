package com.itkcraft.alarmclock.ui.theme

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
import com.itkcraft.alarmclock.data.ThemeMode

val NeonOrange = Color(0xFFFF6A00)
val NeonOrangeLight = Color(0xFFFF8A3D)

private val DarkColors = darkColorScheme(
    primary = NeonOrange,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF3A1D08),
    onPrimaryContainer = Color(0xFFFFD2B3),
    secondary = NeonOrangeLight,
    onSecondary = Color(0xFF1A0A00),
    secondaryContainer = Color(0xFF3A1D08),
    onSecondaryContainer = Color(0xFFFFD2B3),
    tertiary = NeonOrangeLight,
    onTertiary = Color(0xFF1A0A00),
    tertiaryContainer = Color(0xFF3A1D08),
    onTertiaryContainer = Color(0xFFFFD2B3),
    background = Color(0xFF121214),
    onBackground = Color(0xFFF1EFEE),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFF1EFEE),
    surfaceVariant = Color(0xFF26262B),
    onSurfaceVariant = Color(0xFFBDB8B5),
    surfaceContainerLowest = Color(0xFF0D0D0F),
    surfaceContainerLow = Color(0xFF18181B),
    surfaceContainer = Color(0xFF1C1C20),
    surfaceContainerHigh = Color(0xFF232328),
    surfaceContainerHighest = Color(0xFF2B2B31),
    outline = Color(0xFF55525A),
    outlineVariant = Color(0xFF34333A),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFF26300),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D0),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = NeonOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D0),
    onSecondaryContainer = Color(0xFF3A1600),
    tertiary = NeonOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3D0),
    onTertiaryContainer = Color(0xFF3A1600),
    background = Color(0xFFFAF7F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFAF7F5),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEDE7E3),
    onSurfaceVariant = Color(0xFF5A5450),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2EF),
    surfaceContainer = Color(0xFFF2ECE8),
    surfaceContainerHigh = Color(0xFFECE5E1),
    surfaceContainerHighest = Color(0xFFE6DFDA),
    outline = Color(0xFFA8A09B),
    outlineVariant = Color(0xFFDDD5D0),
    error = Color(0xFFD32F2F),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
)

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark(mode)) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
