package com.blacksingularities.openpdf.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openpdf.desktop.engine.DesktopPdfEngine
import com.blacksingularities.openpdf.desktop.engine.DesktopRenderer
import com.blacksingularities.openpdf.desktop.engine.PdfOps
import com.blacksingularities.openpdf.desktop.ui.L
import com.blacksingularities.openpdf.desktop.ui.PageCache
import com.blacksingularities.openpdf.doc.DocFormat
import com.blacksingularities.openpdf.pdf.AnnotInfo
import com.blacksingularities.openpdf.pdf.DocIndex
import com.blacksingularities.openpdf.pdf.FieldEdit
import com.blacksingularities.openpdf.pdf.InfoEdit
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.PageText
import com.blacksingularities.openpdf.pdf.SearchHit
import com.blacksingularities.openpdf.pdf.TextIndex
import com.blacksingularities.openpdf.ui.convertToPdf
import com.blacksingularities.openpdf.ui.detectFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

enum class ZoomMode { FIT_WIDTH, FIT_PAGE, CUSTOM }

sealed interface TabStatus {
    data class Loading(val label: String, val progress: Float? = null) : TabStatus
    data class NeedPassword(val wrong: Boolean) : TabStatus
    data class Failed(val message: String) : TabStatus
    data object Ready : TabStatus
}

/** Prośba o przejście do miejsca w dokumencie (obsługuje ją widok). */
data class NavRequest(val page: Int, val yFrac: Float? = null, val seq: Int = navSeq.incrementAndGet(), val animate: Boolean = false)

private val navSeq = AtomicInteger()
private val tabSeq = AtomicInteger()

/** Zaznaczenie tekstu na jednej stronie. */
data class TextSelection(val page: Int, val anchor: Int, val focus: Int) {
    val range: IntRange get() = minOf(anchor, focus)..maxOf(anchor, focus)
}

/** Hasła, którymi dokument zostanie zaszyfrowany przy zapisie. */
data class Protection(val user: String, val owner: String)

/** Zmiana do cofnięcia: tylko oczekujące elementy albo cały plik (migawka) z elementami. */
private sealed interface Change {
    data class Marks(val before: List<Mark>) : Change
    data class FileState(val snapshot: File, val marks: List<Mark>, val label: String) : Change
}

/** Jeden otwarty dokument (karta). */
class DocTab(file0: File, private val app: AppState) {
    val id = tabSeq.incrementAndGet()
    /** Plik dokumentu; po „Zapisz jako” – nowe miejsce. */
    var file by mutableStateOf(file0)
        private set
    val name: String get() = file.name
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var status by mutableStateOf<TabStatus>(TabStatus.Loading(L("Otwieranie…", "Opening…")))
    var renderer by mutableStateOf<DesktopRenderer?>(null)
        private set
    /** Osobny renderer miniatur – nie kolejkują się przed stronami w widoku. */
    var thumbs by mutableStateOf<DesktopRenderer?>(null)
        private set
    var index by mutableStateOf(DocIndex.Empty)
        private set
    var text: TextIndex? = null
        private set
    var format: DocFormat = DocFormat.PDF
        private set
    /** Plik PDF, który jest wyświetlany (oryginał, wynik zamiany formatu albo kopia robocza). */
    var pdf: File? = null
        private set
    /** Zmienia się po każdej zmianie pliku – widoki odświeżają tekst i obrazy stron. */
    var revision by mutableStateOf(0)
        private set
    private val workDir = File(AppDirs.work, "tab-$id-${System.nanoTime()}")
    /** Kopia robocza w katalogu karty (tworzona przy pierwszej zmianie). */
    private var working: File? = null

    var layout by mutableStateOf(app.settings.layout)
    var flow by mutableStateOf(app.settings.flow)
    var rtl by mutableStateOf(app.settings.rtl)
    var zoomMode by mutableStateOf(ZoomMode.FIT_WIDTH)
    /** Powiększenie: 1 = 100% (rozmiar rzeczywisty przy 96 dpi). */
    var zoom by mutableStateOf(1f)
    /** Obrót widoku (nie zmienia pliku): 0, 90, 180, 270. */
    var rotation by mutableStateOf(0)
    var page by mutableStateOf(0)
    var nav by mutableStateOf<NavRequest?>(null)
    /** Skala faktycznie użyta przez widok (px na punkt) – do przeliczeń powiększenia. */
    var effectiveZoom by mutableStateOf(1f)

