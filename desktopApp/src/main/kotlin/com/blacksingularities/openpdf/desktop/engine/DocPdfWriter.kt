package com.blacksingularities.openpdf.desktop.engine

import com.blacksingularities.openpdf.doc.Align
import com.blacksingularities.openpdf.doc.PageBreak
import com.blacksingularities.openpdf.doc.Para
import com.blacksingularities.openpdf.doc.ParaKind
import com.blacksingularities.openpdf.doc.Picture
import com.blacksingularities.openpdf.doc.Rule
import com.blacksingularities.openpdf.doc.Span
import com.blacksingularities.openpdf.doc.Table
import com.blacksingularities.openpdf.doc.TextDocument
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Składanie dokumentu tekstowego w strony PDF (PDFBox, czcionki systemu Windows osadzane jako
 * podzbiory, więc tekst da się wyszukiwać i kopiować). Łamanie wierszy z justowaniem, wdowy
 * i sieroty, nagłówki trzymane z tekstem, tabele, obrazy, numery stron i spis treści.
 */
class DocPdfWriter(private val doc: TextDocument) {
    private val st = doc.style
    private val pageW = st.widthPt
    private val pageH = st.heightPt
    private val left = st.marginX
    private val contentW = pageW - 2 * st.marginX
    private val top = st.marginTop
    private val bottom = pageH - st.marginBottom
    private val body = st.fontSize

    private val pdf = PDDocument(DesktopPdfEngine.memory)
    private val fonts = Fonts(pdf)
    private var cs: PDPageContentStream? = null
    private var pageNo = 0
    private var y = 0f
    private var hasContent = false
    private var afterHeading = false
    private val headings = ArrayList<Triple<String, Int, Int>>()

    private val ink = Rgb(0x1D, 0x1D, 0x1F)
    private val dim = Rgb(0x55, 0x55, 0x5A)
    private val linkColor = Rgb(0x1A, 0x5F, 0xB4)

    fun write(out: File) {
        try {
            for (b in doc.blocks) {
                when (b) {
                    is Para -> para(b)
                    is Picture -> picture(b)
                    is Table -> table(b)
                    PageBreak -> if (hasContent) newPage()
                    Rule -> rule()
                }
            }
            if (cs == null) newPage()
            finishPage()
            addOutline()
            pdf.save(out)
        } finally {
            runCatching { cs?.close() }
            pdf.close()
        }
    }

    // ------------------------------------------------------------------ strony

    private fun canvas(): PDPageContentStream {
        if (cs == null) newPage()
        return cs!!
    }

    private fun newPage() {
        finishPage()
        pageNo++
        val page = PDPage(PDRectangle(pageW, pageH))
        pdf.addPage(page)
        cs = PDPageContentStream(pdf, page)
        y = top
        hasContent = false
    }

    private fun finishPage() {
        val c = cs ?: return
        if (st.pageNumbers && pageNo > 1) {
            val s = pageNo.toString()
            val f = fonts.get(FontKind.BODY, bold = false, italic = false)
            val size = body * 0.78f
            val w = f.width(s, size)
            drawText(c, f, s, pageW / 2f - w / 2f, pageH - st.marginBottom * 0.45f, size, dim)
        }
        c.close()
        cs = null
    }

    private fun space(amount: Float) {
        if (hasContent) y = min(bottom, y + amount)
    }

    /** Tekst od linii bazowej [baseline] (oś Y w dół, od góry strony). */
    private fun drawText(c: PDPageContentStream, f: Face, text: String, x: Float, baseline: Float, size: Float, color: Rgb) {
        var cx = x
        for ((font, part) in f.runs(text)) {
            c.beginText()
            c.setFont(font, size)
            c.setNonStrokingColor(color.r, color.g, color.b)
            c.newLineAtOffset(cx, pageH - baseline)
            c.showText(part)
            c.endText()
            cx += font.getStringWidth(part) / 1000f * size
        }
    }

    // ------------------------------------------------------------------ układ wierszy

    private class Piece(val text: String, val face: Face, val size: Float, val color: Rgb, val rise: Float, val underline: Boolean, val strike: Boolean, val space: Boolean) {
        val width = face.width(text, size)
    }

    private class Line(val pieces: List<Piece>, val ascent: Float, val height: Float, val last: Boolean, val indent: Float) {
        val width get() = pieces.sumOf { it.width.toDouble() }.toFloat()
    }

