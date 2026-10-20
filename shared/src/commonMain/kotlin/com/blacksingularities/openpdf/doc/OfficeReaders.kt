package com.blacksingularities.openpdf.doc

import com.blacksingularities.openpdf.i18n.tr
import okio.FileSystem
import okio.Path.Companion.toPath

private fun FileSystem.xml(path: String): XElement? = runCatching {
    val bytes = read(path.toPath()) { readByteArray() }
    Markup.parse(decodeText(bytes, declaredCharset(bytes)), html = false)
}.getOrNull()

private fun FileSystem.bytes(path: String): ByteArray? = runCatching { read(path.toPath()) { readByteArray() } }.getOrNull()

/** Wartość logiczna atrybutu „w:val” (brak atrybutu = włączone). */
private fun XElement?.on(): Boolean {
    if (this == null) return false
    val v = attr("val")?.lowercase() ?: return true
    return v != "0" && v != "false" && v != "off" && v != "none"
}

private fun roman(n: Int): String {
    if (n <= 0) return n.toString()
    val vals = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val syms = arrayOf("m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i")
    var x = n
    val sb = StringBuilder()
    for (i in vals.indices) while (x >= vals[i]) {
        sb.append(syms[i])
        x -= vals[i]
    }
    return sb.toString()
}

private fun letters(n: Int): String {
    if (n <= 0) return n.toString()
    var x = n
    val sb = StringBuilder()
    while (x > 0) {
        val r = (x - 1) % 26
        sb.insert(0, 'a' + r)
        x = (x - 1) / 26
    }
    return sb.toString()
}

private fun formatNumber(n: Int, fmt: String): String = when (fmt) {
    "lowerLetter" -> letters(n)
    "upperLetter" -> letters(n).uppercase()
    "lowerRoman" -> roman(n)
    "upperRoman" -> roman(n).uppercase()
    "decimalZero" -> n.toString().padStart(2, '0')
    else -> n.toString()
}

// ------------------------------------------------------------------ DOCX (Word 2007+)

internal object Docx {
    private class StyleInfo(
        val name: String,
        val basedOn: String?,
        val outline: Int?,
        val bold: Boolean?,
        val italic: Boolean?,
        val align: Align?,
    )

    private class Level(val fmt: String, val text: String, val start: Int)

    private class Ctx(val zip: FileSystem) {
        val styles = HashMap<String, StyleInfo>()
        val rels = HashMap<String, String>()
        val abstractNums = HashMap<String, Map<Int, Level>>()
        val nums = HashMap<String, String>()
        val counters = HashMap<String, IntArray>()
    }

