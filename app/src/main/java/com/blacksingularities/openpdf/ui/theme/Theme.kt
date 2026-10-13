package com.blacksingularities.openpdf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    // Czarno-biały interfejs jak w OpenShot: akcent = biel, czerwień dla zmian niezapisanych i usuwania.
    val Amber = Color(0xFFFFFFFF)
    val Coral = Color(0xFFE0E0E0)
    val Record = Color(0xFFFF3B47)
    val Moon = Color(0xFFBDBDBD)
    val Ink = Color(0xFF0B0C10)
    val Panel = Color(0xFF0B0B0B)
    val PanelHigh = Color(0xFF1C1C1C)
    val Glass = Color(0x29FFFFFF)
    val GlassStrong = Color(0x47000000)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)

    /** Tło pod stronami dokumentu. */
    val Desk = Color(0xFF15161A)

    /** Kolory narzędzi (pióro, kształty, tekst). */
    val InkColors = listOf(
        0xFF111111.toInt(), 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(),
        0xFFFB8C00.toInt(), 0xFF8E24AA.toInt(), 0xFF00ACC1.toInt(), 0xFFFFFFFF.toInt(),
    )

    /** Kolory zakreślacza i adnotacji tekstowych. */
    val MarkerColors = listOf(
        0xFFFFEB3B.toInt(), 0xFF76FF03.toInt(), 0xFF40C4FF.toInt(), 0xFFFF80AB.toInt(),
        0xFFFFAB40.toInt(), 0xFFB388FF.toInt(), 0xFFE53935.toInt(), 0xFF111111.toInt(),
    )
}

private val scheme = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Palette.Ink,
    secondary = Palette.Coral,
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Color.White,
    surface = Palette.Panel,
    onSurface = Color.White,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Palette.PanelHigh,
    outline = Color(0x33FFFFFF),
    error = Palette.Record,
)

val MonoDigits = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp)

@Composable
fun OpenPdfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