    private fun pieces(spans: List<Span>, size: Float, kind: FontKind, baseBold: Boolean, baseItalic: Boolean, baseColor: Rgb): List<Piece> {
        val out = ArrayList<Piece>()
        for (s in spans) {
            val k = if (s.mono) FontKind.MONO else kind
            val face = fonts.get(k, s.bold || baseBold, s.italic || baseItalic)
            val sz = size * (if (s.sup || s.sub) 0.72f else 1f) * (if (s.mono && kind != FontKind.MONO) 0.9f else 1f)
            val rise = if (s.sup) size * 0.33f else if (s.sub) -size * 0.15f else 0f
            val color = if (s.link) linkColor else baseColor
            // Słowa i odstępy jako osobne kawałki – miejsca łamania wierszy.
            val re = Regex("\\n|[ \\t]+|[^ \\t\\n]+")
            for (m in re.findAll(s.text)) {
                val t = m.value
                when {
                    t == "\n" -> out += Piece("\n", face, sz, color, rise, false, false, true)
                    t.isBlank() -> out += Piece(" ", face, sz, color, rise, s.underline || s.link, s.strike, true)
                    else -> out += Piece(t, face, sz, color, rise, s.underline || s.link, s.strike, false)
                }
            }
        }
        return out
    }

    /** Łamanie wierszy (zachłanne); zbyt długie słowa dzielone na znaki. */
    private fun breakLines(all: List<Piece>, width: Float, firstIndent: Float, spacing: Float, size: Float): List<Line> {
        val lines = ArrayList<Line>()
        var cur = ArrayList<Piece>()
        var curW = 0f
        var indent = firstIndent
        fun flush(last: Boolean) {
            while (cur.isNotEmpty() && cur.last().space) cur.removeAt(cur.size - 1)
            val maxSize = cur.maxOfOrNull { it.size } ?: size
            lines += Line(cur, maxSize * 0.93f, maxSize * spacing, last, indent)
            cur = ArrayList()
            curW = 0f
            indent = 0f
        }
        for (p in all) {
            if (p.text == "\n") {
                flush(true)
                continue
            }
            if (p.space && cur.isEmpty()) continue
            val avail = width - indent
            if (!p.space && curW + p.width > avail && cur.isNotEmpty()) flush(false)
            if (!p.space && p.width > width - indent) {
                // Słowo dłuższe niż wiersz.
                var rest = p.text
                while (rest.isNotEmpty()) {
                    var n = rest.length
                    while (n > 1 && p.face.width(rest.take(n), p.size) > width - indent - curW) n--
                    val part = Piece(rest.take(n), p.face, p.size, p.color, p.rise, p.underline, p.strike, false)
                    cur += part
                    curW += part.width
                    rest = rest.drop(n)
                    if (rest.isNotEmpty()) flush(false)
                }
                continue
            }
            cur += p
            curW += p.width
        }
        if (cur.isNotEmpty() || lines.isEmpty()) flush(true)
        return lines
    }

    private fun drawLine(c: PDPageContentStream, l: Line, x: Float, lineTop: Float, width: Float, align: Align) {
        val baseline = lineTop + l.ascent + (l.height - l.ascent / 0.93f) * 0.5f
        val natural = l.width
        val avail = width - l.indent
        val spaces = l.pieces.count { it.space }
        var extra = 0f
        var cx = x + l.indent
        when (align) {
            Align.CENTER -> cx += (avail - natural) / 2f
            Align.END -> cx += avail - natural
            Align.JUSTIFY -> if (!l.last && spaces > 0 && natural > avail * 0.6f) extra = (avail - natural) / spaces
            else -> Unit
        }
        for (p in l.pieces) {
            val w = p.width + if (p.space) extra else 0f
            if (!p.space) drawText(c, p.face, p.text, cx, baseline - p.rise, p.size, p.color)
            if (p.underline || p.strike) {
                c.setStrokingColor(p.color.r, p.color.g, p.color.b)
                c.setLineWidth(max(0.4f, p.size * 0.05f))
                if (p.underline) {
                    val uy = pageH - (baseline - p.rise + p.size * 0.12f)
                    c.moveTo(cx, uy); c.lineTo(cx + w, uy); c.stroke()
                }
                if (p.strike) {
                    val sy = pageH - (baseline - p.rise - p.size * 0.3f)
                    c.moveTo(cx, sy); c.lineTo(cx + w, sy); c.stroke()
                }
            }
            cx += w
        }
    }

    // ------------------------------------------------------------------ akapity

