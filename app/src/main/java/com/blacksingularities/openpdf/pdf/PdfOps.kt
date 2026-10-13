package com.blacksingularities.openpdf.pdf

import com.blacksingularities.openpdf.i18n.tr
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Operacje na plikach PDF (PdfBox). Każda funkcja otwiera plik, zmienia go i zapisuje. */
object PdfOps {
    private val memory: MemoryUsageSetting get() = MemoryUsageSetting.setupMixed(48L * 1024 * 1024)

    fun load(file: File, password: String? = null): PDDocument =
        PDDocument.load(file, password ?: "", memory)

    /** Otwiera plik, wykonuje [block] i zapisuje wynik do [out] (domyślnie w miejsce pliku, przez plik tymczasowy). */
    inline fun <T> edit(file: File, out: File = file, block: (PDDocument) -> T): T {
        val tmp = File(out.parentFile, out.name + ".tmp")
        val result = load(file).use { doc ->
            val r = block(doc)
            doc.save(tmp)
            r
        }
        if (out.exists() && !out.delete()) error(tr("Nie można zastąpić pliku {0}", out.name))
        if (!tmp.renameTo(out)) error(tr("Nie można zapisać pliku {0}", out.name))
        return result
    }

    // ------------------------------------------------------------------ otwieranie