    fun read(zip: FileSystem): TextDocument {
        val doc = zip.xml("/word/document.xml") ?: error(tr("Brak treści dokumentu"))
        val ctx = Ctx(zip)
        zip.xml("/word/_rels/document.xml.rels")?.findAll("Relationship")?.forEach { r ->
            val id = r.attr("id") ?: return@forEach
            ctx.rels[id] = r.attr("target") ?: return@forEach
        }
        zip.xml("/word/styles.xml")?.findAll("style")?.forEach { s ->
            val id = s.attr("styleid") ?: return@forEach
            val ppr = s.child("pPr")
            val rpr = s.child("rPr")
            ctx.styles[id] = StyleInfo(
                name = s.child("name")?.attr("val")?.lowercase() ?: id.lowercase(),
                basedOn = s.child("basedOn")?.attr("val"),
                outline = ppr?.child("outlineLvl")?.attr("val")?.toIntOrNull(),
                bold = rpr?.child("b")?.on(),
                italic = rpr?.child("i")?.on(),
                align = ppr?.child("jc")?.attr("val")?.let { jc(it) },
            )
        }
        zip.xml("/word/numbering.xml")?.let { n ->
            for (a in n.findAll("abstractNum")) {
                val id = a.attr("abstractnumid") ?: continue
                val levels = HashMap<Int, Level>()
                for (l in a.all("lvl")) {
                    val ilvl = l.attr("ilvl")?.toIntOrNull() ?: continue
                    levels[ilvl] = Level(
                        l.child("numFmt")?.attr("val") ?: "decimal",
                        l.child("lvlText")?.attr("val") ?: "%${ilvl + 1}.",
                        l.child("start")?.attr("val")?.toIntOrNull() ?: 1,
                    )
                }
                ctx.abstractNums[id] = levels
            }
            for (num in n.findAll("num")) {
                val id = num.attr("numid") ?: continue
                ctx.nums[id] = num.child("abstractNumId")?.attr("val") ?: continue
            }
        }
        val core = zip.xml("/docProps/core.xml")
        val title = core?.find("title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val author = core?.find("creator")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val sink = BlockSink()
        val body = doc.find("body") ?: doc
        Walker(ctx, sink).content(body)
        sink.flush()
        val lang = zip.xml("/word/settings.xml")?.find("themeFontLang")?.attr("val")?.substringBefore('-')
        return TextDocument(title, author, lang, sink.blocks, PageStyle.DOCUMENT)
    }

    private fun jc(v: String): Align? = when (v.lowercase()) {
        "center" -> Align.CENTER
        "right", "end" -> Align.END
        "both", "distribute", "lowkashida", "mediumkashida", "highkashida" -> Align.JUSTIFY
        "left", "start" -> Align.START
        else -> null
    }

    private class Walker(val ctx: Ctx, val sink: BlockSink) {
        private fun style(id: String?): List<StyleInfo> {
            val chain = ArrayList<StyleInfo>()
            var cur = id
            var guard = 0
            while (cur != null && guard++ < 8) {
                val s = ctx.styles[cur] ?: break
                chain += s
                cur = s.basedOn
            }
            return chain
        }

        fun content(container: XElement) {
            for (c in container.elements()) {
                when (c.local) {
                    "p" -> paragraph(c)
                    "tbl" -> table(c)
                    "sdt" -> c.child("sdtContent")?.let { content(it) }
                    "customxml", "ins", "smarttag" -> content(c)
                    "txbxcontent" -> content(c)
                }
            }
        }

        private fun paragraph(p: XElement) {
            sink.flush()
            val ppr = p.child("pPr")
            val chain = style(ppr?.child("pStyle")?.attr("val"))
            val names = chain.map { it.name }
            val outline = ppr?.child("outlineLvl")?.attr("val")?.toIntOrNull() ?: chain.firstNotNullOfOrNull { it.outline }
            val headingByName = names.firstNotNullOfOrNull { n ->
                when {
                    n == "title" -> 1
                    n == "subtitle" -> 3
                    n.startsWith("heading ") -> n.removePrefix("heading ").trim().toIntOrNull()
                    n.startsWith("nagłówek ") -> n.removePrefix("nagłówek ").trim().toIntOrNull()
                    else -> null
                }
            }
            val level = headingByName ?: outline?.takeIf { it in 0..5 }?.plus(1)
            val kind = when {
                level != null -> ParaKind.HEADING
                names.any { "quote" in it || "cytat" in it } -> ParaKind.QUOTE
                names.any { "code" in it || "preformatted" in it || "source" in it } -> ParaKind.CODE
                names.any { it == "caption" || "legenda" in it } -> ParaKind.CAPTION
                else -> ParaKind.BODY
            }
            if (ppr?.child("pageBreakBefore").on()) sink.add(PageBreak)
            sink.kind = kind
            sink.level = level ?: 0
            sink.align = ppr?.child("jc")?.attr("val")?.let { jc(it) } ?: chain.firstNotNullOfOrNull { it.align }
            // Lista: numer albo punktor według definicji z numbering.xml.
            val numPr = ppr?.child("numPr")
            val numId = numPr?.child("numId")?.attr("val")
            if (numId != null && numId != "0") {
                val ilvl = numPr.child("ilvl")?.attr("val")?.toIntOrNull()?.coerceIn(0, 8) ?: 0
                sink.listLevel = ilvl + 1
                sink.marker = marker(numId, ilvl)
            } else {
                sink.listLevel = 0
                sink.marker = null
            }
            val base = Ink(bold = chain.firstNotNullOfOrNull { it.bold } ?: false, italic = chain.firstNotNullOfOrNull { it.italic } ?: false)
            val before = sink.blocks.size
            inline(p, base)
            val empty = !sink.hasText && sink.blocks.size == before
            sink.flush()
            // Puste akapity służą w Wordzie za odstępy – zostaje najwyżej jeden z rzędu.
            if (empty && sink.blocks.lastOrNull().let { it !is Para || it.text.isNotBlank() } && sink.blocks.isNotEmpty()) sink.blank()
            sink.kind = ParaKind.BODY
            sink.level = 0
            sink.align = null
            sink.listLevel = 0
            sink.marker = null
            // Koniec sekcji „od nowej strony”.
            val sect = ppr?.child("sectPr")
            if (sect != null && sect.child("type")?.attr("val").let { it == null || it == "nextPage" || it == "oddPage" || it == "evenPage" }) sink.add(PageBreak)
        }

        private fun marker(numId: String, ilvl: Int): String? {
            val levels = ctx.abstractNums[ctx.nums[numId] ?: return HtmlConverter.bullet(ilvl + 1)] ?: return HtmlConverter.bullet(ilvl + 1)
            val lvl = levels[ilvl] ?: return HtmlConverter.bullet(ilvl + 1)
            val counts = ctx.counters.getOrPut(numId) { IntArray(9) }
            counts[ilvl] = if (counts[ilvl] == 0) lvl.start else counts[ilvl] + 1
            for (k in ilvl + 1 until 9) counts[k] = 0
            return when (lvl.fmt) {
                "bullet" -> HtmlConverter.bullet(ilvl + 1)
                "none" -> null
                else -> {
                    var t = lvl.text
                    for (k in 0..ilvl) {
                        val f = levels[k]?.fmt ?: "decimal"
                        val v = if (counts[k] == 0) levels[k]?.start ?: 1 else counts[k]
                        t = t.replace("%${k + 1}", formatNumber(v, if (k == ilvl) lvl.fmt else f))
                    }
                    t.ifBlank { null }
                }
            }
        }

        private fun inline(e: XElement, ink: Ink) {
            for (c in e.elements()) {
                when (c.local) {
                    "r" -> run(c, ink)
                    "hyperlink" -> inline(c, ink.copy(link = true))
                    "ins", "smarttag", "customxml", "fldsimple", "bdo", "dir", "moveto" -> inline(c, ink)
                    "sdt" -> c.child("sdtContent")?.let { inline(it, ink) }
                    "alternatecontent" -> c.child("choice")?.let { inline(it, ink) }
                }
            }
        }

        private fun run(r: XElement, base: Ink) {
            val rpr = r.child("rPr")
            var ink = base
            if (rpr != null) {
                rpr.child("rStyle")?.attr("val")?.let { id ->
                    val chain = style(id)
                    chain.firstNotNullOfOrNull { it.bold }?.let { ink = ink.copy(bold = it) }
                    chain.firstNotNullOfOrNull { it.italic }?.let { ink = ink.copy(italic = it) }
                    if (chain.any { "hyperlink" in it.name || "link" in it.name }) ink = ink.copy(link = true)
                }
                rpr.child("b")?.let { ink = ink.copy(bold = it.on()) }
                rpr.child("i")?.let { ink = ink.copy(italic = it.on()) }
                rpr.child("u")?.let { ink = ink.copy(underline = it.on()) }
                if (rpr.child("strike").on() || rpr.child("dstrike").on()) ink = ink.copy(strike = true)
                when (rpr.child("vertAlign")?.attr("val")) {
                    "superscript" -> ink = ink.copy(sup = true)
                    "subscript" -> ink = ink.copy(sub = true)
                }
                val font = rpr.child("rFonts")?.attr("ascii")?.lowercase().orEmpty()
                if ("courier" in font || "consolas" in font || "mono" in font) ink = ink.copy(mono = true)
                if (rpr.child("vanish").on()) return
            }
            for (c in r.elements()) {
                when (c.local) {
                    "t" -> sink.text(c.text(), ink)
                    "tab", "ptab" -> sink.text("\t", ink)
                    "br" -> if (c.attr("type") == "page") sink.add(PageBreak) else sink.lineBreak()
                    "cr" -> sink.lineBreak()
                    "nobreakhyphen" -> sink.text("‑", ink)
                    "drawing", "pict", "object" -> media(c)
                    "alternatecontent" -> c.child("choice")?.let { ch -> ch.elements().forEach { if (it.local == "drawing" || it.local == "pict") media(it) } }
                }
            }
        }

        /** Obrazy (a:blip, v:imagedata) i pola tekstowe w rysunku. */
        private fun media(e: XElement) {
            val ids = e.findAll("blip").mapNotNull { it.attr("embed") } + e.findAll("imagedata").mapNotNull { it.attr("id") }
            for (id in ids.distinct()) {
                val target = ctx.rels[id] ?: continue
                val path = if (target.startsWith("/")) target else "/word/" + target.removePrefix("./")
                val bytes = ctx.zip.bytes(Epub.resolve("/".toPath(), path).toString()) ?: continue
                if (imageMime(bytes) != null) sink.add(Picture(bytes))
            }
            e.findAll("txbxcontent").forEach { box ->
                val k = sink.kind
                val l = sink.level
                sink.flush()
                content(box)
                sink.kind = k
                sink.level = l
            }
        }

        private fun table(t: XElement) {
            sink.flush()
            val rows = ArrayList<List<List<Span>>>()
            var header = false
            for ((ri, tr) in t.all("tr").withIndex()) {
                if (ri == 0 && tr.child("trPr")?.child("tblHeader") != null) header = true
                val cells = tr.all("tc").map { tc ->
                    val sub = BlockSink()
                    Walker(ctx, sub).content(tc)
                    sub.cellSpans()
                }
                if (cells.isNotEmpty()) rows += cells
            }
            if (rows.isNotEmpty()) sink.add(Table(rows, header))
        }
    }
}

// ------------------------------------------------------------------ ODT (OpenDocument)

internal object Odt {
    private class OStyle(
        val parent: String?,
        val display: String,
        val bold: Boolean?,
        val italic: Boolean?,
        val underline: Boolean?,
        val strike: Boolean?,
        val sup: Boolean?,
        val sub: Boolean?,
        val mono: Boolean?,
        val align: Align?,
        val breakBefore: Boolean,
        val outline: Int?,
    )

