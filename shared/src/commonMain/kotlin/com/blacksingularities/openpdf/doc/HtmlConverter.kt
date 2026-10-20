package com.blacksingularities.openpdf.doc

/** Bieżący wygląd tekstu w trakcie przechodzenia po drzewie dokumentu. */
internal data class Ink(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val mono: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
    val link: Boolean = false,
) {
    fun span(text: String) = Span(text, bold, italic, underline, strike, mono, sup, sub, link)
}

private fun Span.sameLook(o: Span) =
    bold == o.bold && italic == o.italic && underline == o.underline && strike == o.strike &&
        mono == o.mono && sup == o.sup && sub == o.sub && link == o.link

/**
 * Zbiera tekst w akapity: kolejne fragmenty o tym samym wyglądzie są łączone, białe znaki
 * zwijane jak w HTML (poza blokami kodu), puste akapity pomijane.
 */
internal class BlockSink {
    val blocks = ArrayList<Block>()
    private val spans = ArrayList<Span>()

    var kind = ParaKind.BODY
    var level = 0
    var align: Align? = null
    var listLevel = 0
    /** Znak listy dla najbliższego akapitu (tylko pierwszy akapit elementu listy). */
    var marker: String? = null
    /** Zachowywanie białych znaków (blok kodu, wiersze z pliku tekstowego). */
    var preserve = false

    fun text(raw: String, ink: Ink) {
        if (raw.isEmpty()) return
        val t = if (preserve) raw else collapse(raw)
        if (t.isEmpty()) return
        val last = spans.lastOrNull()
        val text = if (!preserve && t.startsWith(" ") && (last == null || last.text.endsWith(" ") || last.text.endsWith("\n"))) t.substring(1) else t
        if (text.isEmpty()) return
        val s = ink.span(text)
        if (last != null && last.sameLook(s)) spans[spans.size - 1] = last.copy(text = last.text + text) else spans += s
    }

    fun lineBreak() {
        val last = spans.lastOrNull()
        if (last != null) spans[spans.size - 1] = last.copy(text = last.text.trimEnd(' ') + "\n") else if (preserve) spans += Span("\n")
    }

    val hasText: Boolean get() = spans.any { it.text.isNotBlank() }

    fun flush() {
        if (spans.isEmpty()) return
        val list = ArrayList(spans)
        spans.clear()
        if (!preserve) {
            list[0] = list[0].copy(text = list[0].text.trimStart(' ', '\n'))
            val li = list.size - 1
            list[li] = list[li].copy(text = list[li].text.trimEnd(' ', '\n'))
        }
        val cleaned = list.filter { it.text.isNotEmpty() }
        if (cleaned.none { it.text.isNotBlank() }) return
        blocks += Para(cleaned, kind, level, align, listLevel, marker)
        marker = null
    }

    /** Pusty akapit – odstęp (np. „empty-line” w FB2). */
    fun blank() {
        flush()
        val last = blocks.lastOrNull()
        if (last is Para && last.text.isBlank()) return
        blocks += Para(listOf(Span(" ")))
    }

    fun add(b: Block) {
        flush()
        if (b is PageBreak && (blocks.isEmpty() || blocks.last() is PageBreak)) return
        blocks += b
    }

    /** Cały zebrany tekst jako jedna komórka tabeli. */
    fun cellSpans(): List<Span> {
        flush()
        val out = ArrayList<Span>()
        for ((i, b) in blocks.withIndex()) {
            if (b !is Para) continue
            if (i > 0 && out.isNotEmpty()) out += Span("\n")
            out += b.spans
        }
        return out
    }

    private fun collapse(s: String): String {
        val sb = StringBuilder(s.length)
        var ws = false
        for (c in s) {
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\u000C') {
                if (!ws) sb.append(' ')
                ws = true
            } else {
                sb.append(c)
                ws = false
            }
        }
        return sb.toString()
    }
}

/**
 * HTML/XHTML (także rozdziały EPUB i FictionBook) → bloki. [image] zamienia adres obrazu
 * z dokumentu na jego bajty.
 */
