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
    SEPIA("Sepia"),
    ;

    val label: String get() = tr(pl)
}

/** Ustawienia aplikacji (SharedPreferences). */
data class AppSettings(
    val bookMode: BookMode = BookMode.AUTO,
    val coverAlone: Boolean = true,
    val rtl: Boolean = false,
    val spineShadow: Boolean = true,
    val scroll: ScrollMode = ScrollMode.VERTICAL,
    val fitSingle: FitMode = FitMode.WIDTH,
    val fitBook: FitMode = FitMode.PAGE,
    val pageGap: Boolean = true,
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
            bookMode = enumOr(prefs.getString("bookMode", null), d.bookMode),
            coverAlone = prefs.getBoolean("coverAlone", d.coverAlone),
            rtl = prefs.getBoolean("rtl", d.rtl),
            spineShadow = prefs.getBoolean("spineShadow", d.spineShadow),
            scroll = enumOr(prefs.getString("scroll", null), d.scroll),
            fitSingle = enumOr(prefs.getString("fitSingle", null), d.fitSingle),
            fitBook = enumOr(prefs.getString("fitBook", null), d.fitBook),
            pageGap = prefs.getBoolean("pageGap", d.pageGap),
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
            .putString("bookMode", s.bookMode.name)
            .putBoolean("coverAlone", s.coverAlone)
            .putBoolean("rtl", s.rtl)
            .putBoolean("spineShadow", s.spineShadow)
            .putString("scroll", s.scroll.name)
            .putString("fitSingle", s.fitSingle.name)
            .putString("fitBook", s.fitBook.name)
            .putBoolean("pageGap", s.pageGap)
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