    /** Czy plik jest zaszyfrowany i wymaga hasła do otwarcia. */
    fun needsPassword(file: File): Boolean = try {
        load(file).close()
        false
    } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        true
    }

    /**
     * Przygotowuje kopię roboczą: odszyfrowuje (hasło jest zapamiętywane w sesji), a uszkodzone
     * pliki zapisuje na nowo, co zwykle naprawia tablicę odsyłaczy.
     * Zwraca true, gdy oryginał był zaszyfrowany.
     */
    fun prepareWorking(original: File, password: String?, working: File): Boolean {
        load(original, password).use { doc ->
            val encrypted = doc.isEncrypted
            if (encrypted) {
                doc.isAllSecurityToBeRemoved = true
                doc.save(working)
            } else {
                original.copyTo(working, overwrite = true)
            }
            return encrypted
        }
    }

    /** Zapisuje plik na nowo przez PdfBox (naprawa plików, których nie otwiera systemowy renderer). */
    fun rewrite(file: File, out: File) {
        load(file).use { it.save(out) }
    }

    // ------------------------------------------------------------------ indeks i kopia do wyświetlania

    /**
     * Czyta strukturę dokumentu (geometria stron, adnotacje, odnośniki, spis treści, formularz)
     * i tworzy [display] – kopię, w której adnotacje są wtopione w treść, a obrót i przycięcie
     * stron wypieczone. Systemowy renderer nie rysuje adnotacji, a obrót bywa różnie interpretowany.
     * Zwraca indeks i informację, czy kopia była potrzebna (gdy nie – wyświetlany jest [source]).
     */
    fun buildIndex(source: File, display: File): Pair<DocIndex, Boolean> {
        load(source).use { doc ->
            val pages = doc.pages
            val geos = ArrayList<PageGeo>(pages.count)
            val annots = ArrayList<List<AnnotInfo>>(pages.count)
            val links = ArrayList<List<LinkInfo>>(pages.count)
            var needsDisplay = false
            for ((pi, page) in pages.withIndex()) {
                val crop = page.cropBox
                val media = page.mediaBox
                val geo = PageGeo(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, normalizeRotation(page.rotation))
                geos += geo
                if (geo.rotation != 0 || geo.llx != 0f || geo.lly != 0f ||
                    media.lowerLeftX != 0f || media.lowerLeftY != 0f ||
                    media.width != crop.width || media.height != crop.height
                ) {
                    needsDisplay = true
                }
                val pageAnnots = ArrayList<AnnotInfo>()
                val pageLinks = ArrayList<LinkInfo>()
                val list = runCatching { page.annotations }.getOrNull().orEmpty()
                list.forEachIndexed { ai, a ->
                    val r = a.rectangle ?: return@forEachIndexed
                    val rect = geo.rectFromPdf(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY)
                    val subtype = a.subtype ?: ""
                    when {
                        a is PDAnnotationLink -> {
                            val (target, uri) = resolveLink(doc, a)
                            if (target != null || uri != null) pageLinks += LinkInfo(rect, target, uri)
                        }
                        subtype == "Popup" -> Unit
                        else -> {
                            if (subtype != "Widget") {
                                pageAnnots += AnnotInfo(
                                    page = pi,
                                    index = ai,
                                    subtype = subtype,
                                    rect = rect,
                                    contents = a.contents ?: "",
                                    author = a.cosObject.getString(COSName.T) ?: "",
                                    color = colorOf(a.cosObject),
                                )
                            }
                            if (!a.isHidden && !a.isNoView) needsDisplay = true
                        }
                    }
                }
                annots += pageAnnots
                links += pageLinks
            }
            val outline = ArrayList<OutlineEntry>()
            runCatching {
                doc.documentCatalog.documentOutline?.let { readOutline(doc, it, 0, outline) }
            }
            val fields = runCatching { readFields(doc) }.getOrDefault(emptyList())
            val info = runCatching { readInfo(doc) }.getOrDefault(DocInfo())
            val index = DocIndex(geos, annots, links, outline, fields, info)
            if (needsDisplay) {
                makeDisplayCopy(doc, geos)
                doc.save(display)
            }
            return index to needsDisplay
        }
    }

    private fun resolveLink(doc: PDDocument, link: PDAnnotationLink): Pair<Int?, String?> = try {
        when (val action = link.action) {
            is PDActionURI -> null to action.uri
            is PDActionGoTo -> destinationPage(doc, action.destination) to null
            else -> destinationPage(doc, link.destination) to null
        }
    } catch (_: Exception) {
        null to null
    }

    private fun destinationPage(doc: PDDocument, dest: PDDestination?): Int? {
        val d = when (dest) {
            is PDNamedDestination -> doc.documentCatalog.findNamedDestinationPage(dest)
            is PDPageDestination -> dest
            else -> null
        } ?: return null
        val n = d.retrievePageNumber()
        if (n >= 0) return n
        return d.page?.let { doc.pages.indexOf(it) }?.takeIf { it >= 0 }
    }

    private fun readOutline(doc: PDDocument, node: PDOutlineNode, depth: Int, out: MutableList<OutlineEntry>) {
        if (depth > 12 || out.size > 5000) return
        for (item in node.children()) {
            val page = runCatching {
                item.findDestinationPage(doc)?.let { p -> doc.pages.indexOf(p).takeIf { it >= 0 } }
                    ?: (item.action as? PDActionGoTo)?.let { destinationPage(doc, it.destination) }
            }.getOrNull()
            out += OutlineEntry(item.title ?: "", page, depth)
            if (item.hasChildren()) readOutline(doc, item, depth + 1, out)
        }
    }

    private fun readInfo(doc: PDDocument): DocInfo {
        val i = doc.documentInformation
        return DocInfo(
            title = i.title ?: "",
            author = i.author ?: "",
            subject = i.subject ?: "",
            keywords = i.keywords ?: "",
            creator = i.creator ?: "",
            producer = i.producer ?: "",
            version = doc.version.toString(),
            created = runCatching { i.creationDate?.timeInMillis }.getOrNull(),
            modified = runCatching { i.modificationDate?.timeInMillis }.getOrNull(),
        )
    }

    private fun readFields(doc: PDDocument): List<FieldInfo> {
        val form = doc.documentCatalog.acroForm ?: return emptyList()
        val pageIndex = HashMap<COSDictionary, Int>()
        doc.pages.forEachIndexed { i, p -> pageIndex[p.cosObject] = i }
        val out = ArrayList<FieldInfo>()
        for (field in form.fieldTree) {
            if (field !is PDTerminalField) continue
            val widgets = field.widgets.mapNotNull { w ->
                val r = w.rectangle ?: return@mapNotNull null
                var pi = w.page?.let { pageIndex[it.cosObject] }
                if (pi == null) {
                    // Brak /P – szukamy widżetu na listach adnotacji stron.
                    pi = doc.pages.indexOfFirst { p ->
                        runCatching { p.annotations.any { it.cosObject == w.cosObject } }.getOrDefault(false)
                    }.takeIf { it >= 0 }
                }
                pi ?: return@mapNotNull null
                val geo = doc.getPage(pi).let { p ->
                    PageGeo(p.cropBox.lowerLeftX, p.cropBox.lowerLeftY, p.cropBox.width, p.cropBox.height, normalizeRotation(p.rotation))
                }
                FieldWidget(pi, geo.rectFromPdf(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY))
            }
            val kind = when (field) {
                is PDTextField -> FieldKind.TEXT
                is PDCheckBox -> FieldKind.CHECKBOX
                is PDRadioButton -> FieldKind.RADIO
                is PDChoice -> FieldKind.CHOICE
                is PDSignatureField -> FieldKind.SIGNATURE
                is PDPushButton -> FieldKind.BUTTON
                else -> continue
            }
            val (options, exports) = when (field) {
                is PDChoice -> field.optionsDisplayValues.toList() to field.optionsExportValues.toList()
                is PDRadioButton -> runCatching { field.onValues.toList() }.getOrDefault(emptyList()).let { it to it }
                else -> emptyList<String>() to emptyList()
            }
            val value = when (field) {
                is PDCheckBox -> if (field.isChecked) "true" else "false"
                is PDChoice -> field.value.joinToString(", ")
                else -> runCatching { field.valueAsString }.getOrDefault("")
            }
            out += FieldInfo(
                name = field.fullyQualifiedName ?: "",
                label = field.alternateFieldName?.takeIf { it.isNotBlank() } ?: (field.partialName ?: ""),
                kind = kind,
                value = value,
                options = options,
                exportOptions = exports,
                readOnly = field.isReadOnly,
                multiline = (field as? PDTextField)?.isMultiline == true,
                widgets = widgets,
            )
        }
        return out
    }

    private fun colorOf(dict: COSDictionary): Int? {
        val arr = dict.getDictionaryObject(COSName.C) as? COSArray ?: return null
        val v = arr.toFloatArray()
        return when (v.size) {
            1 -> argb(v[0], v[0], v[0])
            3 -> argb(v[0], v[1], v[2])
            4 -> argb((1 - v[0]) * (1 - v[3]), (1 - v[1]) * (1 - v[3]), (1 - v[2]) * (1 - v[3]))
            else -> null
        }
    }

    private fun argb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or ((r * 255).toInt().coerceIn(0, 255) shl 16) or ((g * 255).toInt().coerceIn(0, 255) shl 8) or (b * 255).toInt().coerceIn(0, 255)

    /** Wtapia wygląd adnotacji w treść stron i wypieka obrót oraz przycięcie (tylko kopia do wyświetlania). */
    private fun makeDisplayCopy(doc: PDDocument, geos: List<PageGeo>) {
        runCatching {
            val form = doc.documentCatalog.acroForm
            if (form != null && form.needAppearances) form.refreshAppearances()
        }
        for ((pi, page) in doc.pages.withIndex()) {
            val geo = geos[pi]
            val list = runCatching { page.annotations }.getOrNull().orEmpty()
            val visible = list.filter { a ->
                val st = a.subtype
                st != "Popup" && st != "Link" && !a.isHidden && !a.isNoView && a.rectangle != null
            }
            if (visible.isNotEmpty()) {
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (a in visible) runCatching { flattenAnnotation(doc, cs, a) }
                }
            }
            page.annotations = ArrayList()
            if (geo.rotation != 0 || geo.llx != 0f || geo.lly != 0f || page.mediaBox.width != geo.width || page.mediaBox.height != geo.height ||
                page.mediaBox.lowerLeftX != 0f || page.mediaBox.lowerLeftY != 0f
            ) {
                val m = geo.normalizingMatrix()
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.PREPEND, true).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(Matrix(m[0], m[1], m[2], m[3], m[4], m[5]))
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true).use { cs ->
                    cs.restoreGraphicsState()
                }
                val box = PDRectangle(0f, 0f, geo.displayWidth, geo.displayHeight)
                page.mediaBox = box
                page.cropBox = box
                page.cosObject.removeItem(COSName.BLEED_BOX)
                page.cosObject.removeItem(COSName.TRIM_BOX)
                page.cosObject.removeItem(COSName.ART_BOX)
                page.rotation = 0
            }
        }
    }

    private fun flattenAnnotation(doc: PDDocument, cs: PDPageContentStream, a: PDAnnotation) {
        val rect = a.rectangle
        val ap = a.normalAppearanceStream
        if (ap == null) {
            // Notatki bez własnego wyglądu – rysujemy prostą ikonę dymku.
            if (a.subtype == "Text") drawNoteIcon(cs, rect, colorOf(a.cosObject) ?: 0xFFFFD54F.toInt())
            return
        }
        val bbox = ap.bBox ?: return
        val m = ap.matrix
        val a0 = m.getValue(0, 0)
        val b0 = m.getValue(0, 1)
        val c0 = m.getValue(1, 0)
        val d0 = m.getValue(1, 1)
        val e0 = m.getValue(2, 0)
        val f0 = m.getValue(2, 1)
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        val corners = arrayOf(
            floatArrayOf(bbox.lowerLeftX, bbox.lowerLeftY),
            floatArrayOf(bbox.upperRightX, bbox.lowerLeftY),
            floatArrayOf(bbox.upperRightX, bbox.upperRightY),
            floatArrayOf(bbox.lowerLeftX, bbox.upperRightY),
        )
        for (i in 0 until 4) {
            val (x, y) = corners[i].let { it[0] to it[1] }
            xs[i] = a0 * x + c0 * y + e0
            ys[i] = b0 * x + d0 * y + f0
        }
        val tMinX = xs.min()
        val tMinY = ys.min()
        val tw = xs.max() - tMinX
        val th = ys.max() - tMinY
        if (tw <= 0f || th <= 0f) return
        val sx = rect.width / tw
        val sy = rect.height / th
        cs.saveGraphicsState()
        cs.transform(Matrix(sx, 0f, 0f, sy, rect.lowerLeftX - sx * tMinX, rect.lowerLeftY - sy * tMinY))
        cs.drawForm(ap)
        cs.restoreGraphicsState()
    }

    // ------------------------------------------------------------------ zapis adnotacji

    /** Zapisuje oczekujące elementy jako adnotacje PDF z wyglądem (widoczne w każdym czytniku). */
    fun commitMarks(file: File, marks: List<Mark>, author: String, fontFiles: List<File>) {
        if (marks.isEmpty()) return
        edit(file) { doc ->
            val fonts = FontBox(doc, fontFiles)
            for (mark in marks) {
                if (mark.page !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(mark.page)
                val geo = PageGeo(page.cropBox.lowerLeftX, page.cropBox.lowerLeftY, page.cropBox.width, page.cropBox.height, normalizeRotation(page.rotation))
                val annot = AnnotationWriter(doc, geo, author, fonts).build(mark) ?: continue
                val list = page.annotations
                list.add(annot)
                page.annotations = list
            }
        }
    }

    /** Usuwa istniejące adnotacje (strona → pozycje na liście /Annots). */
    fun deleteAnnotations(file: File, targets: List<AnnotInfo>) {
        if (targets.isEmpty()) return
        edit(file) { doc ->
            targets.groupBy { it.page }.forEach { (pi, items) ->
                if (pi !in 0 until doc.numberOfPages) return@forEach
                val page = doc.getPage(pi)
                val list = page.annotations.toMutableList()
                val popups = HashSet<COSBase>()
                for (idx in items.map { it.index }.distinct().sortedDescending()) {
                    if (idx in list.indices) {
                        val removed = list.removeAt(idx)
                        removed.cosObject.getDictionaryObject(COSName.getPDFName("Popup"))?.let { popups += it }
                    }
                }
                list.removeAll { it.cosObject in popups }
                page.annotations = list
            }
        }
    }

    fun deleteAllAnnotations(file: File) {
        edit(file) { doc ->
            for (page in doc.pages) {
                val list = page.annotations.filter { it.subtype == "Link" || it.subtype == "Widget" }
                page.annotations = list
            }
        }
    }

    // ------------------------------------------------------------------ strony

    fun rotatePages(file: File, pages: Collection<Int>, degrees: Int) = edit(file) { doc ->
        for (i in pages) if (i in 0 until doc.numberOfPages) {
            val p = doc.getPage(i)
            p.rotation = normalizeRotation(p.rotation + degrees)
        }
    }

    fun deletePages(file: File, pages: Collection<Int>) = edit(file) { doc ->
        require(pages.size < doc.numberOfPages) { tr("Dokument musi mieć co najmniej jedną stronę") }
        for (i in pages.sortedDescending()) if (i in 0 until doc.numberOfPages) doc.removePage(i)
    }

    /** Przenosi strony [pages] (w kolejności) tak, by zaczynały się na pozycji [target] (indeks w dokumencie po usunięciu). */
    fun movePages(file: File, pages: List<Int>, target: Int) = edit(file) { doc ->
        val moving = pages.sorted().map { doc.getPage(it) }
        for (p in moving) doc.pages.remove(p)
        val at = target.coerceIn(0, doc.numberOfPages)
        insertAt(doc, moving, at)
    }

    internal fun insertAt(doc: PDDocument, pages: List<PDPage>, at: Int) {
        if (at >= doc.numberOfPages) {
            pages.forEach { doc.pages.add(it) }
        } else {
            val next = doc.getPage(at)
            pages.forEach { doc.pages.insertBefore(it, next) }
        }
    }

    fun duplicatePages(file: File, pages: Collection<Int>) = edit(file) { doc ->
        for (i in pages.sortedDescending()) {
            val src = doc.getPage(i)
            val dict = COSDictionary(src.cosObject)
            dict.removeItem(COSName.ANNOTS)
            dict.removeItem(COSName.PARENT)
            val copy = PDPage(dict)
            // Atrybuty dziedziczone z drzewa stron muszą trafić do kopii wprost.
            copy.mediaBox = src.mediaBox
            copy.cropBox = src.cropBox
            copy.resources = src.resources
            copy.rotation = src.rotation
            doc.pages.insertAfter(copy, src)
        }
    }

    /** Wstawia pustą stronę o rozmiarze sąsiedniej (albo A4) na pozycji [at]. */
    fun insertBlank(file: File, at: Int, landscape: Boolean? = null) = edit(file) { doc ->
        val ref = if (doc.numberOfPages > 0) doc.getPage(at.coerceIn(0, doc.numberOfPages - 1)) else null
        var w = ref?.mediaBox?.width ?: PDRectangle.A4.width
        var h = ref?.mediaBox?.height ?: PDRectangle.A4.height
        if (ref != null && normalizeRotation(ref.rotation) % 180 != 0) w = h.also { h = w }
        if (landscape != null && landscape != (w > h)) w = h.also { h = w }
        insertAt(doc, listOf(PDPage(PDRectangle(w, h))), at)
    }

    /** Wstawia strony innego pliku PDF na pozycji [at] (domyślnie na końcu). */
    fun insertPdf(file: File, other: File, otherPassword: String?, at: Int?) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        load(file).use { doc ->
            // Źródło musi być otwarte, dopóki strony nie zostaną zapisane.
            load(other, otherPassword).use { src ->
                if (src.isEncrypted) src.isAllSecurityToBeRemoved = true
                val before = doc.numberOfPages
                PDFMergerUtility().appendDocument(doc, src)
                val target = at ?: before
                if (target < before) {
                    val added = (before until doc.numberOfPages).map { doc.getPage(it) }
                    added.forEach { doc.pages.remove(it) }
                    insertAt(doc, added, target)
                }
                doc.save(tmp)
            }
        }
        if (!file.delete() || !tmp.renameTo(file)) error(tr("Nie można zapisać pliku {0}", file.name))
    }

    /** Zapisuje wybrane strony do nowego pliku. */
    fun extractPages(file: File, pages: Collection<Int>, out: File) {
        val keep = pages.toSet()
        load(file).use { doc ->
            for (i in doc.numberOfPages - 1 downTo 0) if (i !in keep) doc.removePage(i)
            doc.save(out)
        }
    }

    /** Wstawia obrazy jako nowe strony na pozycji [at]. */
    fun insertImages(file: File, images: List<File>, at: Int) = edit(file) { doc ->
        val created = images.mapNotNull { imagePage(doc, it) }
        insertAt(doc, created, at)
    }

    /** Tworzy nowy dokument z obrazów (po jednym na stronę). */
    fun createFromImages(images: List<File>, out: File) {
        PDDocument(memory).use { doc ->
            for (f in images) imagePage(doc, f)?.let { doc.addPage(it) }
            require(doc.numberOfPages > 0) { tr("Nie udało się odczytać żadnego obrazu") }
            doc.save(out)
        }
    }

    fun createBlank(out: File, pages: Int, size: PDRectangle) {
        PDDocument(memory).use { doc ->
            repeat(pages.coerceAtLeast(1)) { doc.addPage(PDPage(PDRectangle(size.width, size.height))) }
            doc.save(out)
        }
    }

    private fun imagePage(doc: PDDocument, f: File): PDPage? {
        val bmp = decodeScaled(f, 3000) ?: return null
        try {
            val img = if (bmp.hasAlpha()) LosslessFactory.createFromImage(doc, bmp) else JPEGFactory.createFromImage(doc, bmp, 0.9f)
            // Dłuższy bok jak w A4 (842 pt), proporcje obrazu zachowane.
            val long = 842f
            val (w, h) = if (bmp.width >= bmp.height) long to long * bmp.height / bmp.width else long * bmp.width / bmp.height to long
            val page = PDPage(PDRectangle(w, h))
            PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, 0f, 0f, w, h) }
            return page
        } finally {
            bmp.recycle()
        }
    }

    fun decodeScaled(f: File, maxSide: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / sample > maxSide * 2) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val longSide = max(bmp.width, bmp.height)
        if (longSide <= maxSide) return bmp
        val s = maxSide.toFloat() / longSide
        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
        if (scaled != bmp) bmp.recycle()
        return scaled
    }

    // ------------------------------------------------------------------ formularze, metadane, ochrona

    /** Ustawia wartości pól formularza. Zwraca listę pól, których nie udało się wypełnić. */
    fun fillFields(file: File, edits: List<FieldEdit>): List<String> = edit(file) { doc ->
        val form = doc.documentCatalog.acroForm ?: return@edit edits.map { it.name }
        val failed = ArrayList<String>()
        for (e in edits) {
            val field: PDField? = form.getField(e.name)
            if (field == null) {
                failed += e.name
                continue
            }
            try {
                when (field) {
                    is PDCheckBox -> if (e.value == "true") field.check() else field.unCheck()
                    is PDRadioButton -> field.setValue(e.value)
                    is PDChoice -> field.setValue(e.value)
                    is PDTextField -> field.setValue(e.value)
                    else -> failed += e.name
                }
            } catch (_: Exception) {
                failed += e.name
            }
        }
        failed
    }

    fun flattenForm(file: File) = edit(file) { doc ->
        val form = doc.documentCatalog.acroForm ?: return@edit
        runCatching { form.refreshAppearances() }
        form.flatten()
    }

    fun setInfo(file: File, e: InfoEdit) = edit(file) { doc ->
        val i = doc.documentInformation
        i.title = e.title.ifBlank { null }
        i.author = e.author.ifBlank { null }
        i.subject = e.subject.ifBlank { null }
        i.keywords = e.keywords.ifBlank { null }
        i.modificationDate = Calendar.getInstance()
        doc.documentInformation = i
    }

    /** Zapisuje dokument do [out], opcjonalnie szyfrując go hasłem (AES-256). */
    fun export(file: File, out: File, userPassword: String?, ownerPassword: String?) {
        load(file).use { doc ->
            doc.documentInformation.producer = "OpenPDF"
            if (!userPassword.isNullOrEmpty() || !ownerPassword.isNullOrEmpty()) {
                val owner = ownerPassword?.takeIf { it.isNotEmpty() } ?: userPassword!!
                val policy = StandardProtectionPolicy(owner, userPassword ?: "", AccessPermission())
                policy.encryptionKeyLength = 256
                doc.protect(policy)
            }
            doc.save(out)
        }
    }

    /**
     * Zmniejsza plik: obrazy większe niż [maxSide] px są skalowane i zapisywane jako JPEG o jakości
     * [quality]. Obrazy z przezroczystością i maskami są pomijane. Zwraca liczbę zmienionych obrazów.
     */
    fun compress(file: File, out: File, maxSide: Int, quality: Float, onProgress: (Float) -> Unit): Int {
        var changed = 0
        load(file).use { doc ->
            val done = HashMap<COSBase, PDImageXObject>()
            val n = doc.numberOfPages
            for ((pi, page) in doc.pages.withIndex()) {
                page.resources?.let { changed += compressResources(doc, it, done, maxSide, quality, 0) }
                onProgress((pi + 1f) / n)
            }
            doc.save(out)
        }
        return changed
    }

    private fun compressResources(
        doc: PDDocument,
        res: PDResources,
        done: HashMap<COSBase, PDImageXObject>,
        maxSide: Int,
        quality: Float,
        depth: Int,
    ): Int {
        if (depth > 4) return 0
        var changed = 0
        for (name in res.xObjectNames.toList()) {
            val xo = runCatching { res.getXObject(name) }.getOrNull() ?: continue
            when (xo) {
                is PDImageXObject -> {
                    val key = xo.cosObject
                    done[key]?.let { res.put(name, it); return@let }
                    if (done.containsKey(key)) continue
                    val replacement = runCatching { recompress(doc, xo, maxSide, quality) }.getOrNull()
                    if (replacement != null) {
                        res.put(name, replacement)
                        done[key] = replacement
                        changed++
                    }
                }
                is PDFormXObject -> xo.resources?.let { changed += compressResources(doc, it, done, maxSide, quality, depth + 1) }
            }
        }
        return changed
    }

    private fun recompress(doc: PDDocument, img: PDImageXObject, maxSide: Int, quality: Float): PDImageXObject? {
        val dict = img.cosObject
        if (dict.containsKey(COSName.SMASK) || dict.containsKey(COSName.MASK) || img.isStencil) return null
        if (img.bitsPerComponent < 8) return null
        val originalBytes = (img.cosObject as? COSStream)?.length ?: return null
        if (originalBytes < 40_000) return null
        val bmp = img.image ?: return null
        try {
            if (bmp.hasAlpha()) return null
            val longSide = max(bmp.width, bmp.height)
            val scaled = if (longSide > maxSide) {
                val s = maxSide.toFloat() / longSide
                Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
            } else {
                bmp
            }
            val result = JPEGFactory.createFromImage(doc, scaled, quality)
            if (scaled != bmp) scaled.recycle()
            val newBytes = (result.cosObject as? COSStream)?.length ?: return null
            return if (newBytes < originalBytes * 0.9) result else null
        } finally {
            bmp.recycle()
        }
    }

    // ------------------------------------------------------------------ czcionki

    /** Czcionki do tekstu w adnotacjach: systemowe TTF z polskimi znakami, w ostateczności Helvetica. */
    class FontBox(private val doc: PDDocument, private val files: List<File>) {
        private var loaded: PDFont? = null
        private var tried = false

        fun font(): PDFont {
            if (!tried) {
                tried = true
                for (f in files) {
                    loaded = runCatching { PDType0Font.load(doc, f) }.getOrNull()
                    if (loaded != null) break
                }
            }
            return loaded ?: PDType1Font.HELVETICA
        }

        /** Tekst, który da się zapisać daną czcionką (brakujące znaki zastępowane ich wersją bez ogonków lub „?”). */
        fun safe(font: PDFont, text: String): String {
            val sb = StringBuilder()
            for (ch in text) {
                val s = ch.toString()
                if (canEncode(font, s)) {
                    sb.append(s)
                } else {
                    val base = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).firstOrNull()?.toString()
                    val alt = when (ch) { 'ł' -> "l"; 'Ł' -> "L"; else -> base }
                    sb.append(if (alt != null && canEncode(font, alt)) alt else "?")
                }
            }
            return sb.toString()
        }

        private fun canEncode(font: PDFont, s: String) = try {
            font.encode(s)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Domyślne czcionki systemowe Androida z polskimi znakami. */
    fun systemFonts(): List<File> = listOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
        "/system/fonts/NotoSerif-Regular.ttf",
    ).map(::File).filter { it.exists() }

    // ------------------------------------------------------------------ pomocnicze rysowanie

    internal fun drawNoteIcon(cs: PDPageContentStream, r: PDRectangle, color: Int) {
        val s = min(r.width, r.height).coerceAtLeast(14f).coerceAtMost(24f)
        val x = r.lowerLeftX
        val y = r.upperRightY - s
        cs.saveGraphicsState()
        cs.setNonStrokingColor(red(color), green(color), blue(color))
        cs.setStrokingColor(0.2f, 0.2f, 0.2f)
        cs.setLineWidth(0.8f)
        cs.addRect(x, y + s * 0.22f, s, s * 0.78f)
        cs.fillAndStroke()
        cs.moveTo(x + s * 0.2f, y + s * 0.22f)
        cs.lineTo(x + s * 0.2f, y)
        cs.lineTo(x + s * 0.45f, y + s * 0.22f)
        cs.closePath()
        cs.fillAndStroke()
        cs.setStrokingColor(0.25f, 0.25f, 0.25f)
        for (k in 1..3) {
            val ly = y + s * 0.22f + s * 0.78f * k / 4.2f
            cs.moveTo(x + s * 0.18f, ly)
            cs.lineTo(x + s * 0.82f, ly)
        }
        cs.stroke()
        cs.restoreGraphicsState()
    }

    internal fun red(c: Int) = ((c shr 16) and 0xFF) / 255f
    internal fun green(c: Int) = ((c shr 8) and 0xFF) / 255f
    internal fun blue(c: Int) = (c and 0xFF) / 255f
    internal fun alpha(c: Int) = ((c ushr 24) and 0xFF) / 255f

    internal fun floats(vararg v: Float) = COSArray().apply { v.forEach { add(COSFloat(it)) } }

    internal fun arrowHead(x1: Float, y1: Float, x2: Float, y2: Float, size: Float): FloatArray {
        val ang = atan2(y2 - y1, x2 - x1)
        val spread = (PI / 7).toFloat()
        return floatArrayOf(
            x2 - size * cos(ang - spread), y2 - size * sin(ang - spread),
            x2 - size * cos(ang + spread), y2 - size * sin(ang + spread),
        )
    }

    internal fun gsAlpha(alpha: Float, multiply: Boolean): PDExtendedGraphicsState {
        val gs = PDExtendedGraphicsState()
        gs.cosObject.setFloat(COSName.CA, alpha)
        gs.cosObject.setFloat(COSName.getPDFName("ca"), alpha)
        if (multiply) gs.cosObject.setName(COSName.getPDFName("BM"), "Multiply")
        return gs
    }

    internal fun newAppearance(doc: PDDocument, bbox: PDRectangle): PDAppearanceStream {
        val ap = PDAppearanceStream(doc)
        ap.bBox = bbox
        ap.resources = PDResources()
        return ap
    }

    internal fun appearanceDict(ap: PDAppearanceStream): PDAppearanceDictionary {
        val d = PDAppearanceDictionary()
        d.setNormalAppearance(ap)
        return d
    }
}
