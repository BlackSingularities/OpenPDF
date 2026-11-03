package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.i18n.I18n

/** Motywy jak w OpenPano: czarny, jasny i szary; zaznaczenie to odwrócenie kolorów. */
enum class ThemeId { DARK, LIGHT, GRAY }

@Immutable
data class Palette(
    val bg: Color,
    val panel: Color,
    val surface: Color,
    val raised: Color,
    val raised2: Color,
    val canvas: Color,
    val line: Color,
    val line2: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val sel: Color,
    val selText: Color,
    val selHover: Color,
    val accent: Color,
    val warn: Color,
    val err: Color,
    val highlight: Color,
    val highlightCurrent: Color,
    val selection: Color,
    val dark: Boolean,
)

private fun dark() = Palette(
    bg = Color(0xFF000000),
    panel = Color(0xFF000000),
    surface = Color(0xFF000000),
    raised = Color(0xFF0D0D0D),
    raised2 = Color(0xFF1A1A1A),
    canvas = Color(0xFF0B0B0C),
    line = Color.White.copy(alpha = 0.14f),
    line2 = Color.White.copy(alpha = 0.24f),
    text = Color.White,
    text2 = Color.White.copy(alpha = 0.72f),
    text3 = Color.White.copy(alpha = 0.42f),
    sel = Color.White,
    selText = Color.Black,
    selHover = Color(0xFFE6E6E6),
    accent = Color(0xFFE5483A),
    warn = Color(0xFFFFD34D),
    err = Color(0xFFFF3B47),
    highlight = Color(0x66FFD34D),
    highlightCurrent = Color(0xAAFF8A00),
    selection = Color(0x553D8BFF),
    dark = true,
)

private fun light() = Palette(
    bg = Color(0xFFF4F4F5),
    panel = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFF0F0F2),
    raised2 = Color(0xFFE6E6E9),
    canvas = Color(0xFFE4E4E7),
    line = Color.Black.copy(alpha = 0.10f),
    line2 = Color.Black.copy(alpha = 0.20f),
    text = Color(0xFF0A0A0A),
    text2 = Color.Black.copy(alpha = 0.68f),
    text3 = Color.Black.copy(alpha = 0.42f),
    sel = Color(0xFF0A0A0A),
    selText = Color.White,
    selHover = Color(0xFF2A2A2A),
    accent = Color(0xFFD23A2C),
    warn = Color(0xFFB7791F),
    err = Color(0xFFD7263D),
    highlight = Color(0x66FFC400),
    highlightCurrent = Color(0x99FF7A00),
    selection = Color(0x553D8BFF),
    dark = false,
)

private fun gray() = Palette(
    bg = Color(0xFF26272A),
    panel = Color(0xFF2E2F33),
    surface = Color(0xFF2E2F33),
    raised = Color(0xFF37383D),
    raised2 = Color(0xFF424349),
    canvas = Color(0xFF1E1F22),
    line = Color.White.copy(alpha = 0.10f),
    line2 = Color.White.copy(alpha = 0.20f),
    text = Color(0xFFF2F2F3),
    text2 = Color.White.copy(alpha = 0.70f),
    text3 = Color.White.copy(alpha = 0.42f),
    sel = Color(0xFFF2F2F3),
    selText = Color(0xFF1E1F22),
    selHover = Color(0xFFDADADD),
    accent = Color(0xFFE5483A),
    warn = Color(0xFFFFD34D),
    err = Color(0xFFFF5964),
    highlight = Color(0x66FFD34D),
    highlightCurrent = Color(0xAAFF8A00),
    selection = Color(0x553D8BFF),
    dark = true,
)

fun palette(id: ThemeId) = when (id) {
    ThemeId.DARK -> dark()
    ThemeId.LIGHT -> light()
    ThemeId.GRAY -> gray()
}

val LocalPalette = staticCompositionLocalOf { dark() }

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
    val display = TextStyle(fontFamily = UiFont, fontSize = 26.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
}

@Composable
fun AppTheme(id: ThemeId, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette(id)) { content() }
}

/** Tekst w bieżącym języku: polski albo angielski. */
fun L(pl: String, en: String): String = if (I18n.english) en else pl
