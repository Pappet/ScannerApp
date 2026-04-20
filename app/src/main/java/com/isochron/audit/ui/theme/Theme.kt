package com.scanner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scanner.app.R

// ── Spectrum palette ─────────────────────────────────────────
// Instrument-panel dark. Chartreuse accent. From variant-a.jsx handoff.
object Spectrum {
    val Surface = Color(0xFF07090A)
    val SurfaceRaised = Color(0xFF0E1214)
    val SurfaceHi = Color(0xFF131719)
    val Bezel = Color(0xFF111416)
    val FrameBorder = Color(0xFF1C2225)
    val GridLine = Color(0xFF1A2023)

    val OnSurface = Color(0xFFE8EFEC)
    val OnSurfaceDim = Color(0xFF7C8A86)
    val OnSurfaceFaint = Color(0xFF3B4543)

    val Accent = Color(0xFFC8FF4F)        // chartreuse — oscilloscope
    val AccentDim = Color(0xFF5E7A20)
    val Accent2 = Color(0xFF6ED4FF)       // cyan trace
    val Success = Color(0xFF7BD88F)
    val Warning = Color(0xFFFFCB5E)
    val Danger = Color(0xFFFF7A66)

    // Severity helpers (AUDIT_FINDINGS)
    val SeverityHigh = Color(0xFFFF9B66)
    val SeverityLow = Color(0xFF8EC5D9)
}

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

private val SpectrumColorScheme = darkColorScheme(
    primary = Spectrum.Accent,
    onPrimary = Spectrum.Surface,
    primaryContainer = Spectrum.AccentDim,
    onPrimaryContainer = Spectrum.Accent,
    secondary = Spectrum.Accent2,
    onSecondary = Spectrum.Surface,
    tertiary = Spectrum.Warning,
    background = Spectrum.Surface,
    onBackground = Spectrum.OnSurface,
    surface = Spectrum.Surface,
    onSurface = Spectrum.OnSurface,
    surfaceVariant = Spectrum.SurfaceRaised,
    onSurfaceVariant = Spectrum.OnSurfaceDim,
    outline = Spectrum.GridLine,
    outlineVariant = Spectrum.GridLine,
    error = Spectrum.Danger,
    onError = Spectrum.Surface,
)

private val SpectrumTypography = Typography(
    displayLarge = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 56.sp, letterSpacing = (-0.04).em),
    displayMedium = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 38.sp, letterSpacing = (-0.03).em),
    displaySmall = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 26.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.01).em),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.2.em),
    labelMedium = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.18.em),
    labelSmall = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 0.18.em),
)

private val SpectrumShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/**
 * Spectrum theme — instrument-panel dark, monospaced, dense.
 * Forced dark (no light variant, no dynamic color).
 */
@Composable
fun ScannerAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpectrumColorScheme,
        typography = SpectrumTypography,
        shapes = SpectrumShapes,
        content = content,
    )
}
