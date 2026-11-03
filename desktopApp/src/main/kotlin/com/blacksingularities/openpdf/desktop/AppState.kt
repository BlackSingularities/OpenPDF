package com.blacksingularities.openpdf.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openpdf.desktop.engine.DesktopPdfEngine
import com.blacksingularities.openpdf.desktop.engine.PdfOps
import com.blacksingularities.openpdf.desktop.ui.L
import com.blacksingularities.openpdf.i18n.AppLanguage
import com.blacksingularities.openpdf.i18n.I18n
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.ui.baseName
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
import okio.Path.Companion.toOkioPath
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import javax.imageio.ImageIO

enum class Dialog { NONE, SETTINGS, SHORTCUTS, ABOUT, PROPERTIES, UPDATE, EXPORT_IMAGES, COMPRESS, SECURITY, TEXT, NEW_DOC, UNSAVED, INPUT }

/** Narzędzie w widoku dokumentu. */
enum class Tool {
    SELECT, HAND, MARKUP, PEN, MARKER, SHAPE, TEXT, NOTE, ERASER;

    val label: String
        get() = when (this) {
            SELECT -> L("Zaznaczanie tekstu", "Select text")
            HAND -> L("Rączka", "Hand")
            MARKUP -> L("Wyróżnianie tekstu", "Text markup")
            PEN -> L("Pióro", "Pen")
            MARKER -> L("Zakreślacz", "Highlighter")
            SHAPE -> L("Kształty", "Shapes")
            TEXT -> L("Pole tekstowe", "Text box")
            NOTE -> L("Notatka", "Note")
            ERASER -> L("Gumka", "Eraser")
        }

    val key: String
        get() = when (this) {
            SELECT -> "V"
            HAND -> "H"
            MARKUP -> "U"
            PEN -> "P"
            MARKER -> "M"
            SHAPE -> "S"
            TEXT -> "T"
            NOTE -> "K"
            ERASER -> "E"
        }

    /** Narzędzia zmieniające dokument (działają tylko na gotowej karcie). */
    val annotates: Boolean get() = this != SELECT && this != HAND
}

/** Kolory narzędzi (ARGB). */
val INK_COLORS = listOf(
    0xFF111111.toInt(), 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(),
    0xFFFB8C00.toInt(), 0xFF8E24AA.toInt(), 0xFFFFFFFF.toInt(), 0xFF757575.toInt(),
)
val MARKER_COLORS = listOf(
    0xFFFFEB3B.toInt(), 0xFF76FF03.toInt(), 0xFF40C4FF.toInt(), 0xFFFF80AB.toInt(), 0xFFFFAB40.toInt(), 0xFFB388FF.toInt(),
)

/** Ustawienia narzędzi rysowania (wspólne dla kart). */
class ToolStyles {
    var penColor by mutableStateOf(INK_COLORS[2])
    var penWidth by mutableStateOf(2f)
    var markerColor by mutableStateOf(MARKER_COLORS[0])
    var markerWidth by mutableStateOf(12f)
    var markupKind by mutableStateOf(MarkupKind.HIGHLIGHT)
    var markupColor by mutableStateOf(MARKER_COLORS[0])
    var shapeKind by mutableStateOf(ShapeKind.RECT)
    var shapeColor by mutableStateOf(INK_COLORS[1])
    var shapeWidth by mutableStateOf(2f)
    var shapeFill by mutableStateOf(false)
    var textColor by mutableStateOf(INK_COLORS[0])
    var textSize by mutableStateOf(12f)
    var textBackground by mutableStateOf(false)
    var noteColor by mutableStateOf(0xFFFFD54F.toInt())
}

/** Prośba o tekst od użytkownika (pole tekstowe, notatka, nazwa zakładki…). */
data class InputRequest(
    val title: String,
    val initial: String = "",
    val multiline: Boolean = false,
    val hint: String = "",
    val confirm: String = "",
    val onDone: (String) -> Unit,
)

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

