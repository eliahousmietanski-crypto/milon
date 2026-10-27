package com.milon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.milon.app.R

val HebrewFamily = FontFamily(
    Font(R.font.hebrew_serif_regular, FontWeight.Normal),
    Font(R.font.hebrew_serif_bold, FontWeight.Bold),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2B3A67),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2F6),
    onPrimaryContainer = Color(0xFF111B3D),
    secondary = Color(0xFF8C6A3A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E5CF),
    onSecondaryContainer = Color(0xFF3B2A10),
    tertiary = Color(0xFF3F6B5C),
    tertiaryContainer = Color(0xFFD5EADF),
    onTertiaryContainer = Color(0xFF0D2A20),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF4E4A42),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F3EC),
    surfaceContainer = Color(0xFFF2EDE4),
    surfaceContainerHigh = Color(0xFFECE6DB),
    surfaceContainerHighest = Color(0xFFE6E0D4),
    outline = Color(0xFF7F786C),
    outlineVariant = Color(0xFFD5CEC1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB8C5F5),
    onPrimary = Color(0xFF1A2650),
    primaryContainer = Color(0xFF34436F),
    onPrimaryContainer = Color(0xFFDDE2F6),
    secondary = Color(0xFFE2C38F),
    onSecondary = Color(0xFF3F2C0C),
    secondaryContainer = Color(0xFF574321),
    onSecondaryContainer = Color(0xFFF7E4C3),
    tertiary = Color(0xFFA6D0BF),
    tertiaryContainer = Color(0xFF26493D),
    onTertiaryContainer = Color(0xFFD5EADF),
    background = Color(0xFF14161B),
    onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF14161B),
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF2E3038),
    onSurfaceVariant = Color(0xFFC6C2B9),
    surfaceContainerLowest = Color(0xFF0F1115),
    surfaceContainerLow = Color(0xFF1A1C22),
    surfaceContainer = Color(0xFF1E2027),
    surfaceContainerHigh = Color(0xFF282A31),
    surfaceContainerHighest = Color(0xFF33353C),
    outline = Color(0xFF8F8C85),
    outlineVariant = Color(0xFF45464D),
)

/** Facteur de taille du texte hébreu choisi dans les Réglages. */
val LocalHebrewScale = staticCompositionLocalOf { 1f }

@Composable
fun hebrewStyle(size: Float, bold: Boolean = false): TextStyle = TextStyle(
    fontFamily = HebrewFamily,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = (size * LocalHebrewScale.current).sp,
    lineHeight = (size * LocalHebrewScale.current * 1.5f).sp,
    textDirection = TextDirection.Rtl,
)

@Composable
fun MilonTheme(hebrewScale: Float, content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Dark else Light
    CompositionLocalProvider(LocalHebrewScale provides hebrewScale) {
        MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
    }
}