    private fun para(p: Para) {
        if (p.text.isBlank()) {
            space(body * st.lineHeight)
            return
        }
        val heading = p.kind == ParaKind.HEADING
        val level = p.level.coerceIn(1, 6)
        val size = when {
            heading -> body * when (level) {
                1 -> 1.75f
                2 -> 1.45f
                3 -> 1.25f
                4 -> 1.1f
                else -> 1f
            }
            p.kind == ParaKind.CODE -> body * 0.86f
            p.kind == ParaKind.CAPTION -> body * 0.88f
            p.kind == ParaKind.QUOTE -> body * 0.96f
            else -> body
        }
        val kind = if (p.kind == ParaKind.CODE) FontKind.MONO else FontKind.BODY
        val color = if (p.kind == ParaKind.QUOTE || p.kind == ParaKind.CAPTION) dim else ink
        val indentStep = body * 1.6f
        var x = left
        var width = contentW
        when (p.kind) {
            ParaKind.QUOTE -> {
                x += body * 1.6f
                width -= body * 3.2f
            }
            ParaKind.VERSE -> {
                x += body * 2f
                width -= body * 2f
            }
            else -> Unit
        }
        var markerW = 0f
        val marker = p.marker
        val markerFace = fonts.get(kind, false, false)
        if (p.listLevel > 0) {
            x += indentStep * (p.listLevel - 1)
            markerW = max(body * 1.5f, markerFace.width(marker ?: "", size) + body * 0.5f)
            width -= indentStep * (p.listLevel - 1) + markerW
        }
        val alignment = p.align ?: when {
            heading && st.centerHeadings && level <= 2 -> Align.CENTER
            p.kind == ParaKind.CAPTION -> Align.CENTER
            p.kind == ParaKind.BODY && st.justify -> Align.JUSTIFY
            else -> Align.START
        }
        val firstIndent = if (p.kind == ParaKind.BODY && st.firstLineIndent > 0f && !afterHeading && p.listLevel == 0 && alignment != Align.CENTER && alignment != Align.END) {
            body * st.firstLineIndent
        } else 0f
        val lineSpacing = if (heading) 1.18f else if (p.kind == ParaKind.CODE) 1.25f else st.lineHeight
        val src = if (p.kind == ParaKind.CODE || p.kind == ParaKind.VERSE) p.spans else p.spans
        val lines = breakLines(pieces(src, size, kind, heading, p.kind == ParaKind.CAPTION, color), width, firstIndent, lineSpacing, size)
        val before = when {
            heading -> size * (if (level <= 2) 1.3f else 1f)
            p.kind == ParaKind.CODE -> body * 0.3f
            else -> 0f
        }
        val after = when {
            heading -> size * 0.45f
            p.kind == ParaKind.CODE -> body * 0.1f
            else -> body * st.paragraphSpacing
        }
        if (cs == null) newPage()
        if (heading && level <= 1 && !hasContent && st.centerHeadings) y = top + (bottom - top) * 0.12f
        space(before)
        drawLines(
            lines, x + markerW, width, alignment,
            keepWithNext = if (heading) body * st.lineHeight * 3f else 0f,
            background = if (p.kind == ParaKind.CODE) Rgb(0xF2, 0xF2, 0xF4) else null,
        ) { c, firstTop ->
            if (marker != null) {
                val l = lines.first()
                drawText(c, markerFace, marker, x, firstTop + l.ascent + (l.height - l.ascent / 0.93f) * 0.5f, size, ink)
            }
        }
        y = min(bottom, y + after)
        afterHeading = heading
        if (heading) headings += Triple(p.text.replace('\n', ' ').trim(), level, pageNo - 1)
    }

    /** Wiersze od bieżącej pozycji, dzielone między strony, z ochroną przed wdowami i sierotami. */
    private fun drawLines(lines: List<Line>, x: Float, width: Float, align: Align, keepWithNext: Float = 0f, background: Rgb? = null, onFirst: ((PDPageContentStream, Float) -> Unit)? = null) {
        val n = lines.size
        if (n == 0) return
        val tops = FloatArray(n + 1)
        for (i in 0 until n) tops[i + 1] = tops[i] + lines[i].height
        var line = 0
        var firstDone = false
        while (line < n) {
            if (cs == null) newPage()
            val avail = bottom - y
            var end = line
            while (end < n && tops[end + 1] - tops[line] <= avail) end++
            val rest = tops[n] - tops[line]
            if (hasContent) {
                if (line == 0 && keepWithNext > 0f && rest + keepWithNext > avail) {
                    newPage()
                    continue
                }
                if (end - line < min(2, n - line)) {
                    newPage()
                    continue
                }
            }
            if (end < n && n - end == 1 && end - line >= 3) end--
            if (end == line) end = line + 1
            val h = tops[end] - tops[line]
            val c = canvas()
            if (background != null) {
                val pad = body * 0.35f
                c.setNonStrokingColor(background.r, background.g, background.b)
                c.addRect(x - pad, pageH - (y + h + pad * 0.6f), width + 2 * pad, h + pad * 1.2f)
                c.fill()
            }
            if (!firstDone) {
                onFirst?.invoke(c, y)
                firstDone = true
            }
            for (i in line until end) drawLine(c, lines[i], x, y + tops[i] - tops[line], width, align)
            y += h
            hasContent = true
            line = end
            if (line < n) newPage()
        }
    }

