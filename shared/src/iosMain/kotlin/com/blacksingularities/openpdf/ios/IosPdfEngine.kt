package com.blacksingularities.openpdf.ios

import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.pdf.AnnotInfo
import com.blacksingularities.openpdf.pdf.DocIndex
import com.blacksingularities.openpdf.pdf.DocInfo
import com.blacksingularities.openpdf.pdf.FieldEdit
import com.blacksingularities.openpdf.pdf.FieldInfo
import com.blacksingularities.openpdf.pdf.FieldKind
import com.blacksingularities.openpdf.pdf.FieldWidget
import com.blacksingularities.openpdf.pdf.ImageMark
import com.blacksingularities.openpdf.pdf.InfoEdit
import com.blacksingularities.openpdf.pdf.InkMark
import com.blacksingularities.openpdf.pdf.LinkInfo
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.MarkupMark
import com.blacksingularities.openpdf.pdf.NoteMark
import com.blacksingularities.openpdf.pdf.OutlineEntry
import com.blacksingularities.openpdf.pdf.PageGeo
import com.blacksingularities.openpdf.pdf.PageText
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.RectN
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.pdf.ShapeMark
import com.blacksingularities.openpdf.pdf.SignatureMark
import com.blacksingularities.openpdf.pdf.TextMark
import com.blacksingularities.openpdf.pdf.normalizeRotation
import com.blacksingularities.openpdf.platform.A4_HEIGHT_PT
import com.blacksingularities.openpdf.platform.A4_WIDTH_PT
import com.blacksingularities.openpdf.platform.PageRenderer
import com.blacksingularities.openpdf.platform.PageTextSource
import com.blacksingularities.openpdf.platform.PasswordRequiredException
import com.blacksingularities.openpdf.platform.PdfEngine
import kotlinx.cinterop.CValue
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.PDFKit.*
import platform.UIKit.*
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

internal fun Path.url(): NSURL = NSURL.fileURLWithPath(toString())

/** Otwiera plik PDF albo zwraca null (uszkodzony / nie-PDF). */
internal fun openDocument(path: Path): PDFDocument? {
    val d: PDFDocument? = try {
        PDFDocument(uRL = path.url())
    } catch (_: Throwable) {
        null
    }
    return d
}

internal fun PDFDocument.pages(): List<PDFPage> = (0 until pageCount.toInt()).mapNotNull { pageAtIndex(it.toULong()) }

internal fun PDFPage.annots(): List<PDFAnnotation> = annotations.filterIsInstance<PDFAnnotation>()

internal val PDFAnnotation.kind: String get() = (type ?: "").removePrefix("/")

/** Geometria strony: widoczny obszar (CropBox) i obrót. */
internal fun PDFPage.geo(): PageGeo {
    val (x, y, w, h) = boundsForBox(kPDFDisplayBoxCropBox).useContents {
        listOf(origin.x.toFloat(), origin.y.toFloat(), size.width.toFloat(), size.height.toFloat())
    }
    return PageGeo(x, y, max(1f, w), max(1f, h), normalizeRotation(rotation.toInt()))
}

private fun rectOf(l: Float, b: Float, r: Float, t: Float): CValue<CGRect> =
    CGRectMake(l.toDouble(), b.toDouble(), (r - l).toDouble(), (t - b).toDouble())

private fun uiColor(argb: Int, alpha: Float? = null): UIColor = UIColor.colorWithRed(
    ((argb shr 16) and 0xFF) / 255.0,
    green = ((argb shr 8) and 0xFF) / 255.0,
    blue = (argb and 0xFF) / 255.0,
    alpha = (alpha ?: (((argb ushr 24) and 0xFF) / 255f)).toDouble(),
)

private fun argbOf(c: UIColor?): Int? {
    c ?: return null
    return memScoped {
        val r = alloc<CGFloatVar>()
        val g = alloc<CGFloatVar>()
        val b = alloc<CGFloatVar>()
        val a = alloc<CGFloatVar>()
        if (!c.getRed(r.ptr, green = g.ptr, blue = b.ptr, alpha = a.ptr) || a.value <= 0.0) return@memScoped null
        fun ch(v: Double) = (v * 255).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (ch(r.value) shl 16) or (ch(g.value) shl 8) or ch(b.value)
    }
}

