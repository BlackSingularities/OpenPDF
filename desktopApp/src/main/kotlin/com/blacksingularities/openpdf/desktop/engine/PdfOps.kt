package com.blacksingularities.openpdf.desktop.engine

import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.pdf.AnnotInfo
import com.blacksingularities.openpdf.pdf.FieldEdit
import com.blacksingularities.openpdf.pdf.FieldInfo
import com.blacksingularities.openpdf.pdf.FieldKind
import com.blacksingularities.openpdf.pdf.FieldWidget
import com.blacksingularities.openpdf.pdf.InfoEdit
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.PageGeo
import com.blacksingularities.openpdf.pdf.normalizeRotation
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSFloat
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice
import org.apache.pdfbox.pdmodel.interactive.form.PDField
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Operacje zmieniające pliki PDF w wersji Windows (PDFBox 3) – odpowiednik operacji z Androida.
 * Każda funkcja otwiera plik, zmienia go i zapisuje (przez plik tymczasowy).
 */
object PdfOps {
    fun load(file: File, password: String? = null): PDDocument = DesktopPdfEngine.load(file, password)

    /** Otwiera plik, wykonuje [block] i zapisuje wynik do [out] (domyślnie w miejsce pliku). */
    inline fun <T> edit(file: File, out: File = file, block: (PDDocument) -> T): T {
        val tmp = File(out.parentFile, out.name + ".tmp")
        val result = load(file).use { doc ->
            val r = block(doc)
            doc.save(tmp)
            r
        }
        replace(tmp, out)
        return result
    }

    /** Zastępuje [out] plikiem [tmp]; czeka chwilę, gdy Windows jeszcze trzyma plik (zamykany renderer). */
    fun replace(tmp: File, out: File) {
        var tries = 0
        while (out.exists() && !out.delete()) {
            if (++tries > 25) {
                tmp.delete()
                error(tr("Nie można zastąpić pliku {0}", out.name))
            }
            Thread.sleep(80)
        }
        if (!tmp.renameTo(out)) error(tr("Nie można zapisać pliku {0}", out.name))
    }

    // ------------------------------------------------------------------ odczyt

    /** Adnotacje stron (bez odnośników, wyskakujących okienek i pól formularza). */
    fun readAnnots(doc: PDDocument, pi: Int, page: PDPage, geo: PageGeo): List<AnnotInfo> {
        val out = ArrayList<AnnotInfo>()
        runCatching { page.annotations }.getOrNull().orEmpty().forEachIndexed { ai, a ->
            val r = a.rectangle ?: return@forEachIndexed
            val subtype = a.subtype ?: ""
            if (a is PDAnnotationLink || subtype == "Popup" || subtype == "Widget" || subtype == "Link") return@forEachIndexed
            out += AnnotInfo(
                page = pi,
                index = ai,
                subtype = subtype,
                rect = geo.rectFromPdf(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY),
                contents = a.contents ?: "",
                author = a.cosObject.getString(COSName.T) ?: "",
                color = colorOf(a.cosObject),
            )
        }
        return out
    }

    fun readFields(doc: PDDocument): List<FieldInfo> {
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
                    pi = doc.pages.indexOfFirst { p ->
                        runCatching { p.annotations.any { it.cosObject == w.cosObject } }.getOrDefault(false)
                    }.takeIf { it >= 0 }
                }
                pi ?: return@mapNotNull null
                val p = doc.getPage(pi)
                val geo = PageGeo(p.cropBox.lowerLeftX, p.cropBox.lowerLeftY, p.cropBox.width, p.cropBox.height, normalizeRotation(p.rotation))
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

    /** Cały tekst dokumentu (strony oddzielone pustą linią). */
    fun extractText(file: File): String = load(file).use { doc ->
        val sb = StringBuilder()
        val stripper = PDFTextStripper()
        stripper.sortByPosition = true
        for (i in 1..doc.numberOfPages) {
            stripper.startPage = i
            stripper.endPage = i
            val t = runCatching { stripper.getText(doc) }.getOrDefault("").trim()
            if (t.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(t)
            }
        }
        sb.toString()
    }

    // ------------------------------------------------------------------ adnotacje

