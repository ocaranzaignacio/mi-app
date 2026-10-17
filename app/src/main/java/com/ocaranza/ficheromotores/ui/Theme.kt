package com.ocaranza.ficheromotores.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class Ext(
    val bg: Color, val panel: Color, val panel2: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val accentSoft: Color,
    val plate: Color, val plateInk: Color, val plateLine: Color,
    val ok: Color, val okSoft: Color, val obs: Color, val obsSoft: Color, val bad: Color, val badSoft: Color,
)

private val LightExt = Ext(
    bg = Color(0xFFE9EDF0), panel = Color(0xFFFFFFFF), panel2 = Color(0xFFF4F6F8), ink = Color(0xFF16212B),
    muted = Color(0xFF5B6874), line = Color(0xFFCFD7DE), accent = Color(0xFF1C5AA0), accentSoft = Color(0xFFDDE8F5),
    plate = Color(0xFFD9DEE3), plateInk = Color(0xFF1E2A33), plateLine = Color(0xFFA9B3BC),
    ok = Color(0xFF1F7A45), okSoft = Color(0xFFDCF0E3), obs = Color(0xFF9A6200), obsSoft = Color(0xFFFBECCD),
    bad = Color(0xFFB3261E), badSoft = Color(0xFFF8DCD9),
)
private val DarkExt = Ext(
    bg = Color(0xFF11171C), panel = Color(0xFF1A2229), panel2 = Color(0xFF212B33), ink = Color(0xFFE3E9EE),
    muted = Color(0xFF93A1AD), line = Color(0xFF2F3B45), accent = Color(0xFF6AA5EA), accentSoft = Color(0xFF1D3047),
    plate = Color(0xFF2A333B), plateInk = Color(0xFFE1E7EC), plateLine = Color(0xFF46535E),
    ok = Color(0xFF6FCF97), okSoft = Color(0xFF173326), obs = Color(0xFFE8B04B), obsSoft = Color(0xFF3A2D12),
    bad = Color(0xFFF08B83), badSoft = Color(0xFF3D1C1A),
)

val LocalExt = staticCompositionLocalOf { LightExt }

object C {
    val x: Ext @Composable get() = LocalExt.current
}

val Mono = FontFamily.Monospace
val Condensed = FontFamily.SansSerif

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val e = if (dark) DarkExt else LightExt
    val scheme = if (dark) darkColorScheme(
        primary = e.accent, onPrimary = Color(0xFF0B1A2B), primaryContainer = e.accentSoft, onPrimaryContainer = e.ink,
        background = e.bg, onBackground = e.ink, surface = e.panel, onSurface = e.ink, surfaceVariant = e.panel2,
        onSurfaceVariant = e.muted, outline = e.line, outlineVariant = e.line, error = e.bad,
        surfaceContainer = e.panel, surfaceContainerHigh = e.panel, surfaceContainerLow = e.panel, surfaceContainerHighest = e.panel2,
    ) else lightColorScheme(
        primary = e.accent, onPrimary = Color.White, primaryContainer = e.accentSoft, onPrimaryContainer = e.ink,
        background = e.bg, onBackground = e.ink, surface = e.panel, onSurface = e.ink, surfaceVariant = e.panel2,
        onSurfaceVariant = e.muted, outline = e.line, outlineVariant = e.line, error = e.bad,
        surfaceContainer = e.panel, surfaceContainerHigh = e.panel, surfaceContainerLow = e.panel, surfaceContainerHighest = e.panel2,
    )
    val base = Typography()
    val typo = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalExt provides e) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}

/** Estilo de encabezado de sección: mayúsculas, condensado, con espaciado. */
val SectionTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.2.sp, fontFamily = Condensed)