private fun NSDate.millis(): Long = (timeIntervalSince1970 * 1000.0).toLong()

/** Silnik PDF iOS: Apple PDFKit (odczyt, zmiany, adnotacje) i CoreGraphics (rysowanie). */
class IosPdfEngine : PdfEngine {
    private val fs = FileSystem.SYSTEM

    private fun load(file: Path, password: String? = null): PDFDocument {
        val d = openDocument(file) ?: error(tr("Nie można otworzyć pliku PDF"))
        if (d.isLocked && !d.unlockWithPassword(password ?: "")) throw PasswordRequiredException()
        return d
    }

    private fun write(d: PDFDocument, out: Path, options: Map<Any?, Any?>? = null) {
        val ok = if (options == null) d.writeToURL(out.url()) else d.writeToURL(out.url(), withOptions = options)
        if (!ok) error(tr("Nie można zapisać pliku {0}", out.name))
    }

    /** Otwiera plik, wykonuje [block] i zapisuje wynik w miejsce pliku (przez plik tymczasowy). */
    private inline fun <T> edit(file: Path, block: (PDFDocument) -> T): T {
        val d = load(file)
        val r = block(d)
        val tmp = file.parent!! / (file.name + ".tmp")
        write(d, tmp)
        fs.atomicMove(tmp, file)
        return r
    }

    private fun PDFDocument.index(p: PDFPage?): Int? {
        p ?: return null
        val i = indexForPage(p).toLong()
        return if (i in 0 until pageCount.toLong()) i.toInt() else null
    }

    // ------------------------------------------------------------------ otwieranie

    override fun needsPassword(file: Path): Boolean {
        val d = openDocument(file) ?: return false
        return d.isLocked && !d.unlockWithPassword("")
    }

    override fun prepareWorking(original: Path, password: String?, working: Path): Boolean {
        val d = load(original, password)
        return if (d.isEncrypted) {
            write(d, working)
            true
        } else {
            fs.copy(original, working)
            false
        }
    }

    override fun rewrite(file: Path, out: Path) = write(load(file), out)

    // ------------------------------------------------------------------ indeks

    override fun buildIndex(source: Path, display: Path): Pair<DocIndex, Boolean> {
        val d = load(source)
        val pages = d.pages()
        val geos = pages.map { it.geo() }
        val annots = ArrayList<List<AnnotInfo>>()
        val links = ArrayList<List<LinkInfo>>()
        // Widżety pól formularza: nazwa pola → (strona, adnotacja).
        val widgets = LinkedHashMap<String, MutableList<Pair<Int, PDFAnnotation>>>()
        for ((pi, page) in pages.withIndex()) {
            val geo = geos[pi]
            val pageAnnots = ArrayList<AnnotInfo>()
            val pageLinks = ArrayList<LinkInfo>()
            for ((ai, a) in page.annots().withIndex()) {
                val rect = a.bounds.useContents { geo.rectFromPdf(origin.x.toFloat(), origin.y.toFloat(), (origin.x + size.width).toFloat(), (origin.y + size.height).toFloat()) }
                when (a.kind) {
                    "Link" -> {
                        val action = a.action
                        val uri = a.URL?.absoluteString ?: (action as? PDFActionURL)?.URL?.absoluteString
                        val target = d.index(a.destination?.page) ?: d.index((action as? PDFActionGoTo)?.destination?.page)
                        if (target != null || uri != null) pageLinks += LinkInfo(rect, target, uri)
                    }
                    "Popup" -> Unit
                    "Widget" -> {
                        val name = a.fieldName ?: continue
                        widgets.getOrPut(name) { ArrayList() } += pi to a
                    }
                    else -> pageAnnots += AnnotInfo(
                        page = pi,
                        index = ai,
                        subtype = a.kind,
                        rect = rect,
                        contents = a.contents ?: "",
                        author = a.userName ?: "",
                        color = argbOf(a.color),
                    )
                }
            }
            annots += pageAnnots
            links += pageLinks
        }
        val outline = ArrayList<OutlineEntry>()
        runCatching { d.outlineRoot?.let { readOutline(d, it, 0, outline) } }
        val fields = runCatching { readFields(widgets, geos) }.getOrDefault(emptyList())
        val info = runCatching { readInfo(d) }.getOrDefault(DocInfo())
        // PDFKit rysuje obrót, przycięcie i adnotacje sam – kopia do wyświetlania nie jest potrzebna.
        return DocIndex(geos, annots, links, outline, fields, info) to false
    }