    private class Ctx(val zip: FileSystem) {
        val styles = HashMap<String, OStyle>()
        /** Styl listy → czy poziom (od 1) jest numerowany. */
        val lists = HashMap<String, Map<Int, Boolean>>()
    }

    fun read(zip: FileSystem): TextDocument {
        val content = zip.xml("/content.xml") ?: error(tr("Brak treści dokumentu"))
        val ctx = Ctx(zip)
        listOfNotNull(zip.xml("/styles.xml"), content).forEach { collectStyles(it, ctx) }
        val meta = zip.xml("/meta.xml")
        val title = meta?.find("title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val author = (meta?.find("initial-creator") ?: meta?.find("creator"))?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val lang = meta?.find("language")?.text()?.trim()?.substringBefore('-')
        val sink = BlockSink()
        val text = content.find("body")?.find("text") ?: content
        Walker(ctx, sink).content(text, null, 0)
        sink.flush()
        return TextDocument(title, author, lang, sink.blocks, PageStyle.DOCUMENT)
    }

    private fun collectStyles(root: XElement, ctx: Ctx) {
        for (s in root.findAll("style")) {
            if (s.name != "style:style") continue
            val name = s.attr("name") ?: continue
            val tp = s.child("text-properties")
            val pp = s.child("paragraph-properties")
            fun tpAttr(n: String) = tp?.attrs?.entries?.firstOrNull { it.key.substringAfter(':') == n }?.value
            val weight = tpAttr("font-weight")
            val pos = tpAttr("text-position")
            val font = (tpAttr("font-name") ?: tpAttr("font-family"))?.lowercase().orEmpty()
            val align = pp?.attrs?.entries?.firstOrNull { it.key.substringAfter(':') == "text-align" }?.value
            ctx.styles[name] = OStyle(
                parent = s.attr("parent-style-name"),
                display = (s.attr("display-name") ?: name).lowercase().replace("_20_", " "),
                bold = weight?.let { it == "bold" || (it.toIntOrNull() ?: 400) >= 600 },
                italic = tpAttr("font-style")?.let { it == "italic" || it == "oblique" },
                underline = tpAttr("text-underline-style")?.let { it != "none" },
                strike = tpAttr("text-line-through-style")?.let { it != "none" },
                sup = pos?.let { it.startsWith("super") || (it.substringBefore(' ').removeSuffix("%").toIntOrNull() ?: 0) > 0 },
                sub = pos?.let { it.startsWith("sub") || (it.substringBefore(' ').removeSuffix("%").toIntOrNull() ?: 0) < 0 },
                mono = if (font.isEmpty()) null else ("mono" in font || "courier" in font || "consolas" in font),
                align = when (align) {
                    "center" -> Align.CENTER
                    "end", "right" -> Align.END
                    "justify" -> Align.JUSTIFY
                    "start", "left" -> Align.START
                    else -> null
                },
                breakBefore = pp?.attrs?.entries?.any { it.key.substringAfter(':') == "break-before" && it.value == "page" } == true,
                outline = s.attr("default-outline-level")?.toIntOrNull(),
            )
        }
        for (l in root.findAll("list-style")) {
            val name = l.attr("name") ?: continue
            val levels = HashMap<Int, Boolean>()
            for (lv in l.elements()) {
                val n = lv.attr("level")?.toIntOrNull() ?: continue
                levels[n] = lv.local == "list-level-style-number"
            }
            ctx.lists[name] = levels
        }
    }

    private class Walker(val ctx: Ctx, val sink: BlockSink) {
        private val counters = HashMap<Int, Int>()

        private fun chain(name: String?): List<OStyle> {
            val out = ArrayList<OStyle>()
            var cur = name
            var guard = 0
            while (cur != null && guard++ < 8) {
                val s = ctx.styles[cur] ?: break
                out += s
                cur = s.parent
            }
            return out
        }

        private fun ink(base: Ink, styleName: String?): Ink {
            val c = chain(styleName)
            if (c.isEmpty()) return base
            return base.copy(
                bold = c.firstNotNullOfOrNull { it.bold } ?: base.bold,
                italic = c.firstNotNullOfOrNull { it.italic } ?: base.italic,
                underline = c.firstNotNullOfOrNull { it.underline } ?: base.underline,
                strike = c.firstNotNullOfOrNull { it.strike } ?: base.strike,
                sup = c.firstNotNullOfOrNull { it.sup } ?: base.sup,
                sub = c.firstNotNullOfOrNull { it.sub } ?: base.sub,
                mono = c.firstNotNullOfOrNull { it.mono } ?: base.mono,
            )
        }

        fun content(e: XElement, listStyle: String?, depth: Int) {
            for (c in e.elements()) {
                when (c.local) {
                    "h" -> paragraph(c, c.attr("outline-level")?.toIntOrNull() ?: 1, listStyle, depth, null)
                    "p" -> paragraph(c, null, listStyle, depth, null)
                    "list" -> list(c, c.attr("style-name") ?: listStyle, depth + 1)
                    "table" -> table(c)
                    "section", "index-body", "table-of-content", "alphabetical-index", "illustration-index", "deletion" -> if (c.local != "deletion") content(c, listStyle, depth)
                }
            }
        }

        private fun list(l: XElement, style: String?, depth: Int) {
            if (l.attr("continue-numbering") != "true") counters[depth] = 0
            val numbered = style?.let { ctx.lists[it]?.get(depth) } ?: false
            for (item in l.elements()) {
                if (item.local != "list-item" && item.local != "list-header") continue
                var markerUsed = false
                val n = (counters[depth] ?: 0) + 1
                counters[depth] = n
                for (k in (depth + 1)..10) counters.remove(k)
                for (c in item.elements()) {
                    when (c.local) {
                        "p", "h" -> {
                            val m = if (!markerUsed && item.local == "list-item") (if (numbered) "$n." else HtmlConverter.bullet(depth)) else null
                            markerUsed = true
                            paragraph(c, if (c.local == "h") c.attr("outline-level")?.toIntOrNull() ?: 1 else null, style, depth, m)
                        }
                        "list" -> list(c, c.attr("style-name") ?: style, depth + 1)
                        "table" -> table(c)
                    }
                }
            }
        }

        private fun paragraph(p: XElement, heading: Int?, listStyle: String?, depth: Int, marker: String?) {
            sink.flush()
            val c = chain(p.attr("style-name"))
            val names = c.map { it.display }
            val level = heading ?: names.firstNotNullOfOrNull { n ->
                when {
                    n == "title" -> 1
                    n == "subtitle" -> 3
                    n.startsWith("heading ") -> n.removePrefix("heading ").trim().toIntOrNull()
                    else -> null
                }
            } ?: c.firstNotNullOfOrNull { it.outline }
            if (c.any { it.breakBefore }) sink.add(PageBreak)
            sink.kind = when {
                level != null -> ParaKind.HEADING
                names.any { "quotation" in it || "quote" in it || "cytat" in it } -> ParaKind.QUOTE
                names.any { "preformatted" in it || "code" in it } -> ParaKind.CODE
                names.any { "caption" in it || "illustration" in it } -> ParaKind.CAPTION
                else -> ParaKind.BODY
            }
            sink.level = level ?: 0
            sink.align = c.firstNotNullOfOrNull { it.align }
            sink.listLevel = depth
            sink.marker = marker
            inline(p, ink(Ink(), p.attr("style-name")))
            sink.flush()
            sink.kind = ParaKind.BODY
            sink.level = 0
            sink.align = null
            sink.listLevel = 0
            sink.marker = null
        }

        private fun inline(e: XElement, ink: Ink) {
            for (n in e.children) {
                when (n) {
                    is XText -> sink.text(n.text, ink)
                    is XElement -> when (n.local) {
                        "span" -> inline(n, ink(ink, n.attr("style-name")))
                        "a" -> inline(n, ink.copy(link = true))
                        "s" -> sink.text(" ".repeat((n.attr("c")?.toIntOrNull() ?: 1).coerceIn(1, 40)), ink)
                        "tab" -> sink.text("\t", ink)
                        "line-break" -> sink.lineBreak()
                        "note" -> n.child("note-citation")?.let { sink.text(it.text(), ink.copy(sup = true)) }
                        "frame" -> frame(n)
                        "ruby" -> n.child("ruby-base")?.let { inline(it, ink) }
                        "meta", "bookmark-ref", "reference-ref", "sequence", "page-number", "date", "time", "title", "chapter",
                        "author-name", "variable-get", "user-field-get", "text-input", "sender-firstname" -> inline(n, ink)
                    }
                }
            }
        }

        private fun frame(f: XElement) {
            f.child("image")?.attr("href")?.let { href ->
                val bytes = ctx.zip.bytes(Epub.resolve("/".toPath(), href).toString())
                if (bytes != null && imageMime(bytes) != null) sink.add(Picture(bytes))
            }
            f.child("text-box")?.let { box ->
                val k = sink.kind
                sink.flush()
                content(box, null, 0)
                sink.kind = k
            }
        }

        private fun table(t: XElement) {
            sink.flush()
            val rows = ArrayList<List<List<Span>>>()
            var header = false
            fun rowsOf(x: XElement) {
                for (c in x.elements()) {
                    when (c.local) {
                        "table-header-rows" -> {
                            if (rows.isEmpty()) header = true
                            rowsOf(c)
                        }
                        "table-rows", "table-row-group" -> rowsOf(c)
                        "table-row" -> {
                            val cells = c.elements().filter { it.local == "table-cell" }.map { cell ->
                                val sub = BlockSink()
                                Walker(ctx, sub).content(cell, null, 0)
                                sub.cellSpans()
                            }
                            if (cells.isNotEmpty()) rows += cells
                        }
                    }
                }
            }
            rowsOf(t)
            if (rows.isNotEmpty()) sink.add(Table(rows, header))
        }
    }
}
