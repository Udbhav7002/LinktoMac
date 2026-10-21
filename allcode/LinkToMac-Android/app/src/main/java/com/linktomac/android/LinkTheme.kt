package com.linktomac.android

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

// LinkToMac Design Tokens — "Quiet Premium" (mirrors LinkTheme.swift / web CSS).
// Matte surfaces, hairline borders, one blue accent, semantic status colors only.
private val BlueLight  = Color(0xFF0071E3)
private val BlueDark   = Color(0xFF0A84FF)
private val GreenLight = Color(0xFF34C759)
private val GreenDark  = Color(0xFF30D158)
private val AmberLight = Color(0xFFFF9500)
private val AmberDark  = Color(0xFFFF9F0A)
private val RedLight   = Color(0xFFFF3B30)
private val RedDark    = Color(0xFFFF453A)

// Fully-specified schemes — unset M3 roles fall back to stock purple, which is
// exactly how tonal buttons drift away from the brand. Nothing left to defaults.
private val LightScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0x1A0071E3),
    onPrimaryContainer = BlueLight,
    secondary = Color(0xFF6E6E73),
    onSecondary = Color.White,
    secondaryContainer = Color(0x1A0071E3),
    onSecondaryContainer = BlueLight,
    tertiary = Color(0xFF6E6E73),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),
    surface = Color.White,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    surfaceTint = BlueLight,
    inverseSurface = Color(0xFF1D1D1F),
    inverseOnSurface = Color(0xFFF5F5F7),
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0x14000000),
    error = RedLight,
    onError = Color.White,
    errorContainer = Color(0x1AFF3B30),
    onErrorContainer = RedLight
)

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0x240A84FF),
    onPrimaryContainer = BlueDark,
    secondary = Color(0xFF98989D),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0x240A84FF),
    onSecondaryContainer = BlueDark,
    tertiary = Color(0xFF98989D),
    background = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF38383A),
    onSurfaceVariant = Color(0xFF98989D),
    surfaceTint = BlueDark,
    inverseSurface = Color(0xFFF5F5F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0x1AFFFFFF),
    error = RedDark,
    onError = Color.White,
    errorContainer = Color(0x24FF453A),
    onErrorContainer = RedDark
)

// Semantic helpers resolved at composition time.
data class LinkStatusColors(
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerQuiet: Color,
    val textTertiary: Color,
    val hairline: Color,
    val surfaceAlt: Color
)

@Composable
fun linkStatusColors(): LinkStatusColors {
    val dark = isSystemInDarkTheme()
    return LinkStatusColors(
        accent = MaterialTheme.colorScheme.primary,
        success = if (dark) GreenDark else GreenLight,
        warning = if (dark) AmberDark else AmberLight,
        danger = if (dark) RedDark else RedLight,
        dangerQuiet = if (dark) Color(0x24FF453A) else Color(0x1AFF3B30),
        textTertiary = if (dark) Color(0xFF636366) else Color(0xFFAEAEB2),
        hairline = if (dark) Color(0x1AFFFFFF) else Color(0x14000000),
        surfaceAlt = MaterialTheme.colorScheme.surfaceVariant
    )
}

// Type ramp (system font) — SF-Pro-equivalent hierarchy on Roboto.
private val LinkTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

// Control / card / overlay radii — applied to M3 components so stock shapes never leak.
private val LinkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun LinkToMacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = LinkTypography,
        shapes = LinkShapes,
        content = content
    )
}

// Spacing (4dp grid) and radii — single source of truth for Compose UI.
object LS {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp
    val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp
}

object LR {
    val control = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val overlay = RoundedCornerShape(16.dp)
    val badge = RoundedCornerShape(6.dp)
    val pill = RoundedCornerShape(percent = 50)
}

// Type ramp (sp) — weights come from the composables; use these instead of ad-hoc values.
object LT {
    val hero = 20.sp
    val section = 16.sp
    val body = 14.sp
    val caption = 12.sp
}
