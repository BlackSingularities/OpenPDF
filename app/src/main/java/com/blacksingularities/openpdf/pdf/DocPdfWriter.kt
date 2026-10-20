package com.blacksingularities.openpdf.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.blacksingularities.openpdf.doc.Align
import com.blacksingularities.openpdf.doc.Para
import com.blacksingularities.openpdf.doc.PageBreak
import com.blacksingularities.openpdf.doc.ParaKind
import com.blacksingularities.openpdf.doc.Picture
import com.blacksingularities.openpdf.doc.Rule
import com.blacksingularities.openpdf.doc.Span
import com.blacksingularities.openpdf.doc.Table
import com.blacksingularities.openpdf.doc.TextDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Składanie dokumentu tekstowego w strony PDF: StaticLayout (justowanie, dzielenie wyrazów,
 * wysokiej jakości łamanie wierszy) rysowany na stronach PdfDocument. Pilnuje wdów i sierot,
 * nagłówków na dole strony, dzieli długie akapity i tabele między strony, dodaje numery stron
 * i spis treści z nagłówków.
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
    private val locale: Locale = doc.language?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()

    private val pdf = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    private var y = 0f
    /** Czy na bieżącej stronie coś już jest (odstępy przed blokiem pomijamy u góry strony). */
    private var hasContent = false
    private var afterHeading = false
    private val headings = ArrayList<Triple<String, Int, Int>>()

    private val serif: Typeface = Typeface.create(if (st.serif) Typeface.SERIF else Typeface.SANS_SERIF, Typeface.NORMAL)
    private val ink = Color.rgb(0x1D, 0x1D, 0x1F)
    private val dim = Color.rgb(0x55, 0x55, 0x5A)
    private val linkColor = Color.rgb(0x1A, 0x5F, 0xB4)

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
            if (page == null) newPage()
            finishPage()
            out.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        addOutline(out)
    }

    // ------------------------------------------------------------------ strony

    private fun canvas(): Canvas {
        if (page == null) newPage()
        return page!!.canvas
    }

    private fun newPage() {
        finishPage()
        pageNo++
        val info = PdfDocument.PageInfo.Builder(ceil(pageW).toInt(), ceil(pageH).toInt(), pageNo).create()
        page = pdf.startPage(info)
        y = top
        hasContent = false
    }

    private fun finishPage() {
        val p = page ?: return
        if (st.pageNumbers && pageNo > 1) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = body * 0.78f
                typeface = serif
                color = dim
                textAlign = Paint.Align.CENTER
            }
            p.canvas.drawText(pageNo.toString(), pageW / 2f, pageH - st.marginBottom * 0.45f, paint)
        }
        pdf.finishPage(p)
        page = null
    }

    private fun space(amount: Float) {
        if (hasContent) y = min(bottom, y + amount)
    }

    // ------------------------------------------------------------------ akapity

    private fun paint(size: Float, color: Int = ink, face: Typeface = serif) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = size
        typeface = face
        this.color = color
        textLocale = locale
        isLinearText = true
    }

    private fun spannable(spans: List<Span>, size: Float): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (s in spans) {
            val start = sb.length
            sb.append(s.text)
            val end = sb.length
            if (end == start) continue
            fun set(what: Any) = sb.setSpan(what, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            when {
                s.bold && s.italic -> set(StyleSpan(Typeface.BOLD_ITALIC))
                s.bold -> set(StyleSpan(Typeface.BOLD))
                s.italic -> set(StyleSpan(Typeface.ITALIC))
            }
            if (s.underline || s.link) set(UnderlineSpan())
            if (s.strike) set(StrikethroughSpan())
            if (s.mono) {
                set(TypefaceSpan("monospace"))
                set(RelativeSizeSpan(0.9f))
            }
            if (s.sup) {
                set(SuperscriptSpan())
                set(RelativeSizeSpan(0.72f))
            }
            if (s.sub) {
                set(SubscriptSpan())
                set(RelativeSizeSpan(0.72f))
            }
            if (s.link) set(ForegroundColorSpan(linkColor))
        }
        return sb
    }

    private fun layout(text: CharSequence, p: TextPaint, width: Int, align: Layout.Alignment, justify: Boolean, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, width))
            .setAlignment(align)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setJustificationMode(if (justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
            .build()

    private fun para(p: Para) {
        if (p.text.isBlank()) {
            // Pusty akapit – odstęp jednego wiersza.
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
        val face = when (p.kind) {
            ParaKind.CODE -> Typeface.MONOSPACE
            ParaKind.HEADING -> Typeface.create(serif, Typeface.BOLD)
            ParaKind.CAPTION -> Typeface.create(serif, Typeface.ITALIC)
            else -> serif
        }
        val tp = paint(size, if (p.kind == ParaKind.QUOTE || p.kind == ParaKind.CAPTION) dim else ink, face)
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
        if (p.listLevel > 0) {
            x += indentStep * (p.listLevel - 1)
            markerW = max(body * 1.5f, tp.measureText(p.marker ?: "") + body * 0.5f)
            width -= indentStep * (p.listLevel - 1) + markerW
        }
        val text = spannable(p.spans, size)
        val alignment = p.align ?: when {
            heading && st.centerHeadings && level <= 2 -> Align.CENTER
            p.kind == ParaKind.CAPTION -> Align.CENTER
            p.kind == ParaKind.BODY && st.justify -> Align.JUSTIFY
            else -> Align.START
        }
        // Wcięcie pierwszego wiersza – nie po nagłówku, nie w listach i nie w wyśrodkowanych akapitach.
        if (p.kind == ParaKind.BODY && st.firstLineIndent > 0f && !afterHeading && p.listLevel == 0 && alignment != Align.CENTER && alignment != Align.END) {
            text.setSpan(LeadingMarginSpan.Standard((body * st.firstLineIndent).toInt(), 0), 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        val lineSpacing = if (heading) 1.18f else if (p.kind == ParaKind.CODE) 1.25f else st.lineHeight
        val l = layout(
            text, tp, width.toInt(),
            when (alignment) {
                Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                Align.END -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            },
            justify = alignment == Align.JUSTIFY,
            spacing = lineSpacing,
        )
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
        // Rozdział od nowej strony zaczyna się niżej, jak w książce.
        if (page == null) newPage()
        if (heading && level <= 1 && !hasContent && st.centerHeadings) y = top + (bottom - top) * 0.12f
        space(before)
        val marker = p.marker
        drawLayout(
            l, x + markerW,
            keepWithNext = if (heading) body * st.lineHeight * 3f else 0f,
            background = if (p.kind == ParaKind.CODE) Color.rgb(0xF2, 0xF2, 0xF4) else null,
        ) { c, firstTop ->
            if (marker != null) {
                val mp = paint(size, ink, if (marker.firstOrNull()?.isDigit() == true || marker.firstOrNull()?.isLetter() == true) serif else Typeface.DEFAULT)
                c.drawText(marker, x, firstTop + l.getLineBaseline(0).toFloat(), mp)
            }
        }
        y = min(bottom, y + after)
        afterHeading = heading
        if (heading) headings += Triple(p.text.replace('\n', ' ').trim(), level, pageNo - 1)
    }

    /**
     * Rysuje układ tekstu od bieżącej pozycji, dzieląc go między strony całymi wierszami.
     * Nie zostawia samotnego pierwszego ani ostatniego wiersza akapitu na innej stronie,
     * a nagłówek ([keepWithNext] > 0) przenosi na następną stronę razem z początkiem tekstu.
     */
    private fun drawLayout(l: StaticLayout, x: Float, keepWithNext: Float = 0f, background: Int? = null, onFirst: ((Canvas, Float) -> Unit)? = null) {
        val n = l.lineCount
        if (n == 0) return
        var line = 0
        var firstDone = false
        while (line < n) {
            if (page == null) newPage()
            val avail = bottom - y
            val startTop = l.getLineTop(line).toFloat()
            var end = line
            while (end < n && l.getLineBottom(end) - startTop <= avail) end++
            val rest = l.getLineBottom(n - 1) - startTop
            if (hasContent) {
                // Nagłówek musi zmieścić się z co najmniej kilkoma wierszami tekstu.
                if (line == 0 && keepWithNext > 0f && rest + keepWithNext > avail) {
                    newPage()
                    continue
                }
                // Sierota: pierwszy wiersz akapitu sam na dole strony.
                if (end - line < min(2, n - line)) {
                    newPage()
                    continue
                }
            }
            // Wdowa: ostatni wiersz akapitu sam na następnej stronie.
            if (end < n && n - end == 1 && end - line >= 3) end--
            if (end == line) end = line + 1
            val endBottom = l.getLineBottom(end - 1).toFloat()
            val c = canvas()
            if (background != null) {
                val bp = Paint().apply { color = background }
                val pad = body * 0.35f
                c.drawRoundRect(RectF(x - pad, y - pad * 0.6f, x + l.width + pad, y + (endBottom - startTop) + pad * 0.6f), 3f, 3f, bp)
            }
            if (!firstDone) {
                onFirst?.invoke(c, y)
                firstDone = true
            }
            c.save()
            c.translate(x, y - startTop)
            c.clipRect(-body * 2f, startTop, l.width + body * 2f, endBottom)
            l.draw(c)
            c.restore()
            y += endBottom - startTop
            hasContent = true
            line = end
            if (line < n) newPage()
        }
    }

    // ------------------------------------------------------------------ obrazy, tabele, linie

    private fun picture(p: Picture) {
        val bmp = decode(p.bytes) ?: return
        try {
            // Obraz w punktach: 96 dpi ekranu → 72 pt na cal, nie szerszy niż tekst ani wyższy niż strona.
            var w = bmp.width * 0.75f
            var h = bmp.height * 0.75f
            val maxH = (bottom - top) * 0.92f
            if (w > contentW) {
                h *= contentW / w
                w = contentW
            }
            if (h > maxH) {
                w *= maxH / h
                h = maxH
            }
            // Duże obrazy (ilustracje, okładki) wypełniają szerokość tekstu.
            if (bmp.width >= 600 && w < contentW * 0.85f && h < maxH * 0.9f) {
                val k = min(contentW / w, maxH / h)
                w *= k
                h *= k
            }
            space(body * 0.5f)
            if (h > bottom - y && hasContent) newPage()
            val c = canvas()
            val x = left + (contentW - w) / 2f
            c.drawBitmap(bmp, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            y += h
            hasContent = true
            space(body * 0.6f)
            afterHeading = false
        } finally {
            bmp.recycle()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > 2200) sample *= 2
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    private fun table(t: Table) {
        val cols = t.rows.maxOfOrNull { it.size } ?: return
        if (cols == 0) return
        val size = body * if (cols > 4) 0.82f else 0.9f
        val pad = size * 0.4f
        // Szerokości kolumn według długości tekstu (z rozsądnym minimum).
        val weights = FloatArray(cols) { c ->
            val longest = t.rows.maxOfOrNull { r -> r.getOrNull(c)?.sumOf { it.text.length } ?: 0 } ?: 0
            min(40f, max(6f, longest.toFloat()))
        }
        val total = weights.sum()
        val colW = FloatArray(cols) { contentW * weights[it] / total }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xC7, 0xC7, 0xCC)
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        val headerFill = Paint().apply { color = Color.rgb(0xF2, 0xF2, 0xF4) }
        space(body * 0.4f)
        for ((ri, row) in t.rows.withIndex()) {
            val header = ri == 0 && t.header
            val layouts = (0 until cols).map { c ->
                val spans = row.getOrNull(c) ?: emptyList()
                val text = spannable(if (header) spans.map { it.copy(bold = true) } else spans, size)
                layout(text, paint(size), (colW[c] - 2 * pad).toInt(), Layout.Alignment.ALIGN_NORMAL, false, 1.2f)
            }
            val rowH = (layouts.maxOf { it.height } + 2 * pad).coerceAtMost(bottom - top)
            if (rowH > bottom - y && hasContent) newPage()
            val c = canvas()
            var x = left
            for (ci in 0 until cols) {
                val r = RectF(x, y, x + colW[ci], y + rowH)
                if (header) c.drawRect(r, headerFill)
                c.drawRect(r, border)
                c.save()
                c.translate(x + pad, y + pad)
                c.clipRect(0f, 0f, colW[ci] - pad, rowH - pad)
                layouts[ci].draw(c)
                c.restore()
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
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0xB0, 0xB0, 0xB5)
            strokeWidth = 0.7f
        }
        val w = contentW * 0.3f
        c.drawLine(left + (contentW - w) / 2f, y, left + (contentW + w) / 2f, y, p)
        hasContent = true
        space(body * 0.8f)
        afterHeading = true
    }

    // ------------------------------------------------------------------ spis treści

    /** Spis treści PDF z nagłówków (najwyżej trzy poziomy) i metadane dokumentu. */
    private fun addOutline(file: File) {
        val entries = headings.filter { it.second <= 3 && it.first.isNotEmpty() }
        if (entries.isEmpty() && doc.title == null && doc.author == null) return
        runCatching {
            PdfOps.edit(file) { d ->
                doc.title?.let { d.documentInformation.title = it }
                doc.author?.let { d.documentInformation.author = it }
                d.documentInformation.producer = "OpenReader"
                if (entries.isEmpty()) return@edit
                val outline = PDDocumentOutline()
                d.documentCatalog.documentOutline = outline
                // Stos rodziców według poziomów nagłówków.
                val stack = ArrayList<Pair<Int, PDOutlineItem>>()
                for ((title, level, pageIndex) in entries.take(2000)) {
                    val item = PDOutlineItem()
                    item.title = title.take(200)
                    val pg = d.getPage(pageIndex.coerceIn(0, d.numberOfPages - 1))
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
        }
    }
}
