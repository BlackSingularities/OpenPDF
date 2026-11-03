package com.blacksingularities.openpdf.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openpdf.desktop.ui.ThemeId
import com.blacksingularities.openpdf.i18n.AppLanguage
import java.io.File
import java.util.Properties

/** Układ stron: jedna, dwie obok siebie albo książka (okładka osobno). */
enum class PageLayout { SINGLE, SPREAD, BOOK }

/** Przewijanie ciągłe albo strona po stronie. */
enum class Flow { CONTINUOUS, PAGED }

enum class Paper { NORMAL, SEPIA, NIGHT }

enum class SideTab { THUMBS, OUTLINE, SEARCH }

/** Ostatnio otwierany plik i miejsce, w którym skończono czytać. */
data class Recent(val path: String, val page: Int, val pages: Int, val opened: Long)

/** Katalogi programu w profilu użytkownika. */
object AppDirs {
    private val roaming = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "OpenDocument")
    private val local = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "OpenDocument")

    val settings: File get() = File(roaming.apply { mkdirs() }, "settings.properties")
    val cache: File get() = File(local, "cache").apply { mkdirs() }
    val thumbs: File get() = File(local, "thumbs").apply { mkdirs() }
    val work: File get() = File(System.getProperty("java.io.tmpdir"), "OpenDocument").apply { mkdirs() }
    val instance: File get() = File(local.apply { mkdirs() }, "instance.port")
}

/** Ustawienia zapisywane w pliku właściwości w %APPDATA%\OpenDocument. */
class Settings {
    var language by mutableStateOf(AppLanguage.SYSTEM)
    var theme by mutableStateOf(ThemeId.DARK)
    var layout by mutableStateOf(PageLayout.SINGLE)
    var flow by mutableStateOf(Flow.CONTINUOUS)
    var rtl by mutableStateOf(false)
    var paper by mutableStateOf(Paper.NORMAL)
    var sidebar by mutableStateOf(true)
    var sideTab by mutableStateOf(SideTab.THUMBS)
    var restoreSession by mutableStateOf(true)
    var checkUpdates by mutableStateOf(true)
    var rememberPosition by mutableStateOf(true)
    var smoothScroll by mutableStateOf(true)
    var skippedVersion by mutableStateOf("")
    var session: List<String> = emptyList()
    var activeInSession = 0
    var window = WindowBounds()
    val recents = mutableStateListOf<Recent>()

    data class WindowBounds(val x: Int = -1, val y: Int = -1, val w: Int = 1320, val h: Int = 860, val maximized: Boolean = false)

    fun recentFor(path: String) = recents.firstOrNull { it.path.equals(path, ignoreCase = true) }

    fun touchRecent(path: String, page: Int, pages: Int) {
        val i = recents.indexOfFirst { it.path.equals(path, ignoreCase = true) }
        if (i >= 0) recents.removeAt(i)
        recents.add(0, Recent(path, page, pages, System.currentTimeMillis()))
        while (recents.size > 60) recents.removeAt(recents.size - 1)
    }

    fun save() {
        val p = Properties()
        p["language"] = language.name
        p["theme"] = theme.name
        p["layout"] = layout.name
        p["flow"] = flow.name
        p["rtl"] = rtl.toString()
        p["paper"] = paper.name
        p["sidebar"] = sidebar.toString()
        p["sideTab"] = sideTab.name
        p["restoreSession"] = restoreSession.toString()
        p["checkUpdates"] = checkUpdates.toString()
        p["rememberPosition"] = rememberPosition.toString()
        p["smoothScroll"] = smoothScroll.toString()
        p["skippedVersion"] = skippedVersion
        p["session"] = session.joinToString("\n")
        p["session.active"] = activeInSession.toString()
        p["window"] = "${window.x},${window.y},${window.w},${window.h},${window.maximized}"
        recents.forEachIndexed { i, r -> p["recent.$i"] = "${r.page}\t${r.pages}\t${r.opened}\t${r.path}" }
        runCatching {
            val f = AppDirs.settings
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { p.store(it, "OpenDocument") }
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    companion object {
        fun load(): Settings {
            val s = Settings()
            val p = Properties()
            runCatching { AppDirs.settings.takeIf { it.isFile }?.inputStream()?.use { p.load(it) } }
            fun str(k: String) = p.getProperty(k)
            fun bool(k: String, d: Boolean) = str(k)?.toBooleanStrictOrNull() ?: d
            fun <E : Enum<E>> enum(k: String, values: Array<E>, d: E) = values.firstOrNull { it.name == str(k) } ?: d
            s.language = enum("language", AppLanguage.entries.toTypedArray(), AppLanguage.SYSTEM)
            s.theme = enum("theme", ThemeId.entries.toTypedArray(), ThemeId.DARK)
            s.layout = enum("layout", PageLayout.entries.toTypedArray(), PageLayout.SINGLE)
            s.flow = enum("flow", Flow.entries.toTypedArray(), Flow.CONTINUOUS)
            s.rtl = bool("rtl", false)
            s.paper = enum("paper", Paper.entries.toTypedArray(), Paper.NORMAL)
            s.sidebar = bool("sidebar", true)
            s.sideTab = enum("sideTab", SideTab.entries.toTypedArray(), SideTab.THUMBS)
            s.restoreSession = bool("restoreSession", true)
            s.checkUpdates = bool("checkUpdates", true)
            s.rememberPosition = bool("rememberPosition", true)
            s.smoothScroll = bool("smoothScroll", true)
            s.skippedVersion = str("skippedVersion") ?: ""
            s.session = str("session")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            s.activeInSession = str("session.active")?.toIntOrNull() ?: 0
            str("window")?.split(',')?.let { w ->
                if (w.size == 5) s.window = WindowBounds(w[0].toInt(), w[1].toInt(), w[2].toInt(), w[3].toInt(), w[4].toBoolean())
            }
            val rec = p.stringPropertyNames().filter { it.startsWith("recent.") }.sortedBy { it.removePrefix("recent.").toIntOrNull() ?: 0 }
            for (k in rec) {
                val parts = str(k)?.split('\t', limit = 4) ?: continue
                if (parts.size < 4) continue
                s.recents += Recent(parts[3], parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0, parts[2].toLongOrNull() ?: 0L)
            }
            return s
        }
    }
}