    private fun readOutline(d: PDFDocument, node: PDFOutline, depth: Int, out: MutableList<OutlineEntry>) {
        if (depth > 12 || out.size > 5000) return
        for (k in 0 until node.numberOfChildren.toInt()) {
            val item = node.childAtIndex(k.toULong()) ?: continue
            val page = d.index(item.destination?.page) ?: d.index((item.action as? PDFActionGoTo)?.destination?.page)
            out += OutlineEntry(item.label ?: "", page, depth)
            if (item.numberOfChildren > 0u) readOutline(d, item, depth + 1, out)
        }
    }

    private fun widgetKind(a: PDFAnnotation): FieldKind? = when ((a.widgetFieldType ?: "").removePrefix("/")) {
        "Tx" -> FieldKind.TEXT
        "Ch" -> FieldKind.CHOICE
        "Sig" -> FieldKind.SIGNATURE
        "Btn" -> when (a.widgetControlType) {
            kPDFWidgetCheckBoxControl -> FieldKind.CHECKBOX
            kPDFWidgetRadioButtonControl -> FieldKind.RADIO
            else -> FieldKind.BUTTON
        }
        else -> null
    }

    private fun readFields(widgets: Map<String, List<Pair<Int, PDFAnnotation>>>, geos: List<PageGeo>): List<FieldInfo> =
        widgets.mapNotNull { (name, list) ->
            val first = list.first().second
            val kind = widgetKind(first) ?: return@mapNotNull null
            val options = when (kind) {
                FieldKind.CHOICE -> first.choices?.map { it.toString() }.orEmpty()
                FieldKind.RADIO -> list.mapNotNull { it.second.buttonWidgetStateString }.distinct()
                else -> emptyList()
            }
            val value = when (kind) {
                FieldKind.CHECKBOX -> if (first.buttonWidgetState == kPDFWidgetOnState) "true" else "false"
                FieldKind.RADIO -> list.firstOrNull { it.second.buttonWidgetState == kPDFWidgetOnState }?.second?.buttonWidgetStateString ?: ""
                else -> first.widgetStringValue ?: ""
            }
            FieldInfo(
                name = name,
                label = name.substringAfterLast('.'),
                kind = kind,
                value = value,
                options = options,
                exportOptions = options,
                readOnly = first.readOnly,
                multiline = first.multiline,
                widgets = list.map { (pi, a) ->
                    val geo = geos[pi]
                    FieldWidget(pi, a.bounds.useContents { geo.rectFromPdf(origin.x.toFloat(), origin.y.toFloat(), (origin.x + size.width).toFloat(), (origin.y + size.height).toFloat()) })
                },
            )
        }

    private fun readInfo(d: PDFDocument): DocInfo {
        val attrs = d.documentAttributes ?: emptyMap<Any?, Any?>()
        fun s(k: Any?): String = when (val v = attrs[k]) {
            null -> ""
            is List<*> -> v.joinToString(", ")
            else -> v.toString()
        }
        return DocInfo(
            title = s(PDFDocumentTitleAttribute),
            author = s(PDFDocumentAuthorAttribute),
            subject = s(PDFDocumentSubjectAttribute),
            keywords = s(PDFDocumentKeywordsAttribute),
            creator = s(PDFDocumentCreatorAttribute),
            producer = s(PDFDocumentProducerAttribute),
            version = "${d.majorVersion}.${d.minorVersion}",
            created = (attrs[PDFDocumentCreationDateAttribute] as? NSDate)?.millis(),
            modified = (attrs[PDFDocumentModificationDateAttribute] as? NSDate)?.millis(),
        )
    }

