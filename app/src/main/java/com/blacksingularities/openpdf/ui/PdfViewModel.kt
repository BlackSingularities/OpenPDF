package com.blacksingularities.openpdf.ui

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksingularities.openpdf.data.AppSettings
import com.blacksingularities.openpdf.data.Library
import com.blacksingularities.openpdf.data.RecentDoc
import com.blacksingularities.openpdf.data.SavedSignature
import com.blacksingularities.openpdf.data.SettingsStore
import com.blacksingularities.openpdf.data.UserBookmark
import com.blacksingularities.openpdf.pdf.AnnotInfo
import com.blacksingularities.openpdf.pdf.DocIndex
import com.blacksingularities.openpdf.pdf.FieldEdit
import com.blacksingularities.openpdf.pdf.FieldInfo
import com.blacksingularities.openpdf.pdf.FieldKind
import com.blacksingularities.openpdf.pdf.ImageMark
import com.blacksingularities.openpdf.pdf.InfoEdit
import com.blacksingularities.openpdf.pdf.InkMark
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.MarkupMark
import com.blacksingularities.openpdf.pdf.NoteMark
import com.blacksingularities.openpdf.pdf.PageImages
import com.blacksingularities.openpdf.pdf.PageText
import com.blacksingularities.openpdf.pdf.PdfOps
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.RectN
import com.blacksingularities.openpdf.pdf.RenderSession
import com.blacksingularities.openpdf.pdf.SearchHit
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.pdf.ShapeMark
import com.blacksingularities.openpdf.pdf.SignatureMark
import com.blacksingularities.openpdf.pdf.TextIndex
import com.blacksingularities.openpdf.pdf.TextMark
import com.blacksingularities.openpdf.pdf.resizable
import com.blacksingularities.openpdf.pdf.resized
import com.blacksingularities.openpdf.pdf.simplify
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.update.UpdateManager
import com.blacksingularities.openpdf.update.UpdateState
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

class PdfViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val resolver get() = ctx.contentResolver
    private val store = SettingsStore(app)
    val library = Library(app)

    var settings by mutableStateOf(store.load())
        private set

    fun updateSettings(t: (AppSettings) -> AppSettings) {
        settings = t(settings)
        store.save(settings)
    }

    // ------------------------------------------------------------------ stan dokumentu

    var screen by mutableStateOf(Screen.HOME)
    var doc by mutableStateOf<OpenDoc?>(null)
        private set
    var render by mutableStateOf<RenderSession?>(null)
        private set
    var index by mutableStateOf(DocIndex.Empty)
        private set
    var revision by mutableIntStateOf(0)
        private set
    private var textIndex: TextIndex? = null
    val images = PageImages(viewModelScope)

    var busy by mutableStateOf<Busy?>(null)
    var message by mutableStateOf<String?>(null)
    var dirty by mutableStateOf(false)
        private set
    var protection by mutableStateOf<Protection?>(null)
    var currentPage by mutableIntStateOf(0)
        private set
    var jump by mutableStateOf<JumpRequest?>(null)
        private set
    var step by mutableStateOf<StepRequest?>(null)
        private set
    var chrome by mutableStateOf(true)
    var sheet by mutableStateOf<Sheet?>(null)
    var passwordRequest by mutableStateOf<PasswordRequest?>(null)
    var recents by mutableStateOf(library.recents())
        private set
    var bookmarks by mutableStateOf<List<UserBookmark>>(emptyList())
        private set

    /** Fold rozłożony z pionowym zawiasem albo w pozycji książki – sygnał dla trybu automatycznego. */
    var foldBook by mutableStateOf(false)

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()
    private var pendingSave: SaveTarget? = null

    fun toast(text: String) {
        message = text
    }

    // ------------------------------------------------------------------ elementy oczekujące i historia

    var marks by mutableStateOf<List<Mark>>(emptyList())
        private set
    private val undoStack = ArrayList<Change>()
    private val redoStack = ArrayList<Change>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var nextId = System.currentTimeMillis()
    private fun newId() = ++nextId

    private fun refreshHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun pushUndo(c: Change) {
        undoStack += c
        // Najwyżej 25 migawek pliku – najstarsze są usuwane z dysku.
        val files = undoStack.filterIsInstance<Change.FileState>()
        if (files.size > 25) {
            val oldest = files.first()
            undoStack.remove(oldest)
            oldest.snapshot.delete()
        }
        redoStack.forEach { if (it is Change.FileState) it.snapshot.delete() }
        redoStack.clear()
        refreshHistoryFlags()
    }

    private fun setMarks(new: List<Mark>, record: Boolean = true) {
        if (record) pushUndo(Change.Marks(marks))
        marks = new
        dirty = true
    }

    fun undo() = historyStep(undoStack, redoStack)
    fun redo() = historyStep(redoStack, undoStack)

    private fun historyStep(from: ArrayList<Change>, to: ArrayList<Change>) {
        val c = from.removeLastOrNull() ?: return
        selectedId = null
        when (c) {
            is Change.Marks -> {
                to += Change.Marks(marks)
                marks = c.before
                dirty = true
                refreshHistoryFlags()
            }
            is Change.FileState -> {
                val d = doc ?: return
                viewModelScope.launch {
                    busy = Busy(if (from === undoStack) "Cofanie: ${c.label}" else "Ponawianie: ${c.label}")
                    try {
                        val snap = snapshot(d)
                        to += Change.FileState(snap, marks, c.label)
                        withContext(Dispatchers.IO) { c.snapshot.copyTo(d.source, overwrite = true) }
                        c.snapshot.delete()
                        marks = c.marks
                        dirty = true
                        reload(d)
                    } catch (e: Exception) {
                        toast("Nie udało się: ${e.message}")
                    } finally {
                        busy = null
                        refreshHistoryFlags()
                    }
                }
            }
        }
    }

    private var snapCounter = 0

    private suspend fun snapshot(d: OpenDoc): File = withContext(Dispatchers.IO) {
        val dir = File(d.dir, "history").apply { mkdirs() }
        val f = File(dir, "s${snapCounter++}.pdf")
        d.source.copyTo(f, overwrite = true)
        f
    }

    /**
     * Zmiana pliku: migawka do cofnięcia, zapis oczekujących elementów w pliku, operacja [op]
     * na pliku roboczym i ponowne wczytanie dokumentu.
     */
    private suspend fun mutate(label: String, commit: Boolean = true, op: suspend (File) -> Unit): Boolean {
        val d = doc ?: return false
        busy = Busy("$label…")
        val snap = try {
            snapshot(d)
        } catch (e: Exception) {
            busy = null
            toast("Brak miejsca na kopię roboczą")
            return false
        }
        val before = marks
        return try {
            withContext(Dispatchers.IO) {
                if (commit && before.isNotEmpty()) PdfOps.commitMarks(d.source, before, settings.author, PdfOps.systemFonts())
                op(d.source)
            }
            pushUndo(Change.FileState(snap, before, label))
            if (commit) marks = emptyList()
            selectedId = null
            dirty = true
            reload(d)
            true
        } catch (e: Throwable) {
            // Plik roboczy wraca do stanu sprzed operacji.
            runCatching { withContext(Dispatchers.IO) { snap.copyTo(d.source, overwrite = true) } }
            snap.delete()
            toast("$label: nie udało się (${e.message ?: e.javaClass.simpleName})")
            false
        } finally {
            busy = null
        }
    }

    private fun launchMutate(label: String, commit: Boolean = true, op: suspend (File) -> Unit) {
        viewModelScope.launch { mutate(label, commit, op) }
    }

    // ------------------------------------------------------------------ otwieranie

    private val sessionsRoot get() = File(ctx.cacheDir, "sessions")

    private fun newSessionDir(): File = File(sessionsRoot, System.currentTimeMillis().toString()).apply { mkdirs() }

    private data class Meta(val name: String, val size: Long)

    private fun queryMeta(uri: Uri): Meta {
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Dokument.pdf"
        return Meta(name ?: fallback, size)
    }

    private fun checkWritable(uri: Uri): Boolean {
        if (uri.scheme == "file") return uri.path?.let { File(it).canWrite() } == true
        val persisted = runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            true
        }.getOrDefault(false)
        if (!persisted) runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (persisted) return true
        return runCatching { resolver.openFileDescriptor(uri, "rw")?.use { } != null }.getOrDefault(false)
    }

    /** Otwiera dokument z adresu (wybór pliku, inna aplikacja, lista ostatnich). */
    fun open(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            if (!confirmDiscardIfNeeded { open(uri, password) }) return@launch
            busy = Busy("Otwieranie…")
            val dir = newSessionDir()
            var name = "Dokument.pdf"
            try {
                val meta = withContext(Dispatchers.IO) { queryMeta(uri) }
                name = meta.name
                val original = File(dir, "original.pdf")
                withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("Brak dostępu do pliku")
                    input.use { i -> original.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
                }
                val writable = withContext(Dispatchers.IO) { checkWritable(uri) }
                openPrepared(dir, original, password, OpenDoc(uri, name, dir, File(dir, "work.pdf"), writable), meta.size)
            } catch (e: InvalidPasswordException) {
                dir.deleteRecursively()
                passwordRequest = PasswordRequest(uri, null, name, password != null, PasswordPurpose.OPEN)
            } catch (e: Throwable) {
                dir.deleteRecursively()
                toast("Nie udało się otworzyć pliku: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = null
            }
        }
    }

    /** Otwiera plik utworzony w aplikacji (nowy dokument, z obrazów) – zapis wymaga wskazania miejsca. */
    private suspend fun openLocal(file: File, name: String) {
        val dir = file.parentFile ?: return
        openPrepared(dir, file, null, OpenDoc(null, name, dir, File(dir, "work.pdf"), false), file.length())
        dirty = true
    }

    private suspend fun openPrepared(dir: File, original: File, password: String?, newDoc: OpenDoc, size: Long) {
        val encrypted = withContext(Dispatchers.IO) { PdfOps.prepareWorking(original, password, newDoc.source) }
        if (original != newDoc.source) original.delete()
        closeDocument()
        doc = newDoc
        protection = if (encrypted && !password.isNullOrEmpty()) Protection(password, password) else null
        reload(newDoc)
        dirty = false
        undoStack.clear()
        redoStack.clear()
        refreshHistoryFlags()
        tool = Tool.VIEW
        screen = Screen.VIEWER
        chrome = true
        val uri = newDoc.uri
        if (uri != null) {
            val previous = library.recents().firstOrNull { it.uri == uri.toString() }
            val last = previous?.lastPage?.coerceIn(0, max(0, index.pageCount - 1)) ?: 0
            library.touch(RecentDoc(uri.toString(), newDoc.name, size, System.currentTimeMillis(), index.pageCount, last, newDoc.writable))
            recents = library.recents()
            bookmarks = library.bookmarks(uri.toString())
            currentPage = last
            if (last > 0) jumpTo(last)
            saveThumbnail(uri.toString())
        } else {
            bookmarks = emptyList()
            currentPage = 0
        }
    }

    private suspend fun reload(d: OpenDoc) {
        val rev = revision + 1
        val display = File(d.dir, "display-$rev.pdf")
        val (idx, needsDisplay) = withContext(Dispatchers.IO) { PdfOps.buildIndex(d.source, display) }
        val target = if (needsDisplay) display else d.source
        val session = try {
            RenderSession.open(target)
        } catch (e: Throwable) {
            // Plik, którego systemowy renderer nie rozumie – zapis przez PdfBox zwykle go naprawia.
            val fixed = File(d.dir, "fixed-$rev.pdf")
            withContext(Dispatchers.IO) { PdfOps.rewrite(target, fixed) }
            RenderSession.open(fixed)
        }
        val old = render
        val geos = if (idx.geos.size == session.pageCount) idx.geos else {
            List(session.pageCount) { i ->
                idx.geos.getOrNull(i) ?: com.blacksingularities.openpdf.pdf.PageGeo(0f, 0f, session.sizes[i].width, session.sizes[i].height, 0)
            }
        }
        index = idx.copy(geos = geos)
        render = session
        images.attach(session)
        revision = rev
        old?.close()
        textIndex?.close()
        textIndex = TextIndex(d.source, geos)
        selection = null
        // Stare kopie do wyświetlania nie są już potrzebne.
        withContext(Dispatchers.IO) {
            d.dir.listFiles()?.forEach { f ->
                if ((f.name.startsWith("display-") || f.name.startsWith("fixed-")) && f != session.file) {
                    if (old == null || f != old.file || old.closed) f.delete()
                }
            }
        }
        if (searchQuery.isNotBlank() && searchHits.isNotEmpty()) search(searchQuery)
    }

    private fun saveThumbnail(uri: String) {
        viewModelScope.launch {
            val s = render ?: return@launch
            val bmp = s.renderPage(0, 240) ?: return@launch
            withContext(Dispatchers.IO) {
                runCatching { library.thumbFile(uri).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
            }
            recents = library.recents()
            recentsThumbVersion++
        }
    }

    var recentsThumbVersion by mutableIntStateOf(0)
        private set

    fun forgetRecent(uri: String) {
        library.remove(uri)
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        recents = library.recents()
    }

    fun clearRecents() {
        library.clearRecents()
        recents = emptyList()
    }

    /** Pytanie o porzucenie niezapisanych zmian przed otwarciem innego dokumentu. */
    var discardRequest by mutableStateOf<(() -> Unit)?>(null)

    private fun confirmDiscardIfNeeded(retry: () -> Unit): Boolean {
        if (doc == null || (!dirty && marks.isEmpty())) return true
        discardRequest = {
            dirty = false
            marks = emptyList()
            retry()
        }
        return false
    }

    fun closeDocument() {
        searchJob?.cancel()
        val r = render
        render = null
        images.attach(null)
        r?.close()
        val t = textIndex
        textIndex = null
        viewModelScope.launch { t?.close() }
        val d = doc
        doc = null
        if (d != null) {
            val dir = d.dir
            viewModelScope.launch(Dispatchers.IO) {
                delay(1500)
                runCatching { dir.deleteRecursively() }
            }
        }
        index = DocIndex.Empty
        marks = emptyList()
        undoStack.clear()
        redoStack.clear()
        refreshHistoryFlags()
        dirty = false
        selection = null
        searchHits.clear()
        searchQuery = ""
        searchActive = false
        selectedId = null
        sheet = null
        protection = null
        tool = Tool.VIEW
    }

    /** Wyjście do ekranu głównego (z pytaniem o niezapisane zmiany). */
    fun exitToHome(force: Boolean = false) {
        if (!force && (dirty || marks.isNotEmpty())) {
            discardRequest = { exitToHome(force = true) }
            return
        }
        closeDocument()
        screen = Screen.HOME
        recents = library.recents()
    }

    fun submitPassword(password: String) {
        val req = passwordRequest ?: return
        passwordRequest = null
        when (req.purpose) {
            PasswordPurpose.OPEN -> req.uri?.let { open(it, password) }
            PasswordPurpose.INSERT -> req.uri?.let { insertPdf(it, pendingInsertAt, password) }
        }
    }

    // ------------------------------------------------------------------ tworzenie dokumentów

    fun createBlank(pages: Int, landscape: Boolean) {
        viewModelScope.launch {
            if (!confirmDiscardIfNeeded { createBlank(pages, landscape) }) return@launch
            busy = Busy("Tworzenie dokumentu…")
            try {
                val dir = newSessionDir()
                val f = File(dir, "new.pdf")
                val a4 = PDRectangle.A4
                val size = if (landscape) PDRectangle(a4.height, a4.width) else a4
                withContext(Dispatchers.IO) { PdfOps.createBlank(f, pages, size) }
                openLocal(f, "Nowy dokument.pdf")
            } catch (e: Throwable) {
                toast("Nie udało się utworzyć dokumentu: ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    fun createFromImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            if (!confirmDiscardIfNeeded { createFromImages(uris) }) return@launch
            busy = Busy("Tworzenie PDF z ${uris.size} obrazów…")
            try {
                val dir = newSessionDir()
                val files = withContext(Dispatchers.IO) { copyImages(uris, dir) }
                val f = File(dir, "images.pdf")
                withContext(Dispatchers.IO) { PdfOps.createFromImages(files, f) }
                files.forEach { it.delete() }
                openLocal(f, "Obrazy.pdf")
            } catch (e: Throwable) {
                toast("Nie udało się utworzyć PDF: ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    /** Scala wiele plików PDF w nowy dokument. */
    fun mergeFiles(uris: List<Uri>) {
        if (uris.size < 2) {
            uris.firstOrNull()?.let { open(it) }
            return
        }
        viewModelScope.launch {
            if (!confirmDiscardIfNeeded { mergeFiles(uris) }) return@launch
            busy = Busy("Scalanie ${uris.size} plików…")
            try {
                val dir = newSessionDir()
                val first = File(dir, "merge-0.pdf")
                withContext(Dispatchers.IO) { copyUri(uris[0], first) }
                for ((i, u) in uris.drop(1).withIndex()) {
                    busy = Busy("Scalanie ${i + 2} z ${uris.size}…", (i + 1f) / uris.size)
                    val f = File(dir, "merge-${i + 1}.pdf")
                    withContext(Dispatchers.IO) {
                        copyUri(u, f)
                        PdfOps.insertPdf(first, f, null, null)
                        f.delete()
                    }
                }
                openLocal(first, "Scalony dokument.pdf")
            } catch (e: InvalidPasswordException) {
                toast("Jeden z plików jest chroniony hasłem – otwórz go i zapisz bez hasła przed scaleniem")
            } catch (e: Throwable) {
                toast("Scalanie nie powiodło się: ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    private fun copyUri(uri: Uri, out: File) {
        val input = resolver.openInputStream(uri) ?: error("Brak dostępu do pliku")
        input.use { i -> out.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
    }

    private fun copyImages(uris: List<Uri>, dir: File): List<File> = uris.mapIndexedNotNull { i, u ->
        runCatching {
            val f = File(dir, "img-$i")
            copyUri(u, f)
            f
        }.getOrNull()
    }

    // ------------------------------------------------------------------ nawigacja

    fun jumpTo(page: Int, region: RectN? = null) {
        val n = index.pageCount
        if (n == 0) return
        jump = JumpRequest(page.coerceIn(0, n - 1), region, System.nanoTime())
    }

    fun pageStep(direction: Int) {
        step = StepRequest(direction, System.nanoTime())
    }

    private var lastSavedPage = -1

    fun reportPage(page: Int) {
        if (page == currentPage && page == lastSavedPage) return
        currentPage = page
        val uri = doc?.uri?.toString() ?: return
        if (page != lastSavedPage) {
            lastSavedPage = page
            library.updateLastPage(uri, page)
        }
    }

    fun toggleBookmark() {
        val uri = doc?.uri?.toString() ?: run {
            toast("Zakładki działają dla zapisanych plików")
            return
        }
        val p = currentPage
        val list = if (bookmarks.any { it.page == p }) bookmarks.filter { it.page != p } else bookmarks + UserBookmark(p, "Strona ${p + 1}")
        library.setBookmarks(uri, list)
        bookmarks = library.bookmarks(uri)
        toast(if (list.any { it.page == p }) "Dodano zakładkę: strona ${p + 1}" else "Usunięto zakładkę")
    }

    fun removeBookmark(b: UserBookmark) {
        val uri = doc?.uri?.toString() ?: return
        library.setBookmarks(uri, bookmarks.filter { it != b })
        bookmarks = library.bookmarks(uri)
    }

    // ------------------------------------------------------------------ narzędzia

    var tool by mutableStateOf(Tool.VIEW)
        private set
    var shapeKind by mutableStateOf(ShapeKind.RECT)
    var markupKind by mutableStateOf(MarkupKind.HIGHLIGHT)
    var styles by mutableStateOf(
        mapOf(
            Tool.PEN to ToolStyle(0xFF111111.toInt(), 1.6f),
            Tool.HIGHLIGHTER to ToolStyle(0xFFFFEB3B.toInt(), 12f),
            Tool.SHAPE to ToolStyle(0xFFE53935.toInt(), 2f),
            Tool.TEXT to ToolStyle(0xFF111111.toInt(), 12f),
            Tool.NOTE to ToolStyle(0xFFFFD54F.toInt(), 0f),
            Tool.MARKUP to ToolStyle(0xFFFFEB3B.toInt(), 0f),
            Tool.SIGNATURE to ToolStyle(0xFF0D2A6B.toInt(), 1.6f),
        ),
    )

    fun style(t: Tool): ToolStyle = styles[t] ?: ToolStyle(0xFF111111.toInt(), 2f)

    fun setStyle(t: Tool, s: ToolStyle) {
        styles = styles + (t to s)
        // Zmiana koloru zaznaczonego elementu.
        val sel = marks.firstOrNull { it.id == selectedId } ?: return
        val updated: Mark = when (sel) {
            is InkMark -> sel.copy(color = s.color, widthPt = s.width)
            is ShapeMark -> sel.copy(color = s.color, widthPt = s.width)
            is TextMark -> sel.copy(color = s.color)
            is NoteMark -> sel.copy(color = s.color)
            is MarkupMark -> sel.copy(color = s.color)
            is SignatureMark -> sel.copy(color = s.color, widthPt = s.width)
            is ImageMark -> sel
        }
        setMarks(marks.map { if (it.id == sel.id) updated else it })
    }

    var editing by mutableStateOf(false)
        private set

    fun setEditing(on: Boolean) {
        editing = on
        tool = if (on) (lastEditTool ?: Tool.PEN) else Tool.VIEW
        selectedId = null
        selection = null
    }

    private var lastEditTool: Tool? = null

    fun selectTool(t: Tool) {
        tool = t
        if (t != Tool.VIEW) lastEditTool = t
        if (t != Tool.SELECT) selectedId = null
        selection = null
        if (t == Tool.SIGNATURE && library.signatures().isEmpty()) signaturePad = true
    }

    // ------------------------------------------------------------------ rysowanie (przeciąganie)

    var live by mutableStateOf<LiveStroke?>(null)
        private set
    var liveShape by mutableStateOf<ShapeMark?>(null)
        private set
    private var shapeStart: PointN? = null
    private val erasedPending = HashSet<Long>()
    private val erasedCommitted = LinkedHashSet<AnnotInfo>()
    var erasedPreview by mutableStateOf<Set<AnnotInfo>>(emptySet())
        private set
    private var marksBeforeErase: List<Mark>? = null

    private fun pageWidthPt(page: Int) = index.geos.getOrNull(page)?.displayWidth ?: 595f
    private fun pageHeightPt(page: Int) = index.geos.getOrNull(page)?.displayHeight ?: 842f

    fun dragStart(page: Int, p: PointN) {
        when (tool) {
            Tool.PEN, Tool.HIGHLIGHTER -> live = LiveStroke(page, mutableStateListOf(p), pageWidthPt(page))
            Tool.SHAPE -> {
                shapeStart = p
                liveShape = makeShape(0, page, shapeKind, p, p, style(Tool.SHAPE), pageWidthPt(page))
            }
            Tool.ERASER -> {
                marksBeforeErase = marks
                erasedPending.clear()
                erasedCommitted.clear()
                erasedPreview = emptySet()
                eraseAt(page, p)
            }
            Tool.MARKUP -> beginTextSelection(page, p, word = false)
            else -> Unit
        }
    }

    fun dragMove(page: Int, p: PointN) {
        when (tool) {
            Tool.PEN, Tool.HIGHLIGHTER -> live?.let { s ->
                val last = s.points.lastOrNull()
                if (last == null || kotlin.math.hypot(last.x - p.x, last.y - p.y) > 0.0012f) s.points += p
            }
            Tool.SHAPE -> {
                val a = shapeStart ?: return
                var b = p
                if (shapeKind == ShapeKind.LINE || shapeKind == ShapeKind.ARROW) b = snapAngle(a, p, page)
                liveShape = makeShape(0, page, shapeKind, a, b, style(Tool.SHAPE), pageWidthPt(page))
            }
            Tool.ERASER -> eraseAt(page, p)
            Tool.MARKUP -> moveTextSelection(page, p)
            else -> Unit
        }
    }

    fun dragEnd() {
        when (tool) {
            Tool.PEN, Tool.HIGHLIGHTER -> {
                val s = live ?: return
                live = null
                val pts = simplify(s.points.toList(), 0.0005f)
                if (pts.isEmpty()) return
                val st = style(tool)
                setMarks(marks + InkMark(newId(), s.page, listOf(pts), st.color, st.width, tool == Tool.HIGHLIGHTER, s.pageWidthPt))
            }
            Tool.SHAPE -> {
                val m = liveShape ?: return
                liveShape = null
                val r = RectN.of(m.start, m.end)
                if (r.width < 0.004f && r.height < 0.004f) return
                setMarks(marks + m.copy(id = newId()))
            }
            Tool.ERASER -> finishErase()
            Tool.MARKUP -> {
                val sel = selection ?: return
                selection = null
                if (sel.rects.isNotEmpty()) addMarkup(sel, markupKind)
            }
            else -> Unit
        }
    }

    fun dragCancel() {
        live = null
        liveShape = null
        if (tool == Tool.ERASER) {
            marksBeforeErase?.let { marks = it }
            marksBeforeErase = null
            erasedCommitted.clear()
            erasedPreview = emptySet()
        }
        if (tool == Tool.MARKUP) selection = null
    }

    /** Linie przyciągane co 15° (poziom, pion, przekątne), gdy kąt jest blisko. */
    private fun snapAngle(a: PointN, b: PointN, page: Int): PointN {
        val w = pageWidthPt(page)
        val h = pageHeightPt(page)
        val dx = (b.x - a.x) * w
        val dy = (b.y - a.y) * h
        val len = kotlin.math.hypot(dx, dy)
        if (len < 1f) return b
        val ang = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble())
        val snapped = Math.round(ang / 15.0) * 15.0
        if (kotlin.math.abs(ang - snapped) > 4) return b
        val r = Math.toRadians(snapped)
        return PointN(a.x + (len * kotlin.math.cos(r)).toFloat() / w, a.y + (len * kotlin.math.sin(r)).toFloat() / h)
    }

    private fun eraseAt(page: Int, p: PointN) {
        val tol = 8f / pageWidthPt(page)
        val hit = marks.filter { it.page == page && it.hit(p, tol) }
        if (hit.isNotEmpty()) {
            erasedPending += hit.map { it.id }
            marks = marks.filter { it.id !in erasedPending }
        }
        index.annots.getOrNull(page)?.forEach { a ->
            if (a.rect.contains(p, tol) && erasedCommitted.add(a)) erasedPreview = erasedCommitted.toSet()
        }
    }

    private fun finishErase() {
        val before = marksBeforeErase ?: marks
        marksBeforeErase = null
        if (erasedPending.isNotEmpty()) {
            pushUndo(Change.Marks(before))
            dirty = true
        }
        erasedPending.clear()
        val committed = erasedCommitted.toList()
        erasedCommitted.clear()
        if (committed.isNotEmpty()) {
            viewModelScope.launch {
                mutate("Usuwanie adnotacji", commit = false) { f -> PdfOps.deleteAnnotations(f, committed) }
                erasedPreview = emptySet()
            }
        } else {
            erasedPreview = emptySet()
        }
    }

    // ------------------------------------------------------------------ zaznaczanie tekstu

    var selection by mutableStateOf<TextSelection?>(null)
        private set
    private var selectionText: PageText? = null
    private var selectionJob: Job? = null

    fun beginTextSelection(page: Int, p: PointN, word: Boolean) {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            val t = textIndex?.page(page)
            if (t == null || t.length == 0) {
                if (word) toast("Na tej stronie nie ma tekstu do zaznaczenia (skan?)")
                return@launch
            }
            selectionText = t
            val i = t.nearest(p, 0.08f) ?: return@launch
            val range = if (word) t.wordAt(i) else i..i
            selection = TextSelection(page, range.first, range.last, t.rects(range), t.substring(range))
        }
    }

    fun moveTextSelection(page: Int, p: PointN) {
        val s = selection ?: return
        if (s.page != page) return
        val t = selectionText ?: return
        val i = t.nearest(p) ?: return
        val anchor = s.anchor
        val range = minOf(anchor, i)..maxOf(anchor, i)
        selection = s.copy(focus = i, rects = t.rects(range), text = t.substring(range))
    }

    /** Rozszerza zaznaczenie z uchwytu: [start] = true – początek. */
    fun dragSelectionHandle(start: Boolean, page: Int, p: PointN) {
        val s = selection ?: return
        if (s.page != page) return
        val t = selectionText ?: return
        val i = t.nearest(p) ?: return
        val r = s.range
        val (a, b) = if (start) i to r.last else r.first to i
        val range = minOf(a, b)..maxOf(a, b)
        selection = s.copy(anchor = range.first, focus = range.last, rects = t.rects(range), text = t.substring(range))
    }

    fun clearSelection() {
        selection = null
    }

    fun copySelection() {
        val s = selection ?: return
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("OpenPDF", s.text))
        selection = null
        toast("Skopiowano tekst")
    }

    fun markupSelection(kind: MarkupKind) {
        val s = selection ?: return
        selection = null
        addMarkup(s, kind)
    }

    private fun addMarkup(s: TextSelection, kind: MarkupKind) {
        val color = when (kind) {
            MarkupKind.HIGHLIGHT -> style(Tool.MARKUP).color
            MarkupKind.UNDERLINE -> if (tool == Tool.MARKUP) style(Tool.MARKUP).color.let { if (it == 0xFFFFEB3B.toInt()) 0xFF1E88E5.toInt() else it } else 0xFF1E88E5.toInt()
            MarkupKind.STRIKEOUT -> if (tool == Tool.MARKUP) style(Tool.MARKUP).color.let { if (it == 0xFFFFEB3B.toInt()) 0xFFE53935.toInt() else it } else 0xFFE53935.toInt()
        }
        setMarks(marks + MarkupMark(newId(), s.page, kind, s.rects, color, s.text))
    }

    // ------------------------------------------------------------------ dotknięcia

    var textRequest by mutableStateOf<TextRequest?>(null)
    var noteView by mutableStateOf<NoteView?>(null)
    var fieldDialog by mutableStateOf<FieldDialog?>(null)
    var selectedId by mutableStateOf<Long?>(null)
    var signaturePad by mutableStateOf(false)
    private var pendingImageAt: Pair<Int, PointN>? = null
    private var pendingSignatureAt: Pair<Int, PointN>? = null

    /** Dotknięcie w trybie przeglądania: odnośniki, notatki, pola formularza. Zwraca true, gdy obsłużone. */
    fun viewTap(page: Int, p: PointN): Boolean {
        val tol = 0.01f
        index.fields.firstOrNull { f -> f.widgets.any { it.page == page && it.rect.contains(p, tol) } }?.let { f ->
            if (f.kind != FieldKind.BUTTON && f.kind != FieldKind.SIGNATURE) {
                if (f.readOnly) toast("Pole „${f.label.ifBlank { f.name }}” jest tylko do odczytu") else openField(f)
                return true
            }
        }
        index.links.getOrNull(page)?.firstOrNull { it.rect.contains(p, tol) }?.let { l ->
            if (l.targetPage != null) jumpTo(l.targetPage) else l.uri?.let { events.trySend(UiEvent.OpenUri(it)) }
            return true
        }
        marks.lastOrNull { it.page == page && it is NoteMark && it.hit(p, tol) }?.let { m ->
            noteView = NoteView("Notatka", (m as NoteMark).text)
            return true
        }
        index.annots.getOrNull(page)?.lastOrNull { it.rect.contains(p, tol) && it.contents.isNotBlank() && it.subtype in NOTE_TYPES }?.let { a ->
            noteView = NoteView(annotTypeLabel(a.subtype) + (if (a.author.isNotBlank()) " · ${a.author}" else ""), a.contents)
            return true
        }
        return false
    }

    /** Dotknięcie w trybie edycji (narzędzia punktowe). */
    fun editTap(page: Int, p: PointN) {
        when (tool) {
            Tool.TEXT -> {
                val existing = marks.lastOrNull { it.page == page && it is TextMark && it.hit(p, 0.005f) } as? TextMark
                textRequest = if (existing != null) TextRequest(Tool.TEXT, page, PointN(existing.rect.left, existing.rect.top), existing.id, existing.text)
                else TextRequest(Tool.TEXT, page, p, null, "")
            }
            Tool.NOTE -> {
                val existing = marks.lastOrNull { it.page == page && it is NoteMark && it.hit(p, 0.005f) } as? NoteMark
                textRequest = if (existing != null) TextRequest(Tool.NOTE, page, existing.at, existing.id, existing.text)
                else TextRequest(Tool.NOTE, page, p, null, "")
            }
            Tool.IMAGE -> {
                pendingImageAt = page to p
                events.trySend(UiEvent.PickImageForPlacement)
            }
            Tool.SIGNATURE -> {
                val sigs = library.signatures()
                if (sigs.isEmpty()) {
                    pendingSignatureAt = page to p
                    signaturePad = true
                } else {
                    placeSignature(sigs.first(), page, p)
                }
            }
            Tool.SELECT -> {
                selectedId = markAt(page, p)?.id
            }
            else -> Unit
        }
    }

    fun markAt(page: Int, p: PointN): Mark? {
        val tol = 10f / pageWidthPt(page)
        return marks.lastOrNull { it.page == page && it.hit(p, tol) }
    }

    fun submitText(text: String, sizePt: Float? = null) {
        val req = textRequest ?: return
        textRequest = null
        val st = style(req.tool)
        if (text.isBlank()) {
            if (req.editId != null) setMarks(marks.filter { it.id != req.editId })
            return
        }
        when (req.tool) {
            Tool.TEXT -> {
                val size = sizePt ?: (marks.firstOrNull { it.id == req.editId } as? TextMark)?.sizePt ?: st.width
                val rect = measureText(text, size, req.page, req.at)
                val old = marks.firstOrNull { it.id == req.editId } as? TextMark
                val mark = TextMark(req.editId ?: newId(), req.page, rect, text, old?.color ?: st.color, size, old?.background)
                setMarks(if (old != null) marks.map { if (it.id == old.id) mark else it } else marks + mark)
                selectedId = mark.id
            }
            Tool.NOTE -> {
                val w = 22f / pageWidthPt(req.page)
                val aspect = pageWidthPt(req.page) / pageHeightPt(req.page)
                val old = marks.firstOrNull { it.id == req.editId } as? NoteMark
                val mark = NoteMark(req.editId ?: newId(), req.page, req.at, text, old?.color ?: st.color, w, aspect)
                setMarks(if (old != null) marks.map { if (it.id == old.id) mark else it } else marks + mark)
            }
            else -> Unit
        }
    }

    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }

    /** Prostokąt mieszczący tekst (pomiar czcionką systemową, z zapasem na różnice metryk). */
    private fun measureText(text: String, sizePt: Float, page: Int, at: PointN): RectN {
        measurePaint.textSize = sizePt
        val lines = text.split('\n')
        val w = lines.maxOf { measurePaint.measureText(it) } * 1.08f + 2 * com.blacksingularities.openpdf.pdf.AnnotationWriter.TEXT_PAD + 2f
        val h = lines.size * sizePt * com.blacksingularities.openpdf.pdf.AnnotationWriter.TEXT_LEADING + 2 * com.blacksingularities.openpdf.pdf.AnnotationWriter.TEXT_PAD
        val wn = w / pageWidthPt(page)
        val hn = h / pageHeightPt(page)
        val left = at.x.coerceIn(0f, max(0f, 1f - wn))
        val top = at.y.coerceIn(0f, max(0f, 1f - hn))
        return RectN(left, top, left + wn, top + hn)
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        selectedId = null
        setMarks(marks.filter { it.id != id })
    }

    fun editSelectedText() {
        val m = marks.firstOrNull { it.id == selectedId } ?: return
        when (m) {
            is TextMark -> textRequest = TextRequest(Tool.TEXT, m.page, PointN(m.rect.left, m.rect.top), m.id, m.text)
            is NoteMark -> textRequest = TextRequest(Tool.NOTE, m.page, m.at, m.id, m.text)
            else -> Unit
        }
    }

    fun duplicateSelected() {
        val m = marks.firstOrNull { it.id == selectedId } ?: return
        val copy = m.moved(0.02f, 0.02f).let { c ->
            when (c) {
                is InkMark -> c.copy(id = newId())
                is ShapeMark -> c.copy(id = newId())
                is TextMark -> c.copy(id = newId())
                is NoteMark -> c.copy(id = newId())
                is MarkupMark -> c.copy(id = newId())
                is ImageMark -> c.copy(id = newId())
                is SignatureMark -> c.copy(id = newId())
            }
        }
        setMarks(marks + copy)
        selectedId = copy.id
    }

    private var moveBase: List<Mark>? = null

    fun beginMove(id: Long) {
        selectedId = id
        moveBase = marks
    }

    fun moveSelected(dx: Float, dy: Float) {
        val id = selectedId ?: return
        marks = marks.map { if (it.id == id) it.moved(dx, dy) else it }
    }

    fun resizeSelected(corner: PointN) {
        val id = selectedId ?: return
        marks = marks.map { if (it.id == id && it.resizable) it.resized(corner, keepAspect = it !is TextMark, pageAspect = 1f) else it }
    }

    fun endMove() {
        val base = moveBase ?: return
        moveBase = null
        if (base != marks) {
            pushUndo(Change.Marks(base))
            dirty = true
        }
    }

    // ------------------------------------------------------------------ obrazy i podpisy

    private val markBitmaps = LruCache<String, Bitmap>(8)

    fun markBitmap(path: String): Bitmap? {
        markBitmaps.get(path)?.let { return it }
        val b = BitmapFactory.decodeFile(path) ?: return null
        markBitmaps.put(path, b)
        return b
    }

    fun placeImage(uri: Uri?) {
        val at = pendingImageAt
        pendingImageAt = null
        if (uri == null || at == null) return
        val d = doc ?: return
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    val raw = File(d.dir, "img-raw-${newId()}")
                    copyUri(uri, raw)
                    val bmp = PdfOps.decodeScaled(raw, 2000) ?: error("Nie można odczytać obrazu")
                    raw.delete()
                    val out = File(d.dir, "img-${newId()}.png")
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    out to (bmp.width.toFloat() / bmp.height)
                }
                val (page, p) = at
                val wN = 0.4f
                val hN = wN * pageWidthPt(page) / f.second / pageHeightPt(page)
                val left = (p.x - wN / 2).coerceIn(0f, max(0f, 1f - wN))
                val top = (p.y - hN / 2).coerceIn(0f, max(0f, 1f - hN))
                val mark = ImageMark(newId(), page, RectN(left, top, left + wN, top + hN), f.first.absolutePath)
                setMarks(marks + mark)
                tool = Tool.SELECT
                selectedId = mark.id
            } catch (e: Throwable) {
                toast("Nie udało się wstawić obrazu: ${e.message}")
            }
        }
    }

    fun signatures(): List<SavedSignature> = library.signatures()

    fun saveSignature(strokes: List<List<PointN>>, aspect: Float) {
        if (strokes.isEmpty()) return
        val sig = SavedSignature("sig-" + newId(), strokes, aspect)
        library.saveSignatures(listOf(sig) + library.signatures().take(9))
        signaturePad = false
        val at = pendingSignatureAt
        pendingSignatureAt = null
        if (at != null) placeSignature(sig, at.first, at.second)
        else toast("Podpis zapisany – dotknij strony, aby go wstawić")
    }

    fun deleteSignature(id: String) {
        library.saveSignatures(library.signatures().filter { it.id != id })
    }

    fun placeSignature(sig: SavedSignature, page: Int, p: PointN) {
        val st = style(Tool.SIGNATURE)
        val wN = 0.32f
        val hN = wN * pageWidthPt(page) / sig.aspect / pageHeightPt(page)
        val left = (p.x - wN / 2).coerceIn(0f, max(0f, 1f - wN))
        val top = (p.y - hN / 2).coerceIn(0f, max(0f, 1f - hN))
        val mark = SignatureMark(newId(), page, RectN(left, top, left + wN, top + hN), sig.strokes, st.color, st.width)
        setMarks(marks + mark)
        tool = Tool.SELECT
        selectedId = mark.id
    }

    /** Podpis wybrany z listy – wstawiany na środku bieżącej strony. */
    fun placeSignatureOnCurrent(sig: SavedSignature) {
        placeSignature(sig, currentPage, PointN(0.5f, 0.75f))
        sheet = null
    }

    // ------------------------------------------------------------------ wyszukiwanie

    var searchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    val searchHits = mutableStateListOf<SearchHit>()
    var searchIndex by mutableIntStateOf(-1)
        private set
    var searching by mutableStateOf(false)
        private set
    var searchProgress by mutableFloatStateOf(0f)
        private set
    private var searchJob: Job? = null

    fun search(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchHits.clear()
        searchIndex = -1
        val ti = textIndex ?: return
        if (query.isBlank()) {
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            searching = true
            searchProgress = 0f
            val start = currentPage
            try {
                ti.search(query, { searchProgress = it }) { hit ->
                    searchHits += hit
                    // Pierwsze trafienie od bieżącej strony – od razu na ekran.
                    if (searchIndex < 0 && hit.page >= start) {
                        searchIndex = searchHits.size - 1
                        jumpTo(hit.page, hit.rects.first())
                    }
                }
                if (searchIndex < 0 && searchHits.isNotEmpty()) {
                    searchIndex = 0
                    jumpTo(searchHits[0].page, searchHits[0].rects.first())
                }
                if (searchHits.isEmpty()) toast("Nie znaleziono „$query”")
            } finally {
                searching = false
            }
        }
    }

    fun searchStep(dir: Int) {
        if (searchHits.isEmpty()) return
        searchIndex = (searchIndex + dir).mod(searchHits.size)
        val h = searchHits[searchIndex]
        jumpTo(h.page, h.rects.first())
    }

    fun openHit(i: Int) {
        val h = searchHits.getOrNull(i) ?: return
        searchIndex = i
        jumpTo(h.page, h.rects.first())
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchActive = false
        searchHits.clear()
        searchQuery = ""
        searchIndex = -1
    }

    // ------------------------------------------------------------------ strony

    fun rotatePages(pages: Collection<Int>, degrees: Int) = launchMutate(if (degrees > 0) "Obracanie w prawo" else "Obracanie w lewo") { PdfOps.rotatePages(it, pages, degrees) }

    fun deletePages(pages: Collection<Int>) {
        if (pages.size >= index.pageCount) {
            toast("Nie można usunąć wszystkich stron")
            return
        }
        // Elementy oczekujące na usuwanych stronach – usuwane razem z nimi, pozostałe przesuwane.
        launchMutate("Usuwanie stron") { PdfOps.deletePages(it, pages) }
    }

    fun duplicatePages(pages: Collection<Int>) = launchMutate("Powielanie stron") { PdfOps.duplicatePages(it, pages) }

    fun movePages(pages: List<Int>, target: Int) = launchMutate("Przenoszenie stron") { PdfOps.movePages(it, pages, target) }

    fun insertBlank(at: Int) = launchMutate("Wstawianie pustej strony") { PdfOps.insertBlank(it, at) }

    private var pendingInsertAt: Int? = null

    fun insertPdf(uri: Uri, at: Int?, password: String? = null) {
        val d = doc ?: return
        pendingInsertAt = at
        viewModelScope.launch {
            val f = File(d.dir, "insert-${newId()}.pdf")
            try {
                withContext(Dispatchers.IO) { copyUri(uri, f) }
                val ok = mutate("Wstawianie stron z pliku") { PdfOps.insertPdf(it, f, password, at) }
                if (!ok && withContext(Dispatchers.IO) { runCatching { PdfOps.needsPassword(f) }.getOrDefault(false) }) {
                    passwordRequest = PasswordRequest(uri, null, queryMeta(uri).name, password != null, PasswordPurpose.INSERT)
                }
            } catch (e: Throwable) {
                toast("Nie udało się wstawić pliku: ${e.message}")
            } finally {
                f.delete()
            }
        }
    }

    fun insertImages(uris: List<Uri>, at: Int) {
        val d = doc ?: return
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { copyImages(uris, d.dir) }
            mutate("Wstawianie obrazów") { PdfOps.insertImages(it, files, at) }
            files.forEach { it.delete() }
        }
    }

    fun extractPages(pages: Collection<Int>) {
        val d = doc ?: return
        viewModelScope.launch {
            busy = Busy("Wyodrębnianie stron…")
            try {
                val out = File(d.dir, "extract-${newId()}.pdf")
                withContext(Dispatchers.IO) {
                    if (marks.isNotEmpty()) {
                        val tmp = File(d.dir, "extract-src.pdf")
                        d.source.copyTo(tmp, overwrite = true)
                        PdfOps.commitMarks(tmp, marks, settings.author, PdfOps.systemFonts())
                        PdfOps.extractPages(tmp, pages, out)
                        tmp.delete()
                    } else {
                        PdfOps.extractPages(d.source, pages, out)
                    }
                }
                val base = d.name.removeSuffix(".pdf").removeSuffix(".PDF")
                val sorted = pages.sorted()
                val label = if (sorted.size == 1) "strona ${sorted[0] + 1}" else "strony ${sorted.first() + 1}-${sorted.last() + 1}"
                requestSave(SaveTarget.FileCopy(out, "application/pdf"), "$base ($label).pdf", "application/pdf")
            } catch (e: Throwable) {
                toast("Nie udało się wyodrębnić stron: ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    // ------------------------------------------------------------------ formularz, adnotacje, metadane

    fun openField(f: FieldInfo) {
        fieldDialog = FieldDialog(f)
    }

    fun fillFields(edits: List<FieldEdit>) {
        fieldDialog = null
        if (edits.isEmpty()) return
        viewModelScope.launch {
            var failed: List<String> = emptyList()
            mutate("Wypełnianie formularza") { failed = PdfOps.fillFields(it, edits) }
            if (failed.isNotEmpty()) toast("Nie udało się ustawić: ${failed.joinToString()}")
        }
    }

    fun flattenForm() = launchMutate("Spłaszczanie formularza") { PdfOps.flattenForm(it) }

    fun deleteAnnotation(a: AnnotInfo) = launchMutate("Usuwanie adnotacji", commit = false) { PdfOps.deleteAnnotations(it, listOf(a)) }

    fun deleteAllAnnotations() = launchMutate("Usuwanie wszystkich adnotacji", commit = false) { PdfOps.deleteAllAnnotations(it) }

    fun removePendingMark(id: Long) = setMarks(marks.filter { it.id != id })

    fun setInfo(e: InfoEdit) = launchMutate("Zapisywanie właściwości") { PdfOps.setInfo(it, e) }

    /** Zapisuje elementy oczekujące w pliku roboczym (bez zapisu na dysk użytkownika). */
    fun commitPending() {
        if (marks.isEmpty()) return
        launchMutate("Zapisywanie adnotacji") { }
    }

    // ------------------------------------------------------------------ kompresja

    var compressResult by mutableStateOf<String?>(null)

    fun compress(maxSide: Int, quality: Float) {
        val d = doc ?: return
        viewModelScope.launch {
            val before = d.source.length()
            var count = 0
            val out = File(d.dir, "compressed.pdf")
            val ok = mutate("Kompresja") { f ->
                count = PdfOps.compress(f, out, maxSide, quality) { p -> busy = Busy("Kompresja…", p) }
                if (out.length() in 1 until f.length()) {
                    f.delete()
                    if (!out.renameTo(f)) error("Nie można zapisać pliku")
                } else {
                    out.delete()
                }
            }
            if (ok) {
                val after = d.source.length()
                compressResult = if (after < before) {
                    "Zmniejszono z ${formatSize(before)} do ${formatSize(after)} (${100 - after * 100 / max(1, before)}% mniej, obrazy: $count)"
                } else {
                    "Plik nie zawiera obrazów, które da się zmniejszyć"
                }
            }
        }
    }

    // ------------------------------------------------------------------ zapis, udostępnianie, eksport

    fun requestSave(target: SaveTarget, name: String, mime: String) {
        pendingSave = target
        events.trySend(UiEvent.AskSaveLocation(name, mime, target))
    }

    /** „Zapisz”: w miejscu oryginału, gdy to możliwe, w przeciwnym razie „Zapisz jako”. */
    fun save() {
        val d = doc ?: return
        val uri = d.uri
        if (uri == null || !d.writable) {
            saveAs()
            return
        }
        viewModelScope.launch { saveTo(uri, rebind = false) }
    }

    fun saveAs() {
        val d = doc ?: return
        val name = if (d.name.endsWith(".pdf", ignoreCase = true)) d.name else d.name + ".pdf"
        requestSave(SaveTarget.Document, name, "application/pdf")
    }

    /** Wynik wyboru miejsca zapisu. */
    fun onSaveLocation(uri: Uri?) {
        val target = pendingSave
        pendingSave = null
        if (uri == null || target == null) return
        viewModelScope.launch {
            when (target) {
                SaveTarget.Document -> saveTo(uri, rebind = true)
                is SaveTarget.FileCopy -> writeToUri(target.file, uri, "Zapisano plik")
                is SaveTarget.Text -> {
                    try {
                        withContext(Dispatchers.IO) { openOut(uri).use { it.write(target.text.toByteArray()) } }
                        toast("Zapisano tekst")
                    } catch (e: Throwable) {
                        toast("Zapis nie powiódł się: ${e.message}")
                    }
                }
            }
        }
    }

    private fun openOut(uri: Uri) = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: resolver.openOutputStream(uri, "w") ?: error("Brak dostępu do zapisu")

    private suspend fun writeToUri(file: File, uri: Uri, done: String) {
        busy = Busy("Zapisywanie…")
        try {
            withContext(Dispatchers.IO) { openOut(uri).use { o -> file.inputStream().use { it.copyTo(o, 256 * 1024) } } }
            toast(done)
        } catch (e: Throwable) {
            toast("Zapis nie powiódł się: ${e.message}")
        } finally {
            busy = null
        }
    }

    private suspend fun saveTo(uri: Uri, rebind: Boolean) {
        val d = doc ?: return
        if (marks.isNotEmpty() && !mutate("Zapisywanie adnotacji") { }) return
        busy = Busy("Zapisywanie…")
        try {
            val out = File(d.dir, "export.pdf")
            withContext(Dispatchers.IO) {
                PdfOps.export(d.source, out, protection?.user, protection?.owner)
                openOut(uri).use { o -> out.inputStream().use { it.copyTo(o, 256 * 1024) } }
                out.delete()
            }
            dirty = false
            if (rebind) {
                val meta = withContext(Dispatchers.IO) { queryMeta(uri) }
                val writable = withContext(Dispatchers.IO) { checkWritable(uri) }
                doc = OpenDoc(uri, meta.name, d.dir, d.source, writable)
                library.touch(RecentDoc(uri.toString(), meta.name, d.source.length(), System.currentTimeMillis(), index.pageCount, currentPage, writable))
                recents = library.recents()
                saveThumbnail(uri.toString())
            }
            toast("Zapisano")
        } catch (e: Throwable) {
            toast("Zapis nie powiódł się: ${e.message}")
        } finally {
            busy = null
        }
    }

    /** Kopia dokumentu ze wszystkimi zmianami (do udostępnienia i druku). */
    private suspend fun exportCopy(): File? {
        val d = doc ?: return null
        if (marks.isNotEmpty() && !mutate("Zapisywanie adnotacji") { }) return null
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = d.name.let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        val out = File(dir, name)
        busy = Busy("Przygotowywanie…")
        return try {
            withContext(Dispatchers.IO) { PdfOps.export(d.source, out, protection?.user, protection?.owner) }
            out
        } catch (e: Throwable) {
            toast("Nie udało się przygotować pliku: ${e.message}")
            null
        } finally {
            busy = null
        }
    }

    fun share() {
        viewModelScope.launch { exportCopy()?.let { events.send(UiEvent.Share(it, "application/pdf")) } }
    }

    fun print() {
        viewModelScope.launch {
            // Do druku bez hasła – systemowa usługa druku nie otworzy zaszyfrowanego pliku.
            val d = doc ?: return@launch
            if (marks.isNotEmpty() && !mutate("Zapisywanie adnotacji") { }) return@launch
            events.send(UiEvent.Print(d.source, d.name))
        }
    }

    /** Eksport stron jako obrazy do galerii (Obrazy/OpenPDF). */
    fun exportImages(pages: List<Int>, dpi: Int, png: Boolean) {
        val s = render ?: return
        val d = doc ?: return
        viewModelScope.launch {
            val base = d.name.removeSuffix(".pdf").removeSuffix(".PDF").take(60)
            var saved = 0
            try {
                for ((i, p) in pages.withIndex()) {
                    busy = Busy("Eksport strony ${p + 1} (${i + 1} z ${pages.size})", i.toFloat() / pages.size)
                    val bmp = s.renderAtDpi(p, dpi) ?: continue
                    withContext(Dispatchers.IO) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "$base-${p + 1}.${if (png) "png" else "jpg"}")
                            put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenPDF")
                        }
                        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Brak dostępu do galerii")
                        resolver.openOutputStream(uri)?.use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
                    }
                    bmp.recycle()
                    saved++
                }
                toast("Zapisano $saved obrazów w Obrazy/OpenPDF")
            } catch (e: Throwable) {
                toast("Eksport przerwany: ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    var extractedText by mutableStateOf<String?>(null)

    fun extractText() {
        val ti = textIndex ?: return
        viewModelScope.launch {
            busy = Busy("Odczytywanie tekstu…", 0f)
            try {
                val text = ti.allText { busy = Busy("Odczytywanie tekstu…", it) }
                extractedText = text
                sheet = Sheet.TEXT
                if (text.isBlank()) toast("Dokument nie zawiera tekstu (to prawdopodobnie skan)")
            } finally {
                busy = null
            }
        }
    }

    fun saveExtractedText() {
        val t = extractedText ?: return
        val name = (doc?.name?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: "tekst") + ".txt"
        requestSave(SaveTarget.Text(t), name, "text/plain")
    }

    // ------------------------------------------------------------------ aktualizacje

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    private var updateJob: Job? = null

    fun checkUpdate(silent: Boolean = false) {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            update = UpdateState.Checking
            update = try {
                val info = UpdateManager.fetchLatest()
                if (UpdateManager.isNewer(info.version)) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Throwable) {
                if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Brak połączenia")
            }
        }
    }

    fun downloadUpdate() {
        val info = (update as? UpdateState.Available)?.info ?: return
        updateJob = viewModelScope.launch {
            update = UpdateState.Downloading(info, 0f)
            update = try {
                val file = UpdateManager.download(ctx, info) { p -> update = UpdateState.Downloading(info, p) }
                UpdateState.Ready(info, file)
            } catch (e: Throwable) {
                UpdateState.Failed(e.message ?: "Pobieranie nie powiodło się")
            }
        }
    }

    fun installUpdate() {
        val ready = update as? UpdateState.Ready ?: return
        if (!UpdateManager.canInstall(ctx)) {
            UpdateManager.openInstallPermissionSettings(ctx)
            return
        }
        viewModelScope.launch {
            update = UpdateState.Installing(ready.info)
            try {
                UpdateManager.install(ctx, ready.file)
            } catch (e: Throwable) {
                update = UpdateState.Failed(e.message ?: "Instalacja nie powiodła się")
            }
        }
    }

    // Na końcu klasy – wszystkie pola stanu są już utworzone.
    init {
        // Pozostałości poprzednich uruchomień.
        runCatching { sessionsRoot.listFiles()?.forEach { it.deleteRecursively() } }
        if (settings.autoUpdate) checkUpdate(silent = true)
    }

    override fun onCleared() {
        render?.close()
        super.onCleared()
    }

    companion object {
        val NOTE_TYPES = setOf("Text", "FreeText", "Highlight", "Underline", "StrikeOut", "Squiggly", "Square", "Circle", "Ink", "Line", "Polygon", "PolyLine", "Stamp", "Caret", "FileAttachment")

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
            bytes >= 1024 -> "%d KB".format(bytes / 1024)
            else -> "$bytes B"
        }

        fun annotTypeLabel(subtype: String): String = when (subtype) {
            "Text" -> "Notatka"
            "FreeText" -> "Tekst"
            "Highlight" -> "Zakreślenie"
            "Underline" -> "Podkreślenie"
            "StrikeOut" -> "Przekreślenie"
            "Squiggly" -> "Falista linia"
            "Ink" -> "Rysunek"
            "Square" -> "Prostokąt"
            "Circle" -> "Elipsa"
            "Line" -> "Linia"
            "Polygon" -> "Wielokąt"
            "PolyLine" -> "Łamana"
            "Stamp" -> "Pieczątka / obraz"
            "Caret" -> "Wstawienie"
            "FileAttachment" -> "Załącznik"
            "Sound" -> "Dźwięk"
            else -> subtype
        }

        @Suppress("unused")
        val DEFAULT_MARKER = Palette.MarkerColors.first()
    }
}