val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

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
    /** Okno programu (rodzic okien systemowych). */
    var window: Frame? = null

    var tool by mutableStateOf(Tool.SELECT)
    val styles = ToolStyles()
    /** Pole formularza kliknięte na stronie – panel formularza przewija się do niego. */
    var focusField by mutableStateOf<String?>(null)
    var input by mutableStateOf<InputRequest?>(null)
    /** Karty do zamknięcia mimo niezapisanych zmian i dalszy krok (zamknięcie programu). */
    var closing by mutableStateOf<List<DocTab>>(emptyList())
    private var afterClosing: (() -> Unit)? = null
    /** Wyodrębniony tekst dokumentu (okno „Tekst dokumentu”). */
    var extracted by mutableStateOf<String?>(null)
    /** Trwająca operacja programu (scalanie, eksport…) – pasek postępu na dole okna. */
    var work by mutableStateOf<String?>(null)
    var workProgress by mutableStateOf<Float?>(null)

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

    fun ask(req: InputRequest) {
        input = req
        dialog = Dialog.INPUT
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

    /** Zamyka kartę; gdy ma niezapisane zmiany – najpierw pyta. */
    fun requestClose(tab: DocTab) {
        if (tab.unsaved) {
            closing = listOf(tab)
            afterClosing = null
            active = tab
            dialog = Dialog.UNSAVED
        } else {
            close(tab)
        }
    }

    fun requestCloseMany(list: List<DocTab>, then: (() -> Unit)? = null) {
        val dirty = list.filter { it.unsaved }
        list.filter { !it.unsaved }.forEach { close(it) }
        if (dirty.isEmpty()) {
            then?.invoke()
            return
        }
        closing = dirty
        afterClosing = then
        dialog = Dialog.UNSAVED
    }

    /** Odpowiedź z okna niezapisanych zmian: zapisać, odrzucić albo anulować (null). */
    fun resolveClosing(save: Boolean?) {
        val list = closing
        val then = afterClosing
        closing = emptyList()
        afterClosing = null
        dialog = Dialog.NONE
        if (save == null) return
        if (!save) {
            list.forEach { close(it) }
            then?.invoke()
            return
        }
        scope.launch {
            for (t in list) {
                val ok = if (t.savesInPlace) t.saveTo(t.file) else {
                    val target = chooseSaveBlocking(t) ?: return@launch
                    t.saveTo(target)
                }
                if (!ok) return@launch
                close(t)
            }
            then?.invoke()
        }
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

    fun closeOthers(tab: DocTab) = requestCloseMany(tabs.filter { it != tab })

    fun closeAll() = requestCloseMany(tabs.toList())

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

    fun removeRecent(path: String) {
        settings.recents.removeAll { it.path == path }
        runCatching { DocTab.thumbFile(path).delete() }
        saveSoon()
    }

    // ------------------------------------------------------------------ okna plików

    private var picking = false

    private val lastFolder: File?
        get() = (active?.file ?: settings.recents.firstOrNull()?.let { File(it.path) })?.parentFile?.takeIf { it.isDirectory }

    private val openFilters
        get() = listOf(
            NativeFileDialog.Filter(L("Wszystkie obsługiwane", "All supported"), SUPPORTED_EXTENSIONS.map { "*.$it" }),
            NativeFileDialog.Filter("PDF", listOf("*.pdf")),
            NativeFileDialog.Filter(L("Książki (EPUB, FB2, MOBI, DjVu)", "Books (EPUB, FB2, MOBI, DjVu)"), listOf("*.epub", "*.fb2", "*.mobi", "*.azw", "*.azw3", "*.prc", "*.djvu", "*.djv")),
            NativeFileDialog.Filter(L("Dokumenty tekstowe", "Text documents"), listOf("*.docx", "*.odt", "*.rtf", "*.md", "*.markdown", "*.html", "*.htm", "*.xhtml", "*.txt", "*.csv", "*.tsv")),
            NativeFileDialog.Filter(L("Komiksy i obrazy", "Comics and images"), listOf("*.cbz", "*.jpg", "*.jpeg", "*.png", "*.webp", "*.gif", "*.bmp")),
            NativeFileDialog.Filter(L("Wszystkie pliki", "All files"), listOf("*.*")),
        )

    val pdfFilter get() = listOf(NativeFileDialog.Filter("PDF", listOf("*.pdf")))
    val imageFilter get() = listOf(NativeFileDialog.Filter(L("Obrazy", "Images"), IMAGE_EXTENSIONS.map { "*.$it" }))

    /** Wybór plików w osobnym wątku (interfejs działa dalej); zapasowo – okno AWT. */
    fun chooseFiles(title: String, filters: List<NativeFileDialog.Filter>, onPicked: (List<File>) -> Unit) {
        if (picking) return
        picking = true
        val folder = lastFolder
        val win = window
        Thread({
            val files = NativeFileDialog.open(win, title, filters, folder)
            javax.swing.SwingUtilities.invokeLater {
                picking = false
                val result = files ?: awtOpen(title, filters)
                if (result.isNotEmpty()) scope.launch { onPicked(result) }
            }
        }, "file-dialog").apply { isDaemon = true }.start()
    }

    private fun awtOpen(title: String, filters: List<NativeFileDialog.Filter>): List<File> {
        val d = FileDialog(window, title, FileDialog.LOAD)
        d.isMultipleMode = true
        filters.firstOrNull()?.let { f -> d.file = f.patterns.joinToString(";") }
        lastFolder?.let { d.directory = it.absolutePath }
        d.isVisible = true
        return d.files?.toList().orEmpty()
    }

    fun pickFiles(window: Frame? = this.window) {
        if (window != null) this.window = window
        chooseFiles(L("Otwórz dokumenty", "Open documents"), openFilters) { open(it) }
    }

    /** Okno „Zapisz jako”; [onPicked] dostaje plik z rozszerzeniem [ext]. */
    fun chooseSave(title: String, name: String, ext: String, filters: List<NativeFileDialog.Filter>, onPicked: (File) -> Unit) {
        if (picking) return
        picking = true
        val folder = lastFolder
        val win = window
        Thread({
            val r = NativeFileDialog.save(win, title, filters, folder, name, ext)
            javax.swing.SwingUtilities.invokeLater {
                picking = false
                val f = if (r == null) awtSave(title, name, folder) else r.firstOrNull()
                if (f != null) scope.launch { onPicked(withExtension(f, ext)) }
            }
        }, "file-dialog").apply { isDaemon = true }.start()
    }

    private fun withExtension(f: File, ext: String) = if (f.extension.equals(ext, ignoreCase = true)) f else File(f.parentFile, f.name + "." + ext)

    private fun awtSave(title: String, name: String, folder: File?): File? {
        val d = FileDialog(window, title, FileDialog.SAVE)
        d.file = name
        folder?.let { d.directory = it.absolutePath }
        d.isVisible = true
        val n = d.file ?: return null
        return File(d.directory ?: "", n)
    }

    /** Wersja do użycia w korutynie (zamykanie z zapisem). */
    private suspend fun chooseSaveBlocking(tab: DocTab): File? = kotlinx.coroutines.suspendCancellableCoroutine { c ->
        val name = baseName(tab.name) + ".pdf"
        val folder = tab.file.parentFile
        val win = window
        Thread({
            val r = NativeFileDialog.save(win, L("Zapisz jako", "Save as"), pdfFilter, folder, name, "pdf")
            javax.swing.SwingUtilities.invokeLater {
                val f = if (r == null) awtSave(L("Zapisz jako", "Save as"), name, folder) else r.firstOrNull()
                c.resumeWith(Result.success(f?.let { withExtension(it, "pdf") }))
            }
        }, "file-dialog").apply { isDaemon = true }.start()
    }

    fun chooseFolder(title: String, onPicked: (File) -> Unit) {
        if (picking) return
        picking = true
        val folder = lastFolder
        val win = window
        Thread({
            val r = NativeFileDialog.pickFolder(win, title, folder)
            javax.swing.SwingUtilities.invokeLater {
                picking = false
                val f = if (r == null) swingFolder(title, folder) else r.firstOrNull()
                if (f != null) scope.launch { onPicked(f) }
            }
        }, "file-dialog").apply { isDaemon = true }.start()
    }

    private fun swingFolder(title: String, folder: File?): File? {
        val c = javax.swing.JFileChooser(folder)
        c.dialogTitle = title
        c.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        return if (c.showOpenDialog(window) == javax.swing.JFileChooser.APPROVE_OPTION) c.selectedFile else null
    }

    // ------------------------------------------------------------------ zapis i pliki

    fun save(tab: DocTab? = active) {
        val t = tab ?: return
        if (!t.ready) return
        if (!t.savesInPlace) return saveAs(t)
        if (!t.unsaved) {
            showToast(L("Brak zmian do zapisania", "No changes to save"))
            return
        }
        scope.launch { t.saveTo(t.file) }
    }

    fun saveAs(tab: DocTab? = active) {
        val t = tab ?: return
        if (!t.ready) return
        chooseSave(L("Zapisz jako", "Save as"), baseName(t.name) + ".pdf", "pdf", pdfFilter) { f ->
            if (tabs.any { it != t && it.file.absolutePath.equals(f.absolutePath, ignoreCase = true) }) {
                showToast(L("Ten plik jest otwarty w innej karcie", "This file is open in another tab"), error = true)
                return@chooseSave
            }
            scope.launch { t.saveTo(f) }
        }
    }

    private fun runWork(label: String, block: suspend () -> Unit) {
        if (work != null) return
        work = label
        workProgress = null
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                showToast(label.trimEnd('…') + ": " + (e.message ?: e.javaClass.simpleName), error = true)
            } finally {
                work = null
                workProgress = null
            }
        }
    }

    /** Scala wybrane pliki PDF w jeden nowy plik i otwiera go. */
    fun mergePdfs() {
        chooseFiles(L("Wybierz pliki PDF do scalenia (w kolejności)", "Choose PDF files to merge (in order)"), pdfFilter) { files ->
            if (files.size < 2) {
                showToast(L("Wybierz co najmniej dwa pliki", "Choose at least two files"), error = true)
                return@chooseFiles
            }
            chooseSave(L("Zapisz scalony dokument", "Save the merged document"), L("Scalony.pdf", "Merged.pdf"), "pdf", pdfFilter) { out ->
                runWork(L("Scalanie dokumentów…", "Merging documents…")) {
                    withContext(Dispatchers.IO) {
                        val tmp = File(out.parentFile, out.name + ".part")
                        files.first().copyTo(tmp, overwrite = true)
                        files.drop(1).forEachIndexed { i, f ->
                            PdfOps.insertPdf(tmp, f, null, null)
                            withContext(Dispatchers.Main) { workProgress = (i + 2f) / files.size }
                        }
                        PdfOps.replace(tmp, out)
                    }
                    open(listOf(out))
                }
            }
        }
    }

    fun imagesToPdf() {
        chooseFiles(L("Wybierz obrazy (po jednym na stronę)", "Choose images (one per page)"), imageFilter) { files ->
            chooseSave(L("Zapisz nowy dokument", "Save the new document"), baseName(files.first().name) + ".pdf", "pdf", pdfFilter) { out ->
                runWork(L("Tworzenie dokumentu z obrazów…", "Creating a document from images…")) {
                    withContext(Dispatchers.IO) {
                        val tmp = File(out.parentFile, out.name + ".part")
                        engine.createFromImages(files.sortedBy { it.name.lowercase() }.map { it.toOkioPath() }, tmp.toOkioPath())
                        PdfOps.replace(tmp, out)
                    }
                    open(listOf(out))
                }
            }
        }
    }

    fun newBlank(pages: Int, landscape: Boolean, letter: Boolean) {
        chooseSave(L("Nowy dokument", "New document"), L("Nowy dokument.pdf", "New document.pdf"), "pdf", pdfFilter) { out ->
            runWork(L("Tworzenie dokumentu…", "Creating the document…")) {
                withContext(Dispatchers.IO) {
                    val base = if (letter) PDRectangle.LETTER else PDRectangle.A4
                    val size = if (landscape) PDRectangle(base.height, base.width) else base
                    PdfOps.createBlank(out, pages, size)
                }
                open(listOf(out))
            }
        }
    }

    /** Zapisuje strony jako obrazy w wybranym folderze. */
    fun exportImages(tab: DocTab, pages: List<Int>, dpi: Int, png: Boolean) {
        chooseFolder(L("Folder na obrazy stron", "Folder for page images")) { dir ->
            runWork(L("Eksport stron…", "Exporting pages…")) {
                val src = tab.currentFile()
                val base = baseName(tab.name)
                withContext(Dispatchers.IO) {
                    val r = com.blacksingularities.openpdf.desktop.engine.DesktopRenderer.open(src)
                    try {
                        pages.forEachIndexed { i, p ->
                            val s = r.sizes.getOrNull(p) ?: return@forEachIndexed
                            var w = (s.width / 72f * dpi).toInt()
                            var h = (s.height / 72f * dpi).toInt()
                            val max = 40_000_000f
                            if (w.toFloat() * h > max) {
                                val k = kotlin.math.sqrt(max / (w.toFloat() * h))
                                w = (w * k).toInt()
                                h = (h * k).toInt()
                            }
                            val img = r.renderAwt(p, w, h) ?: return@forEachIndexed
                            val n = (p + 1).toString().padStart(pages.maxOf { it + 1 }.toString().length, '0')
                            ImageIO.write(img, if (png) "png" else "jpg", File(dir, "$base-$n.${if (png) "png" else "jpg"}"))
                            withContext(Dispatchers.Main) { workProgress = (i + 1f) / pages.size }
                        }
                    } finally {
                        r.close()
                    }
                }
                showToast(L("Zapisano {0} obrazów w {1}", "Saved {0} images to {1}").replace("{0}", pages.size.toString()).replace("{1}", dir.name))
                runCatching { java.awt.Desktop.getDesktop().open(dir) }
            }
        }
    }

    fun extractText(tab: DocTab) {
        val t = tab.text ?: return
        runWork(L("Wyodrębnianie tekstu…", "Extracting text…")) {
            val text = t.allText { p -> scope.launch { workProgress = p } }.trim()
            extracted = text
            dialog = Dialog.TEXT
        }
    }

    fun saveText(text: String, name: String) {
        chooseSave(L("Zapisz tekst", "Save text"), baseName(name) + ".txt", "txt", listOf(NativeFileDialog.Filter(L("Tekst", "Text"), listOf("*.txt")))) { f ->
            runCatching { f.writeText(text, Charsets.UTF_8) }
                .onSuccess { showToast(L("Zapisano {0}", "Saved {0}").replace("{0}", f.name)) }
                .onFailure { showToast(L("Nie udało się zapisać", "Could not save") + ": " + (it.message ?: ""), error = true) }
        }
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