    override suspend fun openRenderer(file: Path): PageRenderer = IosRenderer.open(file)

    override fun openText(file: Path): PageTextSource = IosText(load(file))

    // ------------------------------------------------------------------ adnotacje

    override fun commitMarks(file: Path, marks: List<Mark>, author: String) {
        if (marks.isEmpty()) return
        edit(file) { d ->
            for (mark in marks) {
                val page = d.pageAtIndex(mark.page.toULong()) ?: continue
                val a = runCatching { AnnotationBuilder(page.geo()).build(mark) }.getOrNull() ?: continue
                a.userName = author
                a.modificationDate = NSDate()
                page.addAnnotation(a)
            }
        }
    }

    override fun deleteAnnotations(file: Path, targets: List<AnnotInfo>) {
        if (targets.isEmpty()) return
        edit(file) { d ->
            targets.groupBy { it.page }.forEach { (pi, items) ->
                val page = d.pageAtIndex(pi.toULong()) ?: return@forEach
                val list = page.annots()
                for (idx in items.map { it.index }.distinct().sortedDescending()) {
                    val a = list.getOrNull(idx) ?: continue
                    a.popup?.let { page.removeAnnotation(it) }
                    page.removeAnnotation(a)
                }
            }
        }
    }

    override fun deleteAllAnnotations(file: Path) = edit(file) { d ->
        for (page in d.pages()) {
            page.annots().filter { it.kind != "Link" && it.kind != "Widget" }.forEach { page.removeAnnotation(it) }
        }
    }

    // ------------------------------------------------------------------ strony

    override fun rotatePages(file: Path, pages: Collection<Int>, degrees: Int) = edit(file) { d ->
        for (i in pages) {
            val p = d.pageAtIndex(i.toULong()) ?: continue
            p.rotation = normalizeRotation(p.rotation.toInt() + degrees).toLong()
        }
    }

    override fun deletePages(file: Path, pages: Collection<Int>) = edit(file) { d ->
        require(pages.size < d.pageCount.toInt()) { tr("Dokument musi mieć co najmniej jedną stronę") }
        for (i in pages.sortedDescending()) if (i in 0 until d.pageCount.toInt()) d.removePageAtIndex(i.toULong())
    }

    override fun movePages(file: Path, pages: List<Int>, target: Int) = edit(file) { d ->
        val sorted = pages.sorted().filter { it in 0 until d.pageCount.toInt() }
        val moving = sorted.mapNotNull { d.pageAtIndex(it.toULong()) }
        for (i in sorted.sortedDescending()) d.removePageAtIndex(i.toULong())
        val at = target.coerceIn(0, d.pageCount.toInt())
        moving.forEachIndexed { k, p -> d.insertPage(p, atIndex = (at + k).toULong()) }
    }

    override fun duplicatePages(file: Path, pages: Collection<Int>) = edit(file) { d ->
        for (i in pages.sortedDescending()) {
            val src = d.pageAtIndex(i.toULong()) ?: continue
            val copy = src.copy() as? PDFPage ?: continue
            copy.annots().forEach { copy.removeAnnotation(it) }
            d.insertPage(copy, atIndex = (i + 1).toULong())
        }
    }

