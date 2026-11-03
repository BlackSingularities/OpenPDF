package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.i18n.I18n

/** Motywy: AMOLED (czysta czerń), grafit i jasny. Nazwy stałych zostają ze starszych wersji (zgodność ustawień). */
enum class ThemeId { DARK, GRAY, LIGHT }

val ThemeId.label: String
    get() = when (this) {
        ThemeId.DARK -> "AMOLED"
        ThemeId.GRAY -> L("Grafit", "Graphite")
        ThemeId.LIGHT -> L("Jasny", "Light")
    }

/** Kolor wyróżnienia: zaznaczone przyciski, aktywna karta, postęp. MONO – odwrócenie kolorów jak dawniej. */
enum class Accent(val dark: Long, val light: Long) {
    BLUE(0xFF5B9BFF, 0xFF2563EB),
    VIOLET(0xFFB197FC, 0xFF7C3AED),
    TEAL(0xFF2DD4BF, 0xFF0F8F82),
    GREEN(0xFF5EE38E, 0xFF15803D),
    AMBER(0xFFFFC53D, 0xFFC26A00),
    RED(0xFFFF6157, 0xFFD72C1F),
    MONO(0xFFFFFFFF, 0xFF111111),
    ;

    val label: String
        get() = when (this) {
            BLUE -> L("Niebieski", "Blue")
            VIOLET -> L("Fioletowy", "Violet")
            TEAL -> L("Morski", "Teal")
            GREEN -> L("Zielony", "Green")
            AMBER -> L("Bursztynowy", "Amber")
            RED -> L("Czerwony", "Red")
            MONO -> L("Czarno-biały", "Monochrome")
        }
}

@Immutable
data class Palette(
    /** Tło okna i paska tytułu. */
    val bg: Color,
    /** Paski narzędzi i panel boczny. */
    val panel: Color,
    /** Pola tekstowe i elementy na panelu. */
    val surface: Color,
    val raised: Color,
    val raised2: Color,
    /** Tło pod stronami dokumentu. */
    val canvas: Color,
    val line: Color,
    val line2: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    /** Zaznaczenie (aktywne przyciski): kolor akcentu i tekst na nim. */
    val sel: Color,
    val selText: Color,
    val selHover: Color,
    val accent: Color,
    /** Delikatne tło w kolorze akcentu (wybrana pozycja listy, aktywna karta). */
    val accentSoft: Color,
    val warn: Color,
    val err: Color,
    val ok: Color,
    val highlight: Color,
    val highlightCurrent: Color,
    val selection: Color,
    val dark: Boolean,
)

private class Base(
    val bg: Long, val panel: Long, val surface: Long, val raised: Long, val raised2: Long, val canvas: Long,
    val dark: Boolean,
)

private val AMOLED = Base(0xFF000000, 0xFF000000, 0xFF0B0B0C, 0xFF121214, 0xFF1D1D20, 0xFF060607, true)
private val GRAPHITE = Base(0xFF16171A, 0xFF1D1E22, 0xFF25262B, 0xFF2A2B31, 0xFF34363C, 0xFF0F1012, true)
private val LIGHT = Base(0xFFF2F2F4, 0xFFFFFFFF, 0xFFF5F5F7, 0xFFEDEDF0, 0xFFE3E3E8, 0xFFD9D9DE, false)

fun palette(id: ThemeId, accent: Accent = Accent.BLUE): Palette {
    val b = when (id) {
        ThemeId.DARK -> AMOLED
        ThemeId.GRAY -> GRAPHITE
        ThemeId.LIGHT -> LIGHT
    }
    val ink = if (b.dark) Color.White else Color(0xFF0B0B0D)
    val a = Color(if (b.dark) accent.dark else accent.light)
    val on = if (a.luminance() > 0.45f) Color(0xFF0B0B0D) else Color.White
    val hover = if (b.dark) lerp(a, Color.White, 0.14f) else lerp(a, Color.Black, 0.12f)
    return Palette(
        bg = Color(b.bg),
        panel = Color(b.panel),
        surface = Color(b.surface),
        raised = Color(b.raised),
        raised2 = Color(b.raised2),
        canvas = Color(b.canvas),
        line = ink.copy(alpha = if (b.dark) 0.10f else 0.09f),
        line2 = ink.copy(alpha = if (b.dark) 0.19f else 0.17f),
        text = ink,
        text2 = ink.copy(alpha = if (b.dark) 0.74f else 0.70f),
        text3 = ink.copy(alpha = if (b.dark) 0.46f else 0.46f),
        sel = a,
        selText = on,
        selHover = hover,
        accent = a,
        accentSoft = a.copy(alpha = if (accent == Accent.MONO) 0.12f else if (b.dark) 0.18f else 0.13f),
        warn = if (b.dark) Color(0xFFFFD34D) else Color(0xFFB7791F),
        err = if (b.dark) Color(0xFFFF5964) else Color(0xFFD7263D),
        ok = if (b.dark) Color(0xFF5EE38E) else Color(0xFF15803D),
        highlight = Color(0x66FFD34D),
        highlightCurrent = Color(0xAAFF8A00),
        selection = Color(0x553D8BFF),
        dark = b.dark,
    )
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha,
)

val LocalPalette = staticCompositionLocalOf { palette(ThemeId.DARK) }

val P: Palette @Composable get() = LocalPalette.current

@OptIn(ExperimentalTextApi::class)
val UiFont: FontFamily = runCatching {
    FontFamily(SystemFont("Segoe UI Variable Text"), SystemFont("Segoe UI"))
}.getOrDefault(FontFamily.Default)

object Type {
    val body = TextStyle(fontFamily = UiFont, fontSize = 12.5.sp)
    val small = TextStyle(fontFamily = UiFont, fontSize = 11.5.sp)
    val tiny = TextStyle(fontFamily = UiFont, fontSize = 10.5.sp)
    val title = TextStyle(fontFamily = UiFont, fontSize = 15.sp)
    val display = TextStyle(fontFamily = UiFont, fontSize = 24.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
}

@Composable
fun AppTheme(id: ThemeId, accent: Accent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette(id, accent)) { content() }
}

/** Tekst w bieżącym języku: polski albo angielski. */
fun L(pl: String, en: String): String = if (I18n.english) en else pl
