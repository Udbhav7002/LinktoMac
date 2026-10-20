package com.linktomac.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// LinktoMac Design Tokens — "Quiet Premium" (mirrors LinkTheme.swift / web CSS)
private val BlueLight  = Color(0xFF0071E3)
private val BlueDark   = Color(0xFF0A84FF)
private val GreenLight = Color(0xFF34C759)
private val GreenDark  = Color(0xFF30D158)
private val AmberLight = Color(0xFFFF9500)
private val AmberDark  = Color(0xFFFF9F0A)
private val RedLight   = Color(0xFFFF3B30)
private val RedDark    = Color(0xFFFF453A)

private val LightScheme = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    secondary = Color(0xFF6E6E73),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),
    surface = Color.White,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFD1D1D6),
    error = RedLight
)

private val DarkScheme = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    secondary = Color(0xFF98989D),
    background = Color(0xFF1C1C1E),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF38383A),
    onSurfaceVariant = Color(0xFF98989D),
    outline = Color(0xFF48484A),
    error = RedDark
)

// Semantic helpers resolved at composition time.
data class LinkStatusColors(
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
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
        textTertiary = if (dark) Color(0xFF636366) else Color(0xFFAEAEB2),
        hairline = if (dark) Color(0x1AFFFFFF) else Color(0x14000000),
        surfaceAlt = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun LinkToMacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content
    )
}

// Spacing (4dp grid) and radii — single source of truth for Compose UI.
object LS {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp
    val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp
}

object LR {
    val control = RoundedCornerShape(10.dp)
    val card = RoundedCornerShape(16.dp)
    val overlay = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(percent = 50)
}

// Type ramp (sp / weight) — use these instead of ad-hoc values.
object LT {
    val hero = 20.sp to 600
    val section = 16.sp to 600
    val body = 14.sp to 400
    val caption = 12.sp to 400
}