    /** Pusta strona [w]×[h] punktów. */
    private fun blankPages(count: Int, w: Double, h: Double): PDFDocument {
        val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, w, h), format = UIGraphicsPDFRendererFormat())
        val data = renderer.PDFDataWithActions { ctx -> repeat(count.coerceAtLeast(1)) { ctx?.beginPage() } }
        return PDFDocument(data = data)
    }

    override fun insertBlank(file: Path, at: Int) = edit(file) { d ->
        val n = d.pageCount.toInt()
        val ref = if (n > 0) d.pageAtIndex(at.coerceIn(0, n - 1).toULong()) else null
        var w = A4_WIDTH_PT.toDouble()
        var h = A4_HEIGHT_PT.toDouble()
        ref?.boundsForBox(kPDFDisplayBoxMediaBox)?.useContents {
            w = size.width
            h = size.height
        }
        if (ref != null && normalizeRotation(ref.rotation.toInt()) % 180 != 0) w = h.also { h = w }
        val page = blankPages(1, w, h).pageAtIndex(0u) ?: error(tr("Nie można utworzyć strony"))
        d.insertPage(page, atIndex = at.coerceIn(0, n).toULong())
    }

    override fun insertPdf(file: Path, other: Path, otherPassword: String?, at: Int?) = edit(file) { d ->
        val src = load(other, otherPassword)
        val before = d.pageCount.toInt()
        var pos = (at ?: before).coerceIn(0, before)
        for (p in src.pages()) {
            val copy = p.copy() as? PDFPage ?: continue
            d.insertPage(copy, atIndex = pos.toULong())
            pos++
        }
    }

    override fun extractPages(file: Path, pages: Collection<Int>, out: Path) {
        val d = load(file)
        val result = PDFDocument()
        var k = 0
        for (i in pages.sorted()) {
            val copy = d.pageAtIndex(i.toULong())?.copy() as? PDFPage ?: continue
            result.insertPage(copy, atIndex = (k++).toULong())
        }
        write(result, out)
    }

    /** Strona z obrazem: dłuższy bok jak w A4 (842 pt), proporcje obrazu zachowane. */
    private fun imagePage(f: Path): PDFPage? {
        val img = loadImage(f) ?: return null
        val (iw, ih) = img.size.useContents { width to height }
        if (iw <= 0.0 || ih <= 0.0) return null
        val long = 842.0
        val (w, h) = if (iw >= ih) long to long * ih / iw else long * iw / ih to long
        val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, w, h), format = UIGraphicsPDFRendererFormat())
        val data = renderer.PDFDataWithActions { ctx ->
            ctx?.beginPage()
            img.drawInRect(CGRectMake(0.0, 0.0, w, h))
        }
        return PDFDocument(data = data).pageAtIndex(0u)
    }

    override fun insertImages(file: Path, images: List<Path>, at: Int) = edit(file) { d ->
        var pos = at.coerceIn(0, d.pageCount.toInt())
        for (f in images) {
            val p = imagePage(f) ?: continue
            d.insertPage(p, atIndex = (pos++).toULong())
        }
    }

    override fun createFromImages(images: List<Path>, out: Path) {
        val d = PDFDocument()
        var k = 0
        for (f in images) {
            val p = imagePage(f) ?: continue
            d.insertPage(p, atIndex = (k++).toULong())
        }
        require(k > 0) { tr("Nie udało się odczytać żadnego obrazu") }
        write(d, out)
    }

    override fun createBlank(out: Path, pages: Int, widthPt: Float, heightPt: Float) {
        write(blankPages(pages, widthPt.toDouble(), heightPt.toDouble()), out)
    }

    // ------------------------------------------------------------------ formularze, metadane, ochrona

    override fun fillFields(file: Path, edits: List<FieldEdit>): List<String> = edit(file) { d ->
        val byName = HashMap<String, MutableList<PDFAnnotation>>()
        for (page in d.pages()) for (a in page.annots()) {
            if (a.kind == "Widget") a.fieldName?.let { byName.getOrPut(it) { ArrayList() } += a }
        }
        val failed = ArrayList<String>()
        for (e in edits) {
            val list = byName[e.name]
            if (list.isNullOrEmpty()) {
                failed += e.name
                continue
            }
            when (widgetKind(list.first())) {
                FieldKind.TEXT, FieldKind.CHOICE -> list.forEach { it.widgetStringValue = e.value }
                FieldKind.CHECKBOX -> list.forEach { it.buttonWidgetState = if (e.value == "true") kPDFWidgetOnState else kPDFWidgetOffState }
                FieldKind.RADIO -> list.forEach { it.buttonWidgetState = if (it.buttonWidgetStateString == e.value) kPDFWidgetOnState else kPDFWidgetOffState }
                else -> failed += e.name
            }
        }
        failed
    }

    override fun flattenForm(file: Path) {
        val d = load(file)
        val tmp = file.parent!! / (file.name + ".tmp")
        // PDFKit wtapia przy zapisie wszystkie adnotacje (w tym pola formularza) w treść stron.
        write(d, tmp, mapOf<Any?, Any?>(PDFDocumentBurnInAnnotationsOption to true))
        fs.atomicMove(tmp, file)
    }

    override fun setInfo(file: Path, e: InfoEdit) = edit(file) { d ->
        val attrs = HashMap<Any?, Any?>()
        d.documentAttributes?.forEach { (k, v) -> attrs[k] = v }
        fun set(k: Any?, v: String) {
            if (v.isBlank()) attrs.remove(k) else attrs[k] = v
        }
        set(PDFDocumentTitleAttribute, e.title)
        set(PDFDocumentAuthorAttribute, e.author)
        set(PDFDocumentSubjectAttribute, e.subject)
        val keywords = e.keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) attrs.remove(PDFDocumentKeywordsAttribute) else attrs[PDFDocumentKeywordsAttribute] = keywords
        attrs[PDFDocumentModificationDateAttribute] = NSDate()
        d.documentAttributes = attrs
    }

    override fun export(file: Path, out: Path, userPassword: String?, ownerPassword: String?) {
        val d = load(file)
        val attrs = HashMap<Any?, Any?>()
        d.documentAttributes?.forEach { (k, v) -> attrs[k] = v }
        attrs[PDFDocumentProducerAttribute] = "OpenPDF"
        d.documentAttributes = attrs
        if (userPassword.isNullOrEmpty() && ownerPassword.isNullOrEmpty()) {
            write(d, out)
        } else {
            val options = HashMap<Any?, Any?>()
            options[PDFDocumentOwnerPasswordOption] = ownerPassword?.takeIf { it.isNotEmpty() } ?: userPassword
            if (!userPassword.isNullOrEmpty()) options[PDFDocumentUserPasswordOption] = userPassword
            write(d, out, options)
        }
    }

    override fun compress(file: Path, out: Path, maxSide: Int, quality: Float, onProgress: (Float) -> Unit): Int {
        val d = load(file)
        onProgress(0.3f)
        write(
            d,
            out,
            mapOf<Any?, Any?>(
                PDFDocumentSaveImagesAsJPEGOption to true,
                PDFDocumentOptimizeImagesForScreenOption to true,
            ),
        )
        onProgress(1f)
        return -1
    }

    override fun imageToPng(src: Path, maxSide: Int, out: Path): Float {
        val img = loadImage(src) ?: error(tr("Nie można odczytać obrazu"))
        val (iw, ih) = img.size.useContents { width to height }
        if (iw <= 0.0 || ih <= 0.0) error(tr("Nie można odczytać obrazu"))
        val pxW = iw * img.scale
        val pxH = ih * img.scale
        val k = min(1.0, maxSide / max(pxW, pxH))
        val w = max(1.0, kotlin.math.floor(pxW * k))
        val h = max(1.0, kotlin.math.floor(pxH * k))
        val format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format)
        val scaled = renderer.imageWithActions { _ -> img.drawInRect(CGRectMake(0.0, 0.0, w, h)) }
        val data = UIImagePNGRepresentation(scaled) ?: error(tr("Nie można odczytać obrazu"))
        if (!data.writeToFile(out.toString(), atomically = true)) error(tr("Nie można zapisać pliku {0}", out.name))
        return (w / h).toFloat()
    }
}

