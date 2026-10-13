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

/** Przycięcie jako tablica [lewy, górny, prawy, dolny], z bezpiecznymi granicami. */
val AppSettings.crop: FloatArray
    get() = floatArrayOf(cropLeft, cropTop, cropRight, cropBottom).map { it.coerceIn(0f, 0.3f) }.toFloatArray()
