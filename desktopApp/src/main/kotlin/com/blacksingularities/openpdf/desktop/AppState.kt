package com.blacksingularities.openpdf.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openpdf.desktop.engine.DesktopPdfEngine
import com.blacksingularities.openpdf.desktop.ui.L
import com.blacksingularities.openpdf.i18n.AppLanguage
import com.blacksingularities.openpdf.i18n.I18n
import com.blacksingularities.openpdf.update.ReleaseInfo
import com.blacksingularities.openpdf.update.UPDATE_API
import com.blacksingularities.openpdf.update.isNewer
import com.blacksingularities.openpdf.update.parseRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale

enum class Dialog { NONE, SETTINGS, SHORTCUTS, ABOUT, PROPERTIES, UPDATE }

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Latest : UpdateStatus
    data class Available(val info: ReleaseInfo) : UpdateStatus
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

data class Toast(val text: String, val error: Boolean = false, val id: Long = System.nanoTime())

/** Formaty obsługiwane przez przeglądarkę (filtr okna otwierania i przeciąganie plików). */
val SUPPORTED_EXTENSIONS = listOf(
    "pdf", "djvu", "djv", "epub", "fb2", "mobi", "azw", "azw3", "prc", "cbz", "docx", "odt", "rtf",
    "md", "markdown", "html", "htm", "xhtml", "txt", "csv", "tsv", "jpg", "jpeg", "png", "webp", "gif", "bmp",
)

class AppState {
    val settings = Settings.load()
    val engine = DesktopPdfEngine()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val version: String = AppState::class.java.getResource("/openpdf-version.txt")?.readText()?.trim() ?: "dev"

    val tabs = mutableStateListOf<DocTab>()
    var active by mutableStateOf<DocTab?>(null)
    var dialog by mutableStateOf(Dialog.NONE)
    var fullscreen by mutableStateOf(false)
    var presentation by mutableStateOf(false)
    var dragOver by mutableStateOf(false)
    var toast by mutableStateOf<Toast?>(null)
    var update by mutableStateOf<UpdateStatus>(UpdateStatus.Idle)
    /** Pole tekstowe ma fokus – skróty jednoklawiszowe są wtedy wyłączone. */
    var typing by mutableStateOf(false)
    var thumbVersion by mutableStateOf(0)
    var focusSearch by mutableStateOf(0)
    var focusPage by mutableStateOf(0)
    /** Prośba o przywrócenie okna na wierzch (np. plik z drugiego uruchomienia). */
    var raise by mutableStateOf(0)

    private var saveJob: Job? = null

    init {
        applyLanguage()
    }

    fun applyLanguage() {
        I18n.apply(settings.language, Locale.getDefault().language)
    }

    fun setLanguage(l: AppLanguage) {
        settings.language = l
        applyLanguage()
        saveSoon()
    }