internal fun loadImage(f: Path): UIImage? = try {
    val img: UIImage? = UIImage(contentsOfFile = f.toString())
    img
} catch (_: Throwable) {
    null
}

/** Tekst stron z położeniem znaków (PDFKit). */
private class IosText(private val doc: PDFDocument) : PageTextSource {
    override fun page(index: Int, geo: PageGeo): PageText? {
        val p = doc.pageAtIndex(index.toULong()) ?: return null
        val s = p.string ?: return PageText("", FloatArray(0))
        val boxes = FloatArray(s.length * 4)
        for (i in s.indices) {
            val r = p.characterBoundsAtIndex(i.toLong()).useContents {
                if (size.width <= 0.0 || size.height <= 0.0 || s[i].isWhitespace()) {
                    null
                } else {
                    geo.rectFromPdf(origin.x.toFloat(), origin.y.toFloat(), (origin.x + size.width).toFloat(), (origin.y + size.height).toFloat())
                }
            }
            if (r == null) {
                boxes.fill(Float.NaN, i * 4, i * 4 + 4)
            } else {
                boxes[i * 4] = r.left
                boxes[i * 4 + 1] = r.top
                boxes[i * 4 + 2] = r.right
                boxes[i * 4 + 3] = r.bottom
            }
        }
        return PageText(s, boxes)
    }

