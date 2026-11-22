package de.fsauter.herbert.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HerbertBlue = Color(0xFF0288D1)
val HerbertBlueLight = Color(0xFFE1F5FE)
val HerbertToolbarGray = Color(0xFFF3F6F8)
val HerbertSurface = Color(0xFFFAFAFA)
val HerbertTextDark = Color(0xDE000000)
val HerbertTextLight = Color(0x8A000000)
val HerbertTextGray = Color(0xFF999999)
val HerbertConnectedGreen = Color(0xFF27B368)
val HerbertWarningRed = Color(0xFFC62828)
val HerbertDivider = Color(0x1F000000)

private val HerbertLightColorScheme = lightColorScheme(
    primary = HerbertBlue,
    onPrimary = Color.White,
    primaryContainer = HerbertBlueLight,
    onPrimaryContainer = Color(0xFF00344D),
    secondary = HerbertBlue,
    onSecondary = Color.White,
    background = HerbertSurface,
    onBackground = HerbertTextDark,
    surface = Color.White,
    onSurface = HerbertTextDark,
    surfaceVariant = HerbertToolbarGray,
    onSurfaceVariant = HerbertTextLight,
    outlineVariant = HerbertDivider,
    error = HerbertWarningRed,
    onError = Color.White,
)

private val HerbertTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        color = HerbertTextDark,
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = HerbertTextDark,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = HerbertTextDark,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = HerbertTextLight,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = HerbertTextLight,
    ),
)

@Composable
fun HerbertTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HerbertLightColorScheme,
        typography = HerbertTypography,
        content = content,
    )
}