    fun saveSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            persist()
        }
    }

    fun persist() {
        for (t in tabs) if (t.ready) settings.touchRecentQuiet(t)
        settings.session = tabs.map { it.file.absolutePath }
        settings.activeInSession = tabs.indexOf(active).coerceAtLeast(0)
        settings.save()
    }

    private fun Settings.touchRecentQuiet(t: DocTab) {
        val i = recents.indexOfFirst { it.path.equals(t.file.absolutePath, ignoreCase = true) }
        if (i >= 0) recents[i] = recents[i].copy(page = t.page, pages = t.pageCount)
    }

    fun showToast(text: String, error: Boolean = false) {
        val t = Toast(text, error)
        toast = t
        scope.launch {
            delay(if (error) 5000 else 2600)
            if (toast?.id == t.id) toast = null
        }
    }

    // ------------------------------------------------------------------ karty

    fun open(files: List<File>, activate: Boolean = true) {
        var last: DocTab? = null
        for (f0 in files) {
            val f = runCatching { f0.canonicalFile }.getOrDefault(f0.absoluteFile)
            if (f.isDirectory) {
                open(f.listFiles()?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }?.sortedBy { it.name.lowercase() } ?: emptyList(), activate)
                continue
            }
            val existing = tabs.firstOrNull { it.file.path.equals(f.path, ignoreCase = true) }
            if (existing != null) {
                last = existing
                continue
            }
            val t = DocTab(f, this)
            tabs += t
            t.start()
            last = t
        }
        if (activate && last != null) active = last
        saveSoon()
    }

    fun close(tab: DocTab) {
        val i = tabs.indexOf(tab)
        if (i < 0) return
        if (tab.ready) {
            settings.touchRecent(tab.file.absolutePath, tab.page, tab.pageCount)
        }
        tabs.removeAt(i)
        tab.close()
        if (active == tab) active = tabs.getOrNull(i) ?: tabs.getOrNull(i - 1)
        if (tabs.isEmpty()) {
            fullscreen = false
            presentation = false
        }
        saveSoon()
    }

    fun closeOthers(tab: DocTab) = tabs.filter { it != tab }.forEach { close(it) }

    fun closeAll() = tabs.toList().forEach { close(it) }

    fun cycleTab(dir: Int) {
        if (tabs.size < 2) return
        val i = tabs.indexOf(active)
        active = tabs[((i + dir) % tabs.size + tabs.size) % tabs.size]
    }

    fun moveTab(from: Int, to: Int) {
        if (from !in tabs.indices || to !in tabs.indices || from == to) return
        val t = tabs.removeAt(from)
        tabs.add(to, t)
    }

    fun restoreSession() {
        if (!settings.restoreSession) return
        val files = settings.session.map { File(it) }.filter { it.isFile }
        if (files.isEmpty()) return
        open(files, activate = false)
        active = tabs.getOrNull(settings.activeInSession) ?: tabs.firstOrNull()
    }

    private var picking = false

    /** Okno wyboru plików Windows (w osobnym wątku, interfejs działa dalej); zapasowo – okno AWT. */
    fun pickFiles(window: Frame?) {
        if (picking) return
        picking = true
        val folder = settings.recents.firstOrNull()?.let { File(it.path).parentFile }
        val title = L("Otwórz dokumenty", "Open documents")
        val filters = listOf(
            NativeFileDialog.Filter(L("Wszystkie obsługiwane", "All supported"), SUPPORTED_EXTENSIONS.map { "*.$it" }),
            NativeFileDialog.Filter("PDF", listOf("*.pdf")),
            NativeFileDialog.Filter(L("Książki (EPUB, FB2, MOBI, DjVu)", "Books (EPUB, FB2, MOBI, DjVu)"), listOf("*.epub", "*.fb2", "*.mobi", "*.azw", "*.azw3", "*.prc", "*.djvu", "*.djv")),
            NativeFileDialog.Filter(L("Dokumenty tekstowe", "Text documents"), listOf("*.docx", "*.odt", "*.rtf", "*.md", "*.markdown", "*.html", "*.htm", "*.xhtml", "*.txt", "*.csv", "*.tsv")),
            NativeFileDialog.Filter(L("Komiksy i obrazy", "Comics and images"), listOf("*.cbz", "*.jpg", "*.jpeg", "*.png", "*.webp", "*.gif", "*.bmp")),
            NativeFileDialog.Filter(L("Wszystkie pliki", "All files"), listOf("*.*")),
        )
        Thread({
            val files = NativeFileDialog.open(window, title, filters, folder)
            javax.swing.SwingUtilities.invokeLater {
                picking = false
                if (files == null) pickFilesAwt(window) else if (files.isNotEmpty()) scope.launch { open(files) }
            }
        }, "file-dialog").apply { isDaemon = true }.start()
    }

    private fun pickFilesAwt(window: Frame?) {
        val d = FileDialog(window, L("Otwórz dokumenty", "Open documents"), FileDialog.LOAD)
        d.isMultipleMode = true
        d.file = SUPPORTED_EXTENSIONS.joinToString(";") { "*.$it" }
        settings.recents.firstOrNull()?.let { r -> File(r.path).parentFile?.takeIf { it.isDirectory }?.let { d.directory = it.absolutePath } }
        d.isVisible = true
        val files = d.files?.toList().orEmpty()
        if (files.isNotEmpty()) open(files)
    }

    fun removeRecent(path: String) {
        settings.recents.removeAll { it.path == path }
        runCatching { DocTab.thumbFile(path).delete() }
        saveSoon()
    }

    // ------------------------------------------------------------------ aktualizacje

    private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build() }

    fun checkUpdates(manual: Boolean) {
        if (update is UpdateStatus.Checking || update is UpdateStatus.Downloading) return
        update = UpdateStatus.Checking
        scope.launch {
            update = try {
                val info = withContext(Dispatchers.IO) {
                    val req = HttpRequest.newBuilder(URI(UPDATE_API)).header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(20)).build()
                    val res = http.send(req, HttpResponse.BodyHandlers.ofString())
                    if (res.statusCode() !in 200..299) error("HTTP ${res.statusCode()}")
                    parseRelease(res.body(), "-setup-x64.exe")
                }
                if (isNewer(info.version, version)) {
                    if (manual) {
                        settings.skippedVersion = ""
                        dialog = Dialog.UPDATE
                    }
                    UpdateStatus.Available(info)
                } else {
                    if (manual) showToast(L("Masz najnowszą wersję ({0})", "You have the latest version ({0})").replace("{0}", version))
                    UpdateStatus.Latest
                }
            } catch (e: Exception) {
                if (manual) showToast(L("Nie udało się sprawdzić aktualizacji", "Could not check for updates"), error = true)
                UpdateStatus.Failed(e.message ?: "")
            }
        }
    }

    /** Pobiera instalator nowej wersji, uruchamia go i zamyka program. */
    fun installUpdate(info: ReleaseInfo, exit: () -> Unit) {
        scope.launch {
            try {
                update = UpdateStatus.Downloading(info, 0f)
                val out = File(AppDirs.work, "OpenDocument-${info.version}-setup-x64.exe")
                withContext(Dispatchers.IO) {
                    val req = HttpRequest.newBuilder(URI(info.assetUrl)).timeout(Duration.ofMinutes(10)).build()
                    val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
                    if (res.statusCode() !in 200..299) error("HTTP ${res.statusCode()}")
                    val total = res.headers().firstValueAsLong("Content-Length").orElse(info.sizeBytes).coerceAtLeast(1)
                    res.body().use { input ->
                        out.outputStream().use { o ->
                            val buf = ByteArray(1 shl 16)
                            var done = 0L
                            var lastUi = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                done += n
                                if (done - lastUi > 256 * 1024) {
                                    lastUi = done
                                    val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) { update = UpdateStatus.Downloading(info, p) }
                                }
                            }
                        }
                    }
                }
                persist()
                ProcessBuilder(out.absolutePath, "/SP-", "/SILENT", "/NOCANCEL", "/RESTARTAPPLICATIONS").start()
                exit()
            } catch (e: Exception) {
                update = UpdateStatus.Available(info)
                showToast(L("Nie udało się pobrać aktualizacji", "Could not download the update") + ": " + (e.message ?: ""), error = true)
            }
        }
    }

    fun startUpdateChecks() {
        scope.launch {
            delay(4000)
            while (true) {
                if (settings.checkUpdates) checkUpdates(manual = false)
                delay(6L * 60 * 60 * 1000)
            }
        }
    }
}
