package com.blacksingularities.openpdf.data

import android.content.Context
import com.blacksingularities.openpdf.i18n.AppLanguage
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.ui.FitMode
import com.blacksingularities.openpdf.ui.ScrollMode

enum class BookMode(private val pl: String) {
    AUTO("Automatycznie"),
    ALWAYS("Zawsze"),
    NEVER("Nigdy"),
    ;

    val label: String get() = tr(pl)
}

enum class PaperTheme(private val pl: String) {
    NORMAL("Zwykły"),
    NIGHT("Nocny"),
    DARK("Ciemny"),
    SEPIA("Sepia"),
    ;

    val label: String get() = tr(pl)
}

/** Ustawienia aplikacji (SharedPreferences). */
data class AppSettings(
    /**
     * Automatyczny układ: telefon w pionie – pojedyncze strony przewijane w pionie; w poziomie,
     * na tablecie i rozłożonym foldzie – książka z rozkładówkami i przewracaniem kartek.
     */
    val autoLayout: Boolean = true,
    val bookMode: BookMode = BookMode.AUTO,
    val coverAlone: Boolean = true,
    val rtl: Boolean = false,
    val spineShadow: Boolean = true,
    val pageFlip: Boolean = true,
    val scroll: ScrollMode = ScrollMode.VERTICAL,
    val fitSingle: FitMode = FitMode.WIDTH,
    val fitBook: FitMode = FitMode.PAGE,
    val pageGap: Boolean = true,
    val edgeToEdge: Boolean = true,
    /** Przycięcie marginesów stron (ułamek szerokości/wysokości): lewy, górny, prawy, dolny. */
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f,
    /**
     * Przycinanie rozkładówki jako całości: w widoku książki lewy margines dotyczy tylko lewej
     * strony, prawy – tylko prawej, a miejsce styku przy grzbiecie zostaje nietknięte.
     * Wyłączone – każda strona rozkładówki przycinana jak pojedyncza strona.
     */
    val cropSpread: Boolean = true,
    val spreadCropLeft: Float = 0f,
    val spreadCropTop: Float = 0f,
    val spreadCropRight: Float = 0f,
    val spreadCropBottom: Float = 0f,
    val hideStatusBar: Boolean = false,
    val paper: PaperTheme = PaperTheme.NORMAL,
    val keepScreenOn: Boolean = true,
    val volumeKeys: Boolean = true,
    val tapZones: Boolean = true,
    val haptics: Boolean = true,
    val stylusOnly: Boolean = false,
    val author: String = "OpenPDF",
    val autoUpdate: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("openpdf_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            autoLayout = prefs.getBoolean("autoLayout", d.autoLayout),
            bookMode = enumOr(prefs.getString("bookMode", null), d.bookMode),
            coverAlone = prefs.getBoolean("coverAlone", d.coverAlone),
            rtl = prefs.getBoolean("rtl", d.rtl),
            spineShadow = prefs.getBoolean("spineShadow", d.spineShadow),
            pageFlip = prefs.getBoolean("pageFlip", d.pageFlip),
            scroll = enumOr(prefs.getString("scroll", null), d.scroll),
            fitSingle = enumOr(prefs.getString("fitSingle", null), d.fitSingle),
            fitBook = enumOr(prefs.getString("fitBook", null), d.fitBook),
            pageGap = prefs.getBoolean("pageGap", d.pageGap),
            edgeToEdge = prefs.getBoolean("edgeToEdge", d.edgeToEdge),
            cropLeft = prefs.getFloat("cropLeft", 0f),
            cropTop = prefs.getFloat("cropTop", 0f),
            cropRight = prefs.getFloat("cropRight", 0f),
            cropBottom = prefs.getFloat("cropBottom", 0f),
            cropSpread = prefs.getBoolean("cropSpread", d.cropSpread),
            spreadCropLeft = prefs.getFloat("spreadCropLeft", 0f),
            spreadCropTop = prefs.getFloat("spreadCropTop", 0f),
            spreadCropRight = prefs.getFloat("spreadCropRight", 0f),
            spreadCropBottom = prefs.getFloat("spreadCropBottom", 0f),
            hideStatusBar = prefs.getBoolean("hideStatusBar", d.hideStatusBar),
            paper = enumOr(prefs.getString("paper", null), d.paper),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            volumeKeys = prefs.getBoolean("volumeKeys", d.volumeKeys),
            tapZones = prefs.getBoolean("tapZones", d.tapZones),
            haptics = prefs.getBoolean("haptics", d.haptics),
            stylusOnly = prefs.getBoolean("stylusOnly", d.stylusOnly),
            author = prefs.getString("author", null) ?: d.author,
            autoUpdate = prefs.getBoolean("autoUpdate", d.autoUpdate),
            language = enumOr(prefs.getString("language", null), d.language),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean("autoLayout", s.autoLayout)
            .putString("bookMode", s.bookMode.name)
            .putBoolean("coverAlone", s.coverAlone)
            .putBoolean("rtl", s.rtl)
            .putBoolean("spineShadow", s.spineShadow)
            .putBoolean("pageFlip", s.pageFlip)
            .putString("scroll", s.scroll.name)
            .putString("fitSingle", s.fitSingle.name)
            .putString("fitBook", s.fitBook.name)
            .putBoolean("pageGap", s.pageGap)
            .putBoolean("edgeToEdge", s.edgeToEdge)
            .putFloat("cropLeft", s.cropLeft)
            .putFloat("cropTop", s.cropTop)
            .putFloat("cropRight", s.cropRight)
            .putFloat("cropBottom", s.cropBottom)
            .putBoolean("cropSpread", s.cropSpread)
            .putFloat("spreadCropLeft", s.spreadCropLeft)
            .putFloat("spreadCropTop", s.spreadCropTop)
            .putFloat("spreadCropRight", s.spreadCropRight)
            .putFloat("spreadCropBottom", s.spreadCropBottom)
            .putBoolean("hideStatusBar", s.hideStatusBar)
            .putString("paper", s.paper.name)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("volumeKeys", s.volumeKeys)
            .putBoolean("tapZones", s.tapZones)
            .putBoolean("haptics", s.haptics)
            .putBoolean("stylusOnly", s.stylusOnly)
            .putString("author", s.author)
            .putBoolean("autoUpdate", s.autoUpdate)
            .putString("language", s.language.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}

/** Tryb książki w użyciu (przy automatycznym układzie zawsze „Automatycznie”). */
val AppSettings.effectiveBookMode: BookMode get() = if (autoLayout) BookMode.AUTO else bookMode

/** Przewijanie w użyciu: przy automatycznym układzie książka przewraca kartki, pojedyncze strony płyną w pionie. */
fun AppSettings.effectiveScroll(book: Boolean): ScrollMode = if (autoLayout) (if (book) ScrollMode.PAGED else ScrollMode.VERTICAL) else scroll

/** Najwięcej, ile można przyciąć z jednej krawędzi strony. */
const val MAX_CROP = 0.3f

private fun cropOf(vararg v: Float) = FloatArray(4) { v[it].coerceIn(0f, MAX_CROP) }

/** Przycięcie pojedynczej strony jako tablica [lewy, górny, prawy, dolny], z bezpiecznymi granicami. */
val AppSettings.crop: FloatArray
    get() = cropOf(cropLeft, cropTop, cropRight, cropBottom)

/** Przycięcie rozkładówki [zewnętrzny lewy, górny, zewnętrzny prawy, dolny]. */
val AppSettings.spreadCrop: FloatArray
    get() = cropOf(spreadCropLeft, spreadCropTop, spreadCropRight, spreadCropBottom)

/**
 * Przycięcie każdej strony w danym układzie. W rozkładówce przycinanej jako całość strona po
 * lewej stronie grzbietu traci tylko lewy (zewnętrzny) margines, a po prawej – tylko prawy;
 * krawędzie przy grzbiecie zostają, więc strony stykają się jak w otwartej książce.
 */
fun AppSettings.pageCrops(groups: List<List<Int?>>, count: Int, book: Boolean): List<FloatArray> {
    val single = crop
    if (!book || !cropSpread) return List(count) { single }
    val sc = spreadCrop
    val leftSide = floatArrayOf(sc[0], sc[1], 0f, sc[3])
    val rightSide = floatArrayOf(0f, sc[1], sc[2], sc[3])
    val out = MutableList(count) { single }
    for (g in groups) {
        if (g.size != 2) continue
        g[0]?.let { out[it] = leftSide }
        g[1]?.let { out[it] = rightSide }
    }
    return out
}