    // ------------------------------------------------------------------ obrazy, tabele, linie

    private fun picture(p: Picture) {
        val img = Images.decodeBytes(p.bytes) ?: return
        var w = img.width * 0.75f
        var h = img.height * 0.75f
        val maxH = (bottom - top) * 0.92f
        if (w > contentW) {
            h *= contentW / w
            w = contentW
        }
        if (h > maxH) {
            w *= maxH / h
            h = maxH
        }
        if (img.width >= 600 && w < contentW * 0.85f && h < maxH * 0.9f) {
            val k = min(contentW / w, maxH / h)
            w *= k
            h *= k
        }
        space(body * 0.5f)
        if (h > bottom - y && hasContent) newPage()
        val c = canvas()
        val scaled = if (max(img.width, img.height) > 2200) Images.scale(img, 2200) else img
        val x = left + (contentW - w) / 2f
        val xo = if (scaled.colorModel.hasAlpha()) LosslessFactory.createFromImage(pdf, scaled) else JPEGFactory.createFromImage(pdf, scaled, 0.88f)
        c.drawImage(xo, x, pageH - (y + h), w, h)
        y += h
        hasContent = true
        space(body * 0.6f)
        afterHeading = false
    }

    private fun table(t: Table) {
        val cols = t.rows.maxOfOrNull { it.size } ?: return
        if (cols == 0) return
        val size = body * if (cols > 4) 0.82f else 0.9f
        val pad = size * 0.4f
        val weights = FloatArray(cols) { c ->
            val longest = t.rows.maxOfOrNull { r -> r.getOrNull(c)?.sumOf { it.text.length } ?: 0 } ?: 0
            min(40f, max(6f, longest.toFloat()))
        }
        val total = weights.sum()
        val colW = FloatArray(cols) { contentW * weights[it] / total }
        space(body * 0.4f)
        for ((ri, row) in t.rows.withIndex()) {
            val header = ri == 0 && t.header
            val cells = (0 until cols).map { c ->
                val spans = row.getOrNull(c) ?: emptyList()
                breakLines(pieces(spans, size, FontKind.BODY, header, false, ink), colW[c] - 2 * pad, 0f, 1.2f, size)
            }
            val rowH = (cells.maxOf { ls -> ls.sumOf { it.height.toDouble() }.toFloat() } + 2 * pad).coerceAtMost(bottom - top)
            if (rowH > bottom - y && hasContent) newPage()
            val c = canvas()
            var x = left
            for (ci in 0 until cols) {
                if (header) {
                    c.setNonStrokingColor(0xF2 / 255f, 0xF2 / 255f, 0xF4 / 255f)
                    c.addRect(x, pageH - (y + rowH), colW[ci], rowH)
                    c.fill()
                }
                c.setStrokingColor(0xC7 / 255f, 0xC7 / 255f, 0xCC / 255f)
                c.setLineWidth(0.6f)
                c.addRect(x, pageH - (y + rowH), colW[ci], rowH)
                c.stroke()
                var ly = y + pad
                for (l in cells[ci]) {
                    if (ly + l.height > y + rowH) break
                    drawLine(c, l, x + pad, ly, colW[ci] - 2 * pad, Align.START)
                    ly += l.height
                }
                x += colW[ci]
            }
            y += rowH
            hasContent = true
        }
        space(body * 0.6f)
        afterHeading = false
    }

    private fun rule() {
        space(body * 0.8f)
        if (bottom - y < body * 2f) {
            newPage()
            return
        }
        val c = canvas()
        c.setStrokingColor(0xB0 / 255f, 0xB0 / 255f, 0xB5 / 255f)
        c.setLineWidth(0.7f)
        val w = contentW * 0.3f
        c.moveTo(left + (contentW - w) / 2f, pageH - y)
        c.lineTo(left + (contentW + w) / 2f, pageH - y)
        c.stroke()
        hasContent = true
        space(body * 0.8f)
        afterHeading = true
    }

    // ------------------------------------------------------------------ spis treści