    override fun close() {}
}

/**
 * Zamienia oczekujący element ([Mark]) w adnotację PDFKit. Współrzędne liczone w przestrzeni
 * strony PDF (jak na Androidzie), punkty ścieżek – względem początku prostokąta adnotacji.
 */
private class AnnotationBuilder(private val geo: PageGeo) {
    private val dW = geo.displayWidth

    private fun pdf(p: PointN): Pair<Float, Float> = geo.toPdf(p)

    private fun pdfRect(r: RectN): FloatArray = geo.rectToPdf(r)

    fun build(mark: Mark): PDFAnnotation? = when (mark) {
        is InkMark -> ink(mark.strokes, mark.color, mark.widthPt, mark.highlighter, mark.bounds())
        is SignatureMark -> {
            val strokes = mark.strokes.map { s ->
                s.map { PointN(mark.rect.left + it.x * mark.rect.width, mark.rect.top + it.y * mark.rect.height) }
            }
            ink(strokes, mark.color, mark.widthPt, false, mark.rect.inflate(mark.widthPt / dW))
        }
        is ShapeMark -> shape(mark)
        is TextMark -> freeText(mark)
        is NoteMark -> note(mark)
        is MarkupMark -> markup(mark)
        is ImageMark -> image(mark)
    }

    private fun annotation(bounds: RectN, subtype: String?): Pair<PDFAnnotation, FloatArray> {
        val r = pdfRect(bounds)
        r[2] = max(r[2], r[0] + 1f)
        r[3] = max(r[3], r[1] + 1f)
        return PDFAnnotation(bounds = rectOf(r[0], r[1], r[2], r[3]), forType = subtype!!, withProperties = null) to r
    }

    private fun border(width: Float) = PDFBorder().apply { lineWidth = width.toDouble() }

    private fun ink(strokes: List<List<PointN>>, color: Int, widthPt: Float, highlighter: Boolean, bounds: RectN): PDFAnnotation? {
        if (strokes.isEmpty()) return null
        val (a, r) = annotation(bounds, PDFAnnotationSubtypeInk)
        a.color = uiColor(color, if (highlighter) 0.45f else null)
        a.border = border(widthPt)
        for (s in strokes) {
            if (s.isEmpty()) continue
            val path = UIBezierPath()
            path.lineWidth = widthPt.toDouble()
            path.lineCapStyle = if (highlighter) CGLineCap.kCGLineCapSquare else CGLineCap.kCGLineCapRound
            path.lineJoinStyle = CGLineJoin.kCGLineJoinRound
            s.forEachIndexed { i, p ->
                val (x, y) = pdf(p)
                val pt = CGPointMake((x - r[0]).toDouble(), (y - r[1]).toDouble())
                if (i == 0) path.moveToPoint(pt) else path.addLineToPoint(pt)
            }
            if (s.size == 1) {
                val (x, y) = pdf(s[0])
                path.addLineToPoint(CGPointMake((x - r[0]).toDouble() + 0.01, (y - r[1]).toDouble()))
            }
            a.addBezierPath(path)
        }
        return a
    }