    /** Zapisuje oczekujące elementy jako adnotacje PDF z wyglądem (widoczne w każdym czytniku). */
    fun commitMarks(file: File, marks: List<Mark>, author: String) {
        if (marks.isEmpty()) return
        edit(file) { doc ->
            val fonts = FontBox(doc, systemFonts())
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

    fun deleteAllAnnotations(file: File) = edit(file) { doc ->
        for (page in doc.pages) {
            page.annotations = page.annotations.filter { it.subtype == "Link" || it.subtype == "Widget" }
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

    /** Przenosi strony [pages] tak, by zaczynały się na pozycji [target] (indeks po ich usunięciu). */
    fun movePages(file: File, pages: List<Int>, target: Int) = edit(file) { doc ->
        val moving = pages.sorted().map { doc.getPage(it) }
        for (p in moving) doc.pages.remove(p)
        insertAt(doc, moving, target.coerceIn(0, doc.numberOfPages))
    }

    fun insertAt(doc: PDDocument, pages: List<PDPage>, at: Int) {
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
            val dict = COSDictionary()
            dict.addAll(src.cosObject)
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
    fun insertBlank(file: File, at: Int) = edit(file) { doc ->
        val ref = if (doc.numberOfPages > 0) doc.getPage(at.coerceIn(0, doc.numberOfPages - 1)) else null
        var w = ref?.mediaBox?.width ?: PDRectangle.A4.width
        var h = ref?.mediaBox?.height ?: PDRectangle.A4.height
        if (ref != null && normalizeRotation(ref.rotation) % 180 != 0) w = h.also { h = w }
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
        replace(tmp, file)
    }

    fun extractPages(file: File, pages: Collection<Int>, out: File) {
        val keep = pages.toSet()
        load(file).use { doc ->
            for (i in doc.numberOfPages - 1 downTo 0) if (i !in keep) doc.removePage(i)
            doc.save(out)
        }
    }

    fun insertImages(file: File, images: List<File>, at: Int) = edit(file) { doc ->
        insertAt(doc, images.mapNotNull { imagePage(doc, it) }, at)
    }

    fun createBlank(out: File, pages: Int, size: PDRectangle) {
        PDDocument(DesktopPdfEngine.memory).use { doc ->
            repeat(pages.coerceAtLeast(1)) { doc.addPage(PDPage(PDRectangle(size.width, size.height))) }
            doc.save(out)
        }
    }

    fun imagePage(doc: PDDocument, f: File): PDPage? {
        val img = Images.decode(f, 3000) ?: return null
        val x = if (img.colorModel.hasAlpha()) org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, img)
        else JPEGFactory.createFromImage(doc, img, 0.9f)
        // Dłuższy bok jak w A4 (842 pt), proporcje obrazu zachowane.
        val long = 842f
        val (w, h) = if (img.width >= img.height) long to long * img.height / img.width else long * img.width / img.height to long
        val page = PDPage(PDRectangle(w, h))
        PDPageContentStream(doc, page).use { cs -> cs.drawImage(x, 0f, 0f, w, h) }
        return page
    }

    // ------------------------------------------------------------------ formularze, metadane, ochrona

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
        val tmp = File(out.parentFile, out.name + ".tmp")
        load(file).use { doc ->
            if (!userPassword.isNullOrEmpty() || !ownerPassword.isNullOrEmpty()) {
                val owner = ownerPassword?.takeIf { it.isNotEmpty() } ?: userPassword!!
                val policy = StandardProtectionPolicy(owner, userPassword ?: "", AccessPermission())
                policy.encryptionKeyLength = 256
                doc.protect(policy)
            }
            doc.save(tmp)
        }
        replace(tmp, out)
    }

    /**
     * Zmniejsza plik: obrazy większe niż [maxSide] px są skalowane i zapisywane jako JPEG o jakości
     * [quality]. Obrazy z przezroczystością i maskami są pomijane. Zwraca liczbę zmienionych obrazów.
     */
    fun compress(file: File, out: File, maxSide: Int, quality: Float, onProgress: (Float) -> Unit): Int {
        var changed = 0
        load(file).use { doc ->
            val done = HashMap<COSBase, PDImageXObject>()
            val n = doc.numberOfPages.coerceAtLeast(1)
            for ((pi, page) in doc.pages.withIndex()) {
                page.resources?.let { changed += compressResources(doc, it, done, maxSide, quality, 0) }
                onProgress((pi + 1f) / n)
            }
            doc.save(out)
        }
        return changed
    }

    private fun compressResources(doc: PDDocument, res: PDResources, done: HashMap<COSBase, PDImageXObject>, maxSide: Int, quality: Float, depth: Int): Int {
        if (depth > 4) return 0
        var changed = 0
        for (name in res.xObjectNames.toList()) {
            val xo = runCatching { res.getXObject(name) }.getOrNull() ?: continue
            when (xo) {
                is PDImageXObject -> {
                    val key: COSBase = xo.cosObject
                    val prev = done[key]
                    if (prev != null) {
                        res.put(name, prev)
                        continue
                    }
                    val replacement = runCatching { recompress(doc, xo, maxSide, quality) }.getOrNull() ?: continue
                    res.put(name, replacement)
                    done[key] = replacement
                    changed++
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
        val src = img.image ?: return null
        if (src.colorModel.hasAlpha()) return null
        val longSide = max(src.width, src.height)
        val rgb = if (longSide > maxSide) Images.scale(src, maxSide) else src
        val flat = if (rgb.type == BufferedImage.TYPE_INT_RGB || rgb.type == BufferedImage.TYPE_3BYTE_BGR || rgb.type == BufferedImage.TYPE_BYTE_GRAY) rgb else toRgb(rgb)
        val result = JPEGFactory.createFromImage(doc, flat, quality)
        val newBytes = (result.cosObject as? COSStream)?.length ?: return null
        return if (newBytes < originalBytes * 0.9) result else null
    }

    private fun toRgb(src: BufferedImage): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return out
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
            return loaded ?: PDType1Font(Standard14Fonts.FontName.HELVETICA)
        }

        /** Tekst, który da się zapisać daną czcionką (brakujące znaki zastępowane wersją bez ogonków lub „?”). */
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

    /** Czcionki Windows z polskimi znakami. */
    fun systemFonts(): List<File> {
        val dir = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf("segoeui.ttf", "arial.ttf", "calibri.ttf", "tahoma.ttf", "verdana.ttf").map { File(dir, it) }.filter { it.isFile }
    }

    // ------------------------------------------------------------------ pomocnicze rysowanie

    fun drawNoteIcon(cs: PDPageContentStream, r: PDRectangle, color: Int) {
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

    fun red(c: Int) = ((c shr 16) and 0xFF) / 255f
    fun green(c: Int) = ((c shr 8) and 0xFF) / 255f
    fun blue(c: Int) = (c and 0xFF) / 255f
    fun alpha(c: Int) = ((c ushr 24) and 0xFF) / 255f

    fun floats(vararg v: Float) = COSArray().apply { v.forEach { add(COSFloat(it)) } }

    fun arrowHead(x1: Float, y1: Float, x2: Float, y2: Float, size: Float): FloatArray {
        val ang = atan2(y2 - y1, x2 - x1)
        val spread = (PI / 7).toFloat()
        return floatArrayOf(
            x2 - size * cos(ang - spread), y2 - size * sin(ang - spread),
            x2 - size * cos(ang + spread), y2 - size * sin(ang + spread),
        )
    }

    fun gsAlpha(alpha: Float, multiply: Boolean): PDExtendedGraphicsState {
        val gs = PDExtendedGraphicsState()
        gs.cosObject.setFloat(COSName.CA, alpha)
        gs.cosObject.setFloat(COSName.getPDFName("ca"), alpha)
        if (multiply) gs.cosObject.setName(COSName.getPDFName("BM"), "Multiply")
        return gs
    }

    fun newAppearance(doc: PDDocument, bbox: PDRectangle): PDAppearanceStream {
        val ap = PDAppearanceStream(doc)
        ap.bBox = bbox
        ap.resources = PDResources()
        return ap
    }

    fun appearanceDict(ap: PDAppearanceStream): PDAppearanceDictionary {
        val d = PDAppearanceDictionary()
        d.setNormalAppearance(ap)
        return d
    }
}
