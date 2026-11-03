package com.blacksingularities.openpdf.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openpdf.desktop.engine.DesktopPdfEngine
import com.blacksingularities.openpdf.desktop.engine.DesktopRenderer
import com.blacksingularities.openpdf.desktop.ui.L
import com.blacksingularities.openpdf.doc.DocFormat
import com.blacksingularities.openpdf.pdf.DocIndex
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

/** Jeden otwarty dokument (karta). */
class DocTab(val file: File, private val app: AppState) {
    val id = tabSeq.incrementAndGet()
    val name: String = file.name
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
    /** Plik PDF, który jest wyświetlany (oryginał albo wynik zamiany formatu). */
    var pdf: File? = null
        private set
    private val workDir = File(AppDirs.work, "tab-$id-${System.nanoTime()}")

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

    val pageCount get() = index.pageCount
    val ready get() = status == TabStatus.Ready

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
                withContext(Dispatchers.IO) {
                    workDir.mkdirs()
                    val out = File(workDir, "unlocked.pdf")
                    app.engine.prepareWorking(path, password, out.toOkioPath())
                    out
                }
            }
        } else {
            convert()
        }
        pdf = source
        status = TabStatus.Loading(L("Wczytywanie stron…", "Loading pages…"))
        val (idx, _) = withContext(Dispatchers.IO) { app.engine.buildIndex(source.toOkioPath(), source.toOkioPath()) }
        if (idx.pageCount == 0) error(L("Dokument nie ma stron", "The document has no pages"))
        renderer = DesktopRenderer.open(source)
        thumbs = runCatching { DesktopRenderer.open(source) }.getOrNull()
        index = idx
        text = TextIndex(app.engine, source.toOkioPath(), idx.geos)
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
        val r = (rowOf(page) + dir).coerceIn(0, rows.size - 1)
        goTo(rows[r].first(), animate = app.settings.smoothScroll)
    }

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

    fun close() {
        scope.cancel()
        renderer?.close()
        thumbs?.close()
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