internal class HtmlConverter(private val image: (String) -> ByteArray?, private val fb2: Boolean = false) {
    val sink = BlockSink()
    private var listDepth = 0
    private val counters = ArrayList<Int?>()
    private var sectionDepth = 0

    fun convert(root: XElement) {
        walk(root, Ink())
        sink.flush()
    }

    private fun walk(n: XNode, ink: Ink) {
        when (n) {
            is XText -> sink.text(n.text, ink)
            is XElement -> element(n, ink)
        }
    }

    private fun children(e: XElement, ink: Ink) {
        for (c in e.children) walk(c, ink)
    }

    private fun element(e: XElement, ink0: Ink) {
        val name = e.local
        if (name in SKIP) return
        val css = e.attrs["style"]?.lowercase()?.replace(" ", "") ?: ""
        if ("display:none" in css) return
        if ("page-break-before:always" in css || "break-before:page" in css) sink.add(PageBreak)
        var ink = ink0
        if ("font-weight:bold" in css || Regex("font-weight:[6-9]00").containsMatchIn(css)) ink = ink.copy(bold = true)
        if ("font-style:italic" in css) ink = ink.copy(italic = true)
        if ("text-decoration:underline" in css) ink = ink.copy(underline = true)
        if ("line-through" in css) ink = ink.copy(strike = true)
        if ("vertical-align:super" in css) ink = ink.copy(sup = true)
        if ("vertical-align:sub" in css) ink = ink.copy(sub = true)
        val align = alignOf(e, css)
        if (fb2 && name == "cite") {
            block(e, ink, ParaKind.QUOTE, 0, align)
            return
        }
        when (name) {
            "b", "strong" -> children(e, ink.copy(bold = true))
            "i", "em", "cite", "dfn", "var", "emphasis" -> children(e, ink.copy(italic = true))
            "u", "ins" -> children(e, ink.copy(underline = true))
            "s", "strike", "del", "strikethrough" -> children(e, ink.copy(strike = true))
            "code", "tt", "kbd", "samp" -> children(e, ink.copy(mono = true))
            "sup" -> children(e, ink.copy(sup = true))
            "sub" -> children(e, ink.copy(sub = true))
            "a" -> children(e, if (e.attr("href") != null) ink.copy(link = true) else ink)
            "br" -> sink.lineBreak()
            "img", "image" -> picture(e)
            "hr" -> sink.add(Rule)
            "empty-line" -> sink.blank()
            "h1", "h2", "h3", "h4", "h5", "h6" -> block(e, ink, ParaKind.HEADING, name[1] - '0', align)
            "title" -> block(e, ink, ParaKind.HEADING, (sectionDepth).coerceIn(1, 4), align)
            "subtitle" -> block(e, ink, ParaKind.HEADING, (sectionDepth + 2).coerceIn(3, 6), align ?: Align.CENTER)
            "section" -> {
                // FictionBook: zagnieżdżone sekcje – kolejne poziomy nagłówków; każdy rozdział od nowej strony.
                if (fb2 && sectionDepth == 0 && sink.blocks.isNotEmpty()) sink.add(PageBreak)
                sectionDepth++
                block(e, ink, sink.kind, sink.level, align)
                sectionDepth--
            }
            "blockquote", "epigraph", "cite-block" -> block(e, ink, ParaKind.QUOTE, 0, align)
            "pre" -> {
                sink.flush()
                val was = sink.preserve
                sink.preserve = true
                block(e, ink.copy(mono = true), ParaKind.CODE, 0, align)
                sink.preserve = was
            }
            "poem", "stanza" -> block(e, ink, ParaKind.VERSE, 0, align)
            "v" -> block(e, ink, ParaKind.VERSE, 0, align)
            "text-author" -> block(e, ink.copy(italic = true), sink.kind, 0, Align.END)
            "figcaption", "caption" -> block(e, ink, ParaKind.CAPTION, 0, align ?: Align.CENTER)
            "ul", "ol" -> list(e, ink, ordered = name == "ol")
            "li" -> item(e, ink)
            "table" -> table(e)
            "dt" -> block(e, ink.copy(bold = true), sink.kind, 0, align)
            "dd" -> {
                sink.flush()
                val was = sink.listLevel
                sink.listLevel = was + 1
                block(e, ink, sink.kind, 0, align)
                sink.listLevel = was
            }
            in BLOCKS -> block(e, ink, sink.kind, sink.level, align)
            else -> children(e, ink)
        }
    }