    private fun shape(m: ShapeMark): PDFAnnotation {
        val subtype = when (m.kind) {
            ShapeKind.RECT -> PDFAnnotationSubtypeSquare
            ShapeKind.ELLIPSE -> PDFAnnotationSubtypeCircle
            ShapeKind.LINE, ShapeKind.ARROW -> PDFAnnotationSubtypeLine
        }
        val (a, r) = annotation(m.bounds(), subtype)
        a.color = uiColor(m.color)
        a.border = border(m.widthPt)
        if (m.fill != null && (m.kind == ShapeKind.RECT || m.kind == ShapeKind.ELLIPSE)) a.interiorColor = uiColor(m.fill)
        if (m.kind == ShapeKind.LINE || m.kind == ShapeKind.ARROW) {
            val (x1, y1) = pdf(m.start)
            val (x2, y2) = pdf(m.end)
            a.startPoint = CGPointMake((x1 - r[0]).toDouble(), (y1 - r[1]).toDouble())
            a.endPoint = CGPointMake((x2 - r[0]).toDouble(), (y2 - r[1]).toDouble())
            a.startLineStyle = kPDFLineStyleNone
            a.endLineStyle = if (m.kind == ShapeKind.ARROW) kPDFLineStyleOpenArrow else kPDFLineStyleNone
        }
        return a
    }

    private fun freeText(m: TextMark): PDFAnnotation {
        val (a, _) = annotation(m.rect, PDFAnnotationSubtypeFreeText)
        a.contents = m.text
        a.font = UIFont.systemFontOfSize(m.sizePt.toDouble())
        a.fontColor = uiColor(m.color)
        a.color = if (m.background != null) uiColor(m.background) else UIColor.clearColor
        a.border = border(0f)
        return a
    }

    private fun note(m: NoteMark): PDFAnnotation {
        val (a, _) = annotation(m.bounds(), PDFAnnotationSubtypeText)
        a.contents = m.text
        a.color = uiColor(m.color)
        a.iconType = kPDFTextAnnotationIconComment
        return a
    }

    private fun markup(m: MarkupMark): PDFAnnotation {
        val subtype = when (m.kind) {
            MarkupKind.HIGHLIGHT -> PDFAnnotationSubtypeHighlight
            MarkupKind.UNDERLINE -> PDFAnnotationSubtypeUnderline
            MarkupKind.STRIKEOUT -> PDFAnnotationSubtypeStrikeOut
        }
        val (a, r) = annotation(m.bounds(), subtype)
        a.color = uiColor(m.color, if (m.kind == MarkupKind.HIGHLIGHT) 0.55f else null)
        a.contents = m.text
        val quads = ArrayList<NSValue>()
        for (q in m.rects) {
            // Kolejność narożników jak w Acrobacie: lewy górny, prawy górny, lewy dolny, prawy dolny.
            for (p in listOf(PointN(q.left, q.top), PointN(q.right, q.top), PointN(q.left, q.bottom), PointN(q.right, q.bottom))) {
                val (x, y) = pdf(p)
                quads += NSValue.valueWithCGPoint(CGPointMake((x - r[0]).toDouble(), (y - r[1]).toDouble()))
            }
        }
        a.quadrilateralPoints = quads
        return a
    }

    private fun image(m: ImageMark): PDFAnnotation? {
        val img = loadImage(m.path.toPath()) ?: return null
        val r = pdfRect(m.rect)
        return ImageStamp(rectOf(r[0], r[1], r[2], r[3]), img, geo.rotation)
    }
}

/** Pieczątka z obrazem: PDFKit zapisuje jej wygląd z rysowania [drawWithBox]. */
private class ImageStamp(bounds: CValue<CGRect>, private val image: UIImage, private val rotation: Int) :
    PDFAnnotation(bounds = bounds, forType = PDFAnnotationSubtypeStamp!!, withProperties = null) {
    override fun drawWithBox(box: PDFDisplayBox, inContext: CGContextRef?) {
        val ctx = inContext ?: return
        val cg = image.CGImage ?: return
        val (x, y, w, h) = bounds.useContents { listOf(origin.x, origin.y, size.width, size.height) }
        CGContextSaveGState(ctx)
        // Obraz stoi prosto na obróconej stronie: obrót wokół środka prostokąta.
        CGContextTranslateCTM(ctx, x + w / 2, y + h / 2)
        CGContextRotateCTM(ctx, rotation * PI / 180.0)
        val (dw, dh) = if (rotation % 180 != 0) h to w else w to h
        CGContextDrawImage(ctx, CGRectMake(-dw / 2, -dh / 2, dw, dh), cg)
        CGContextRestoreGState(ctx)
    }
}