    val list = LazyListState()
    val hscroll = ScrollState(0)

    // Wyszukiwanie
    var query by mutableStateOf("")
    val hits = mutableStateListOf<SearchHit>()
    var hitIndex by mutableStateOf(-1)
    var searching by mutableStateOf(false)
    var searchProgress by mutableStateOf(0f)
    var searchedFor by mutableStateOf("")
    private var searchJob: Job? = null

    var selection by mutableStateOf<TextSelection?>(null)
    val pageTexts = HashMap<Int, PageText>()

    // Edycja
    /** Elementy narysowane, a jeszcze niezapisane w pliku. */
    var marks by mutableStateOf<List<Mark>>(emptyList())
        private set
    var dirty by mutableStateOf(false)
        private set
    /** Trwająca operacja na pliku (etykieta) – blokuje kolejne. */
    var busy by mutableStateOf<String?>(null)
        private set
    var protection by mutableStateOf<Protection?>(null)
    /** Widok porządkowania stron zamiast czytania. */
    var organize by mutableStateOf(false)
    /** Strony zaznaczone w widoku porządkowania. */
    val picked = mutableStateListOf<Int>()
    private val undoStack = ArrayList<Change>()
    private val redoStack = ArrayList<Change>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var snapCounter = 0
    private var markSeq = System.currentTimeMillis()

    val pageCount get() = index.pageCount
    val ready get() = status == TabStatus.Ready
    val editable get() = ready && busy == null
    /** Czy „Zapisz” może zastąpić oryginał (inne formaty zapisuje się jako nowy PDF). */
    val savesInPlace get() = format == DocFormat.PDF
    /** Czy są zmiany do zapisania (zmieniony plik, oczekujące elementy albo nowe hasło). */
    val unsaved get() = dirty || marks.isNotEmpty()

    fun newMarkId() = ++markSeq

    /** Szerokość wyświetlanej strony w punktach. */
    fun pageWidthPt(p: Int): Float = renderer?.sizes?.getOrNull(p)?.width ?: 612f

    fun pageHeightPt(p: Int): Float = renderer?.sizes?.getOrNull(p)?.height ?: 792f

