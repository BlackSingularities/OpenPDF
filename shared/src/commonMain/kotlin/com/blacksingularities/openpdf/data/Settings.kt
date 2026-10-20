package com.blacksingularities.openpdf.data

import com.blacksingularities.openpdf.platform.KeyValueStore
import com.blacksingularities.openpdf.i18n.AppLanguage
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.ui.FitMode
import com.blacksingularities.openpdf.ui.ScrollMode

/**
 * Sposób wyświetlania dokumentu. [AUTO] – telefon w pionie: przewijanie ciągłe; poziomo,
 * na tablecie i rozłożonym foldzie – książka.
 */
enum class ViewMode(private val pl: String, private val plHint: String) {
    AUTO("Automatycznie", "Ciągły w pionie, książka poziomo, na tablecie i rozłożonym foldzie"),
    CONTINUOUS("Ciągły", "Jedna kolumna stron przewijana w pionie"),
    PAGES("Strony", "Po jednej stronie, przesuwanie w bok"),
    TWO_PAGES("Dwie strony", "Dwie strony obok siebie, przesuwanie w bok"),
    BOOK("Książka", "Rozkładówki z grzbietem i przewracaniem kartek"),
    ;

    val label: String get() = tr(pl)
    val hint: String get() = tr(plHint)

    /** Dwie strony w rzędzie. */
    val twoUp: Boolean get() = this == TWO_PAGES || this == BOOK

    val scroll: ScrollMode get() = if (this == CONTINUOUS) ScrollMode.VERTICAL else ScrollMode.PAGED
}

enum class PaperTheme(private val pl: String) {
    NORMAL("Zwykły"),
    NIGHT("Nocny"),
    DARK("Ciemny"),
    SEPIA("Sepia"),
    ;

    val label: String get() = tr(pl)
}

/** Ustawienia aplikacji. */
data class AppSettings(
    val viewMode: ViewMode = ViewMode.AUTO,
    val coverAlone: Boolean = true,
    val rtl: Boolean = false,
    val spineShadow: Boolean = true,
    val pageFlip: Boolean = true,
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
     * Przycięcie rozkładówki jako całości: w widoku książki lewy margines dotyczy tylko lewej
     * strony, prawy – tylko prawej, a miejsce styku przy grzbiecie zostaje nietknięte.
     */
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
    val author: String = "OpenDocument",
    val autoUpdate: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
)

class SettingsStore(private val prefs: KeyValueStore) {
    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            viewMode = enumOr(prefs.getString("viewMode"), legacyViewMode()),
            coverAlone = prefs.getBoolean("coverAlone", d.coverAlone),
            rtl = prefs.getBoolean("rtl", d.rtl),
            spineShadow = prefs.getBoolean("spineShadow", d.spineShadow),
            pageFlip = prefs.getBoolean("pageFlip", d.pageFlip),
            fitSingle = enumOr(prefs.getString("fitSingle"), d.fitSingle),
            fitBook = enumOr(prefs.getString("fitBook"), d.fitBook),
            pageGap = prefs.getBoolean("pageGap", d.pageGap),
            edgeToEdge = prefs.getBoolean("edgeToEdge", d.edgeToEdge),
            cropLeft = prefs.getFloat("cropLeft", 0f),
            cropTop = prefs.getFloat("cropTop", 0f),
            cropRight = prefs.getFloat("cropRight", 0f),
            cropBottom = prefs.getFloat("cropBottom", 0f),
            spreadCropLeft = prefs.getFloat("spreadCropLeft", 0f),
            spreadCropTop = prefs.getFloat("spreadCropTop", 0f),
            spreadCropRight = prefs.getFloat("spreadCropRight", 0f),
            spreadCropBottom = prefs.getFloat("spreadCropBottom", 0f),
            hideStatusBar = prefs.getBoolean("hideStatusBar", d.hideStatusBar),
            paper = enumOr(prefs.getString("paper"), d.paper),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            volumeKeys = prefs.getBoolean("volumeKeys", d.volumeKeys),
            tapZones = prefs.getBoolean("tapZones", d.tapZones),
            haptics = prefs.getBoolean("haptics", d.haptics),
            stylusOnly = prefs.getBoolean("stylusOnly", d.stylusOnly),
            author = prefs.getString("author") ?: d.author,
            autoUpdate = prefs.getBoolean("autoUpdate", d.autoUpdate),
            language = enumOr(prefs.getString("language"), d.language),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit {
            putString("viewMode", s.viewMode.name)
            putBoolean("coverAlone", s.coverAlone)
            putBoolean("rtl", s.rtl)
            putBoolean("spineShadow", s.spineShadow)
            putBoolean("pageFlip", s.pageFlip)
            putString("fitSingle", s.fitSingle.name)
            putString("fitBook", s.fitBook.name)
            putBoolean("pageGap", s.pageGap)
            putBoolean("edgeToEdge", s.edgeToEdge)
            putFloat("cropLeft", s.cropLeft)
            putFloat("cropTop", s.cropTop)
            putFloat("cropRight", s.cropRight)
            putFloat("cropBottom", s.cropBottom)
            putFloat("spreadCropLeft", s.spreadCropLeft)
            putFloat("spreadCropTop", s.spreadCropTop)
            putFloat("spreadCropRight", s.spreadCropRight)
            putFloat("spreadCropBottom", s.spreadCropBottom)
            putBoolean("hideStatusBar", s.hideStatusBar)
            putString("paper", s.paper.name)
            putBoolean("keepScreenOn", s.keepScreenOn)
            putBoolean("volumeKeys", s.volumeKeys)
            putBoolean("tapZones", s.tapZones)
            putBoolean("haptics", s.haptics)
            putBoolean("stylusOnly", s.stylusOnly)
            putString("author", s.author)
            putBoolean("autoUpdate", s.autoUpdate)
            putString("language", s.language.name)
        }
    }

    /** Tryb z ustawień sprzed wersji 1.7 (auto-układ, tryb książki, przewijanie). */
    private fun legacyViewMode(): ViewMode {
        if (prefs.getBoolean("autoLayout", true)) return ViewMode.AUTO
        val paged = prefs.getString("scroll") == ScrollMode.PAGED.name
        return when (prefs.getString("bookMode")) {
            "ALWAYS" -> if (paged) ViewMode.BOOK else ViewMode.TWO_PAGES
            "NEVER" -> if (paged) ViewMode.PAGES else ViewMode.CONTINUOUS
            else -> ViewMode.AUTO
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}

/** Tryb w użyciu: „Automatycznie” zamienia się w książkę ([autoBook]) albo widok ciągły. */
fun AppSettings.resolvedView(autoBook: Boolean): ViewMode =
    if (viewMode == ViewMode.AUTO) (if (autoBook) ViewMode.BOOK else ViewMode.CONTINUOUS) else viewMode

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
 * Przycięcie każdej strony w danym układzie. W rozkładówce strona po
 * lewej stronie grzbietu traci tylko lewy (zewnętrzny) margines, a po prawej – tylko prawy;
 * krawędzie przy grzbiecie zostają, więc strony stykają się jak w otwartej książce.
 */
fun AppSettings.pageCrops(groups: List<List<Int?>>, count: Int, book: Boolean): List<FloatArray> {
    val single = crop
    if (!book) return List(count) { single }
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