    private fun addOutline() {
        doc.title?.let { pdf.documentInformation.title = it }
        doc.author?.let { pdf.documentInformation.author = it }
        pdf.documentInformation.producer = "OpenDocument"
        val entries = headings.filter { it.second <= 3 && it.first.isNotEmpty() }
        if (entries.isEmpty()) return
        val outline = PDDocumentOutline()
        pdf.documentCatalog.documentOutline = outline
        val stack = ArrayList<Pair<Int, PDOutlineItem>>()
        for ((title, level, pageIndex) in entries.take(2000)) {
            val item = PDOutlineItem()
            item.title = title.take(200)
            val pg = pdf.getPage(pageIndex.coerceIn(0, pdf.numberOfPages - 1))
            item.destination = PDPageXYZDestination().apply {
                page = pg
                top = pg.mediaBox.height.toInt()
            }
            while (stack.isNotEmpty() && stack.last().first >= level) stack.removeAt(stack.size - 1)
            if (stack.isEmpty()) outline.addLast(item) else stack.last().second.addLast(item)
            stack += level to item
        }
        outline.openNode()
    }

    // ------------------------------------------------------------------ czcionki

    private class Rgb(r: Int, g: Int, b: Int) {
        val r = r / 255f
        val g = g / 255f
        val b = b / 255f
    }

    private enum class FontKind { BODY, MONO }

    /** Czcionka z zapasowymi: znaki, których nie ma w podstawowej, idą kolejną z listy. */
    private class Face(val fonts: List<PDFont>) {
        private val support = HashMap<Pair<Int, Int>, Boolean>()

        private fun has(fi: Int, cp: Int): Boolean = support.getOrPut(fi to cp) {
            runCatching { fonts[fi].encode(String(Character.toChars(cp))); true }.getOrDefault(false)
        }

        /** Tekst podzielony na odcinki z czcionką, która ma ich znaki (brakujące → „?”). */
        fun runs(text: String): List<Pair<PDFont, String>> {
            val out = ArrayList<Pair<PDFont, String>>()
            var curFont = -1
            val sb = StringBuilder()
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                i += Character.charCount(cp)
                if (cp < 32 || cp == 0xAD) continue
                var fi = fonts.indices.firstOrNull { has(it, cp) }
                var s = String(Character.toChars(cp))
                if (fi == null) {
                    fi = 0
                    s = "?"
                }
                if (fi != curFont && sb.isNotEmpty()) {
                    out += fonts[curFont] to sb.toString()
                    sb.clear()
                }
                curFont = fi
                sb.append(s)
            }
            if (sb.isNotEmpty()) out += fonts[curFont] to sb.toString()
            return out
        }

        fun width(text: String, size: Float): Float = runs(text).sumOf { (f, s) -> (f.getStringWidth(s) / 1000f * size).toDouble() }.toFloat()
    }

    private inner class Fonts(private val pdf: PDDocument) {
        private val dir = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        private val loaded = HashMap<String, PDFont?>()
        private val faces = HashMap<Triple<FontKind, Boolean, Boolean>, Face>()

        private fun file(name: String): PDFont? = loaded.getOrPut(name) {
            val f = File(dir, name)
            if (f.isFile) runCatching { PDType0Font.load(pdf, f) }.getOrNull() else null
        }

        private fun pick(vararg names: String): PDFont? = names.firstNotNullOfOrNull { file(it) }

        fun get(kind: FontKind, bold: Boolean, italic: Boolean): Face = faces.getOrPut(Triple(kind, bold, italic)) {
            val style = (if (bold) 1 else 0) + (if (italic) 2 else 0)
            val main = when (kind) {
                FontKind.MONO -> when (style) {
                    1 -> pick("consolab.ttf", "courbd.ttf")
                    2 -> pick("consolai.ttf", "couri.ttf")
                    3 -> pick("consolaz.ttf", "courbi.ttf")
                    else -> pick("consola.ttf", "cour.ttf")
                }
                FontKind.BODY -> if (st.serif) when (style) {
                    1 -> pick("georgiab.ttf", "timesbd.ttf")
                    2 -> pick("georgiai.ttf", "timesi.ttf")
                    3 -> pick("georgiaz.ttf", "timesbi.ttf")
                    else -> pick("georgia.ttf", "times.ttf")
                } else when (style) {
                    1 -> pick("segoeuib.ttf", "arialbd.ttf")
                    2 -> pick("segoeuii.ttf", "ariali.ttf")
                    3 -> pick("segoeuiz.ttf", "arialbi.ttf")
                    else -> pick("segoeui.ttf", "arial.ttf")
                }
            }
            val list = listOfNotNull(main, pick("arial.ttf"), pick("seguisym.ttf"), pick("seguiemj.ttf"))
            Face(list.ifEmpty { listOf(PDType1Font(Standard14Fonts.FontName.HELVETICA)) })
        }
    }
}