    fun start(password: String? = null) {
        status = TabStatus.Loading(L("Otwieranie…", "Opening…"))
        scope.launch {
            try {
                load(password)
                status = TabStatus.Ready
                val r = app.settings.recentFor(file.absolutePath)
                if (app.settings.rememberPosition && r != null && r.page in 1 until pageCount) goTo(r.page)
                app.settings.touchRecent(file.absolutePath, page, pageCount)
                app.saveSoon()
                saveThumb()
            } catch (_: InvalidPasswordException) {
                status = TabStatus.NeedPassword(password != null)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                status = TabStatus.Failed(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun load(password: String?) {
        if (!file.isFile) error(L("Plik nie istnieje albo jest niedostępny", "The file does not exist or is not accessible"))
        val path = file.toOkioPath()
        format = withContext(Dispatchers.IO) { detectFormat(path, name) }
        val source = if (format == DocFormat.PDF) {
            if (password == null) {
                // Sprawdzenie hasła przed otwarciem renderera.
                withContext(Dispatchers.IO) { DesktopPdfEngine.load(file).close() }
                file
            } else {
                val out = withContext(Dispatchers.IO) {
                    workDir.mkdirs()
                    val out = File(workDir, "work.pdf")
                    app.engine.prepareWorking(path, password, out.toOkioPath())
                    out
                }
                working = out
                // Zapis zachowuje szyfrowanie tym samym hasłem.
                protection = Protection(password, password)
                out
            }
        } else {
            convert()
        }
        pdf = source
        status = TabStatus.Loading(L("Wczytywanie stron…", "Loading pages…"))
        open(source)
        if (index.pageCount == 0) error(L("Dokument nie ma stron", "The document has no pages"))
    }

    /** Otwiera (albo ponownie otwiera po zmianie) renderer, indeks i tekst pliku [source]. */
    private suspend fun open(source: File) {
        val (idx, _) = withContext(Dispatchers.IO) { app.engine.buildIndex(source.toOkioPath(), source.toOkioPath()) }
        val r = DesktopRenderer.open(source)
        val th = runCatching { DesktopRenderer.open(source) }.getOrNull()
        val oldR = renderer
        val oldT = thumbs
        val oldText = text
        PageCache.dropTab(id)
        renderer = r
        thumbs = th
        index = idx
        text = TextIndex(app.engine, source.toOkioPath(), idx.geos)
        pageTexts.clear()
        selection = null
        revision++
        oldR?.close()
        oldT?.close()
        if (oldText != null) withContext(Dispatchers.IO) { oldText.close() }
        if (pageCount > 0 && page >= pageCount) page = pageCount - 1
        picked.removeAll { it >= pageCount }
    }

    /** Inny format → PDF; wynik trafia do pamięci podręcznej, więc kolejne otwarcie jest natychmiastowe. */
    private suspend fun convert(): File {
        val key = withContext(Dispatchers.IO) {
            val md = MessageDigest.getInstance("SHA-1")
            md.update("${file.absolutePath}|${file.length()}|${file.lastModified()}|v1".toByteArray())
            md.digest().joinToString("") { "%02x".format(it) }
        }
        val cached = File(AppDirs.cache, "$key.pdf")
        if (cached.isFile && cached.length() > 0) {
            cached.setLastModified(System.currentTimeMillis())
            return cached
        }
        status = TabStatus.Loading(L("Przygotowanie dokumentu…", "Preparing the document…"))
        val dir = File(workDir, "convert").apply { mkdirs() }
        val copy = File(dir, "source")
        withContext(Dispatchers.IO) { file.copyTo(copy, overwrite = true) }
        val out = convertToPdf(app.engine, dir.toOkioPath(), copy.toOkioPath(), format, name) { b ->
            status = TabStatus.Loading(b.label, b.progress)
        }
        withContext(Dispatchers.IO) {
            val tmp = File(AppDirs.cache, "$key.tmp")
            out.toFile().copyTo(tmp, overwrite = true)
            tmp.renameTo(cached)
            out.toFile().delete()
            pruneCache()
        }
        return cached
    }

    private fun pruneCache() {
        val files = AppDirs.cache.listFiles { f -> f.extension == "pdf" }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        for (f in files) {
            total += f.length()
            if (total > 1_500L * 1024 * 1024) f.delete()
        }
    }

    private fun saveThumb() {
        val r = renderer ?: return
        val out = thumbFile(file.absolutePath)
        scope.launch(Dispatchers.IO) {
            val s = r.sizes.firstOrNull() ?: return@launch
            val w = 220
            val img = r.renderAwt(0, w, (w / s.aspect).toInt().coerceIn(1, 600)) ?: return@launch
            runCatching { ImageIO.write(img, "png", out) }
            withContext(Dispatchers.Main) { app.thumbVersion++ }
        }
    }

    fun goTo(p: Int, yFrac: Float? = null, animate: Boolean = false) {
        if (pageCount == 0) return
        val target = p.coerceIn(0, pageCount - 1)
        page = target
        nav = NavRequest(target, yFrac, animate = animate)
    }

    /** Strony w wierszach widoku (rozkładówki w trybie dwustronicowym). */
    fun rows(): List<IntArray> = buildRows(pageCount, layout)

    fun rowOf(p: Int): Int = when (layout) {
        PageLayout.SINGLE -> p
        PageLayout.SPREAD -> p / 2
        PageLayout.BOOK -> if (p == 0) 0 else (p + 1) / 2
    }

    /** Krok przewracania: o rozkładówkę w trybie dwustronicowym. */
    fun step(dir: Int) {
        val rows = rows()
        if (rows.isEmpty()) return
        val r = (rowOf(page) + dir).coerceIn(0, rows.size - 1)
        goTo(rows[r].first(), animate = app.settings.smoothScroll)
    }

    // ------------------------------------------------------------------ wyszukiwanie

    fun search(q: String) {
        searchJob?.cancel()
        hits.clear()
        hitIndex = -1
        searchedFor = q.trim()
        val t = text ?: return
        if (q.isBlank()) {
            searching = false
            return
        }
        searching = true
        searchProgress = 0f
        searchJob = scope.launch {
            try {
                t.search(q, onProgress = { p -> scope.launch { searchProgress = p } }) { hit ->
                    scope.launch {
                        hits += hit
                        if (hitIndex < 0) selectHit(0)
                    }
                }
            } finally {
                searching = false
            }
        }
    }

    fun selectHit(i: Int) {
        if (hits.isEmpty()) return
        hitIndex = ((i % hits.size) + hits.size) % hits.size
        val h = hits[hitIndex]
        goTo(h.page, h.rects.firstOrNull()?.top)
    }

    fun clearSearch() {
        searchJob?.cancel()
        hits.clear()
        hitIndex = -1
        searchedFor = ""
        searching = false
    }

    suspend fun pageText(p: Int): PageText? = pageTexts[p] ?: text?.page(p)?.also { pageTexts[p] = it }

    // ------------------------------------------------------------------ zakładki

    val bookmarks get() = app.settings.bookmarksFor(file.absolutePath)

    fun isBookmarked(p: Int) = bookmarks.any { it.page == p }

    fun toggleBookmark(p: Int = page) {
        val list = bookmarks
        if (list.any { it.page == p }) {
            app.settings.setBookmarks(file.absolutePath, list.filter { it.page != p })
            app.showToast(L("Usunięto zakładkę", "Bookmark removed"))
        } else {
            val title = index.outline.lastOrNull { (it.page ?: -1) in 0..p }?.title?.takeIf { it.isNotBlank() }
                ?: L("Strona {0}", "Page {0}").replace("{0}", (p + 1).toString())
            app.settings.setBookmarks(file.absolutePath, list + Bookmark(p, title))
            app.showToast(L("Dodano zakładkę na stronie {0}", "Bookmark added on page {0}").replace("{0}", (p + 1).toString()))
        }
        app.saveSoon()
    }

    fun renameBookmark(b: Bookmark, title: String) {
        app.settings.setBookmarks(file.absolutePath, bookmarks.map { if (it == b) it.copy(title = title.ifBlank { b.title }) else it })
        app.saveSoon()
    }

    fun removeBookmark(b: Bookmark) {
        app.settings.setBookmarks(file.absolutePath, bookmarks.filter { it != b })
        app.saveSoon()
    }

    // ------------------------------------------------------------------ elementy oczekujące i historia

    private fun refreshHistory() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun pushUndo(c: Change) {
        undoStack += c
        // Najwyżej 30 migawek pliku – najstarsze są usuwane z dysku.
        val files = undoStack.filterIsInstance<Change.FileState>()
        if (files.size > 30) {
            val oldest = files.first()
            undoStack.remove(oldest)
            oldest.snapshot.delete()
        }
        redoStack.forEach { if (it is Change.FileState) it.snapshot.delete() }
        redoStack.clear()
        refreshHistory()
    }

    private fun clearHistory() {
        (undoStack + redoStack).forEach { if (it is Change.FileState) it.snapshot.delete() }
        undoStack.clear()
        redoStack.clear()
        refreshHistory()
    }

    fun addMark(m: Mark) {
        pushUndo(Change.Marks(marks))
        marks = marks + m
    }

    fun removeMark(id: Long) {
        if (marks.none { it.id == id }) return
        pushUndo(Change.Marks(marks))
        marks = marks.filter { it.id != id }
    }

    fun clearMarks() {
        if (marks.isEmpty()) return
        pushUndo(Change.Marks(marks))
        marks = emptyList()
    }

    fun undo() = historyStep(undoStack, redoStack, undo = true)

    fun redo() = historyStep(redoStack, undoStack, undo = false)

    private fun historyStep(from: ArrayList<Change>, to: ArrayList<Change>, undo: Boolean) {
        if (busy != null) return
        val c = from.removeLastOrNull() ?: return
        when (c) {
            is Change.Marks -> {
                to += Change.Marks(marks)
                marks = c.before
                refreshHistory()
            }
            is Change.FileState -> {
                val w = working ?: return
                busy = (if (undo) L("Cofanie: {0}", "Undo: {0}") else L("Ponawianie: {0}", "Redo: {0}")).replace("{0}", c.label)
                scope.launch {
                    try {
                        val snap = snapshot(w)
                        to += Change.FileState(snap, marks, c.label)
                        withContext(Dispatchers.IO) { c.snapshot.copyTo(w, overwrite = true) }
                        c.snapshot.delete()
                        marks = c.marks
                        dirty = true
                        open(w)
                    } catch (e: Exception) {
                        app.showToast(L("Nie udało się: {0}", "Failed: {0}").replace("{0}", e.message ?: ""), error = true)
                    } finally {
                        busy = null
                        refreshHistory()
                    }
                }
            }
        }
    }

    private suspend fun snapshot(w: File): File = withContext(Dispatchers.IO) {
        val dir = File(workDir, "history").apply { mkdirs() }
        val f = File(dir, "s${snapCounter++}.pdf")
        w.copyTo(f, overwrite = true)
        f
    }

    /** Kopia robocza (przy pierwszej zmianie – kopia wyświetlanego pliku w katalogu karty). */
    private suspend fun ensureWorking(): File {
        working?.let { return it }
        val src = pdf ?: error(L("Dokument nie jest otwarty", "The document is not open"))
        val w = withContext(Dispatchers.IO) {
            workDir.mkdirs()
            val w = File(workDir, "work.pdf")
            src.copyTo(w, overwrite = true)
            w
        }
        working = w
        pdf = w
        return w
    }

    /**
     * Zmiana pliku: kopia robocza, migawka do cofnięcia, zapis oczekujących elementów, operacja [op]
     * i ponowne wczytanie dokumentu. Zwraca true, gdy się udało.
     */
    suspend fun mutate(label: String, commit: Boolean = true, op: (File) -> Unit): Boolean {
        if (busy != null || !ready) return false
        busy = "$label…"
        try {
            val w = ensureWorking()
            val snap = snapshot(w)
            val before = marks
            return try {
                withContext(Dispatchers.IO) {
                    if (commit && before.isNotEmpty()) PdfOps.commitMarks(w, before, app.settings.author)
                    op(w)
                }
                pushUndo(Change.FileState(snap, before, label))
                if (commit) marks = emptyList()
                dirty = true
                open(w)
                true
            } catch (e: Throwable) {
                // Plik roboczy wraca do stanu sprzed operacji.
                runCatching { withContext(Dispatchers.IO) { snap.copyTo(w, overwrite = true) } }
                snap.delete()
                if (e is kotlinx.coroutines.CancellationException) throw e
                app.showToast(L("{0}: nie udało się ({1})", "{0}: failed ({1})").replace("{0}", label).replace("{1}", e.message ?: e.javaClass.simpleName), error = true)
                runCatching { open(w) }
                false
            }
        } finally {
            busy = null
        }
    }

    fun launchMutate(label: String, commit: Boolean = true, after: () -> Unit = {}, op: (File) -> Unit) {
        scope.launch { if (mutate(label, commit, op)) after() }
    }

    // ------------------------------------------------------------------ operacje

    fun commitMarks() = launchMutate(L("Zapisywanie adnotacji w dokumencie", "Writing annotations into the document")) { }

    fun deleteAnnotation(a: AnnotInfo) = launchMutate(L("Usuwanie adnotacji", "Deleting annotation"), commit = false) { PdfOps.deleteAnnotations(it, listOf(a)) }

    fun deleteAllAnnotations() = launchMutate(L("Usuwanie adnotacji", "Deleting annotations"), commit = false) { PdfOps.deleteAllAnnotations(it) }

    fun rotatePages(pages: Collection<Int>, degrees: Int) =
        launchMutate(if (degrees > 0) L("Obracanie w prawo", "Rotating right") else L("Obracanie w lewo", "Rotating left")) { PdfOps.rotatePages(it, pages, degrees) }

    fun deletePages(pages: Collection<Int>) {
        if (pages.size >= pageCount) {
            app.showToast(L("Dokument musi mieć co najmniej jedną stronę", "The document must keep at least one page"), error = true)
            return
        }
        launchMutate(L("Usuwanie stron", "Deleting pages"), after = { picked.clear() }) { PdfOps.deletePages(it, pages) }
    }

    fun duplicatePages(pages: Collection<Int>) = launchMutate(L("Powielanie stron", "Duplicating pages")) { PdfOps.duplicatePages(it, pages) }

    /** Przesuwa zaznaczone strony o [dir] pozycji (w lewo/prawo); zaznaczenie podąża za nimi. */
    fun shiftPages(pages: List<Int>, dir: Int) {
        if (pages.isEmpty()) return
        val sorted = pages.sorted()
        val target = (sorted.first() + dir).coerceIn(0, pageCount - sorted.size)
        if (target == sorted.first()) return
        launchMutate(L("Przenoszenie stron", "Moving pages"), after = {
            picked.clear()
            picked.addAll(target until target + sorted.size)
        }) { PdfOps.movePages(it, sorted, target) }
    }

    fun movePagesTo(pages: List<Int>, target: Int) {
        if (pages.isEmpty()) return
        val sorted = pages.sorted()
        val before = sorted.count { it < target }
        val at = (target - before).coerceIn(0, pageCount - sorted.size)
        launchMutate(L("Przenoszenie stron", "Moving pages"), after = {
            picked.clear()
            picked.addAll(at until at + sorted.size)
        }) { PdfOps.movePages(it, sorted, at) }
    }

    fun insertBlank(at: Int) = launchMutate(L("Wstawianie pustej strony", "Inserting a blank page")) { PdfOps.insertBlank(it, at) }

    fun insertPdfs(files: List<File>, at: Int) = launchMutate(L("Wstawianie stron", "Inserting pages")) { w ->
        var pos = at
        for (f in files) {
            val before = DesktopPdfEngine.load(w).use { it.numberOfPages }
            PdfOps.insertPdf(w, f, null, pos)
            pos += DesktopPdfEngine.load(w).use { it.numberOfPages } - before
        }
    }

    fun insertImages(files: List<File>, at: Int) = launchMutate(L("Wstawianie obrazów", "Inserting images")) { PdfOps.insertImages(it, files, at) }

    fun fillFields(edits: List<FieldEdit>) = launchMutate(L("Wypełnianie formularza", "Filling in the form")) { w ->
        val failed = PdfOps.fillFields(w, edits)
        if (failed.isNotEmpty()) scope.launch { app.showToast(L("Nie wypełniono pól: {0}", "Fields not filled: {0}").replace("{0}", failed.joinToString(", ")), error = true) }
    }

    fun flattenForm() = launchMutate(L("Spłaszczanie formularza", "Flattening the form")) { PdfOps.flattenForm(it) }

    fun setInfo(e: InfoEdit) = launchMutate(L("Zapisywanie właściwości", "Saving properties")) { PdfOps.setInfo(it, e) }

    /** Kompresja obrazów; po zakończeniu pokazuje rozmiar przed i po. */
    fun compress(maxSide: Int, quality: Float) {
        scope.launch {
            val before = (working ?: pdf)?.length() ?: 0L
            var count = 0
            val ok = mutate(L("Zmniejszanie rozmiaru", "Reducing size")) { w ->
                val tmp = File(w.parentFile, "compressed.pdf")
                count = PdfOps.compress(w, tmp, maxSide, quality) { }
                PdfOps.replace(tmp, w)
            }
            if (ok) {
                val after = working?.length() ?: 0L
                app.showToast(
                    if (count == 0) L("Nie znaleziono obrazów do zmniejszenia", "No images to shrink were found")
                    else L("Zmniejszono {0} obrazów: {1} → {2}", "Shrunk {0} images: {1} → {2}")
                        .replace("{0}", count.toString()).replace("{1}", humanBytes(before)).replace("{2}", humanBytes(after)),
                )
            }
        }
    }

    /** Zapisuje wybrane strony w nowym pliku. */
    fun extractPages(pages: Collection<Int>, out: File, openAfter: Boolean) {
        scope.launch {
            busy = L("Wyodrębnianie stron…", "Extracting pages…")
            try {
                val src = commitPendingCopy()
                withContext(Dispatchers.IO) { PdfOps.extractPages(src, pages, out) }
                app.showToast(L("Zapisano {0}", "Saved {0}").replace("{0}", out.name))
                if (openAfter) app.open(listOf(out))
            } catch (e: Exception) {
                app.showToast(L("Nie udało się wyodrębnić stron", "Could not extract pages") + ": " + (e.message ?: ""), error = true)
            } finally {
                busy = null
            }
        }
    }

    /** Plik z bieżącą zawartością (z oczekującymi elementami) – kopia tymczasowa, bez zmiany karty. */
    private suspend fun commitPendingCopy(): File {
        val src = working ?: pdf ?: error("no pdf")
        if (marks.isEmpty()) return src
        return withContext(Dispatchers.IO) {
            workDir.mkdirs()
            val f = File(workDir, "export-${System.nanoTime()}.pdf")
            src.copyTo(f, overwrite = true)
            PdfOps.commitMarks(f, marks, app.settings.author)
            f
        }
    }

    /** Bieżący stan dokumentu do druku i eksportu (z oczekującymi elementami). */
    suspend fun currentFile(): File = commitPendingCopy()

    fun setProtection(p: Protection?) {
        protection = p
        dirty = true
    }

    // ------------------------------------------------------------------ zapis

    /** Zapisuje w miejscu oryginału (PDF) albo – dla innych formatów – pyta o nowy plik. */
    suspend fun saveTo(target: File): Boolean {
        if (busy != null) return false
        busy = L("Zapisywanie…", "Saving…")
        try {
            val w = ensureWorking()
            // Renderer nie może trzymać otwartego oryginału, który zaraz zostanie zastąpiony.
            if (renderer?.file != w.toOkioPath()) open(w)
            val pending = marks
            withContext(Dispatchers.IO) {
                if (pending.isNotEmpty()) PdfOps.commitMarks(w, pending, app.settings.author)
                val p = protection
                if (p != null) {
                    PdfOps.export(w, target, p.user, p.owner)
                } else {
                    val tmp = File(target.parentFile, target.name + ".part")
                    w.copyTo(tmp, overwrite = true)
                    PdfOps.replace(tmp, target)
                }
            }
            marks = emptyList()
            clearHistory()
            open(w)
            val moved = !target.absolutePath.equals(file.absolutePath, ignoreCase = true)
            if (moved) {
                file = target
                format = DocFormat.PDF
            }
            dirty = false
            app.settings.touchRecent(file.absolutePath, page, pageCount)
            app.saveSoon()
            saveThumb()
            app.showToast(L("Zapisano {0}", "Saved {0}").replace("{0}", target.name))
            return true
        } catch (e: Exception) {
            app.showToast(L("Nie udało się zapisać", "Could not save") + ": " + (e.message ?: e.javaClass.simpleName), error = true)
            return false
        } finally {
            busy = null
        }
    }

    fun close() {
        scope.cancel()
        renderer?.close()
        thumbs?.close()
        PageCache.dropTab(id)
        val t = text
        if (t != null) CoroutineScope(Dispatchers.IO).launch { t.close() }
        CoroutineScope(Dispatchers.IO).launch { runCatching { workDir.deleteRecursively() } }
    }

    companion object {
        fun thumbFile(path: String): File {
            val md = MessageDigest.getInstance("SHA-1").digest(path.lowercase().toByteArray())
            return File(AppDirs.thumbs, md.joinToString("") { "%02x".format(it) } + ".png")
        }
    }
}

fun humanBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.1f MB".format(b / (1024.0 * 1024))
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

fun buildRows(count: Int, layout: PageLayout): List<IntArray> = when (layout) {
    PageLayout.SINGLE -> List(count) { intArrayOf(it) }
    PageLayout.SPREAD -> (0 until count step 2).map { if (it + 1 < count) intArrayOf(it, it + 1) else intArrayOf(it) }
    PageLayout.BOOK -> buildList {
        if (count > 0) add(intArrayOf(0))
        var i = 1
        while (i < count) {
            add(if (i + 1 < count) intArrayOf(i, i + 1) else intArrayOf(i))
            i += 2
        }
    }
}