    private fun block(e: XElement, ink: Ink, kind: ParaKind, level: Int, align: Align?) {
        sink.flush()
        val k0 = sink.kind
        val l0 = sink.level
        val a0 = sink.align
        sink.kind = kind
        sink.level = level
        if (align != null) sink.align = align
        children(e, ink)
        sink.flush()
        sink.kind = k0
        sink.level = l0
        sink.align = a0
    }

    private fun list(e: XElement, ink: Ink, ordered: Boolean) {
        sink.flush()
        listDepth++
        counters += if (ordered) (e.attr("start")?.toIntOrNull() ?: 1) - 1 else null
        val was = sink.listLevel
        sink.listLevel = listDepth
        children(e, ink)
        sink.flush()
        sink.listLevel = was
        counters.removeAt(counters.size - 1)
        listDepth--
    }

    private fun item(e: XElement, ink: Ink) {
        sink.flush()
        val idx = counters.size - 1
        val marker = if (idx >= 0 && counters[idx] != null) {
            val n = counters[idx]!! + 1
            counters[idx] = n
            "$n."
        } else {
            bullet(listDepth)
        }
        val was = sink.listLevel
        if (sink.listLevel == 0) sink.listLevel = 1
        sink.marker = marker
        children(e, ink)
        sink.flush()
        sink.marker = null
        sink.listLevel = was
    }

    private fun table(e: XElement) {
        sink.flush()
        val rows = ArrayList<List<List<Span>>>()
        var header = false
        val trs = ArrayList<XElement>()
        fun collect(x: XElement) {
            for (c in x.elements()) {
                when (c.local) {
                    "tr" -> trs += c
                    "thead", "tbody", "tfoot" -> collect(c)
                }
            }
        }
        collect(e)
        for ((ri, tr) in trs.withIndex()) {
            val cells = tr.elements().filter { it.local == "td" || it.local == "th" }
            if (cells.isEmpty()) continue
            if (ri == 0 && cells.all { it.local == "th" }) header = true
            rows += cells.map { cell ->
                val sub = HtmlConverter(image, fb2)
                sub.walk(cell, Ink())
                sub.sink.cellSpans()
            }
        }
        if (rows.isNotEmpty()) sink.add(Table(rows, header))
    }

    private fun picture(e: XElement) {
        val src = e.attr("src") ?: e.attr("href") ?: return
        val bytes = runCatching { image(src) }.getOrNull() ?: return
        if (imageMime(bytes) == null) return
        // Małe obrazki w tekście (ozdobniki, ikony) zostają w akapicie jako znak – pomijamy je.
        sink.add(Picture(bytes, e.attr("alt")?.takeIf { it.isNotBlank() && it.length < 120 }))
    }

    private fun alignOf(e: XElement, css: String): Align? {
        val v = Regex("text-align:([a-z]+)").find(css)?.groupValues?.get(1) ?: e.attrs["align"]?.lowercase()
        return when (v) {
            "center" -> Align.CENTER
            "right", "end" -> Align.END
            "justify" -> Align.JUSTIFY
            "left", "start" -> Align.START
            else -> if (e.local == "center") Align.CENTER else null
        }
    }

    companion object {
        private val SKIP = setOf("head", "script", "style", "noscript", "template", "rt", "rp", "binary", "description", "annotation", "svg-defs", "object", "iframe", "button", "input", "select", "textarea", "form", "map")
        private val BLOCKS = setOf(
            "p", "div", "section", "article", "header", "footer", "main", "aside", "figure", "address", "center",
            "body", "html", "#root", "nav", "dl", "fictionbook", "details", "summary", "hgroup",
        )

        fun bullet(depth: Int) = when ((depth - 1).coerceAtLeast(0) % 3) {
            0 -> "•"
            1 -> "◦"
            else -> "▪"
        }
    }
}
