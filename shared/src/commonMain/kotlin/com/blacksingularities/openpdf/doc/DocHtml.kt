package com.blacksingularities.openpdf.doc

/**
 * Dokument jako strona HTML z arkuszem stylów do druku – na iOS składa ją w strony systemowy
 * mechanizm drukowania (UIMarkupTextPrintFormatter). Marginesy strony ustawia platforma.
 */
object DocHtml {
    fun render(doc: TextDocument): String {
        val st = doc.style
        val font = if (st.serif) "'Iowan Old Style', 'Palatino', 'Georgia', 'Times New Roman', serif" else "-apple-system, 'Helvetica Neue', 'Helvetica', 'Arial', sans-serif"
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html")
        doc.language?.let { sb.append(" lang=\"").append(esc(it)).append('"') }
        sb.append("><head><meta charset=\"utf-8\"><style>")
        sb.append("body{margin:0;font-family:").append(font).append(";font-size:").append(st.fontSize).append("pt;line-height:").append(st.lineHeight)
            .append(";color:#1d1d1f;-webkit-hyphens:auto;hyphens:auto;}")
        sb.append("p{margin:0 0 ").append(st.paragraphSpacing).append("em 0;orphans:2;widows:2;")
        if (st.justify) sb.append("text-align:justify;")
        sb.append("}")
        if (st.firstLineIndent > 0f) sb.append("p.b{text-indent:").append(st.firstLineIndent).append("em;}h1+p.b,h2+p.b,h3+p.b,h4+p.b,hr+p.b,.pb+p.b{text-indent:0;}")
        val hAlign = if (st.centerHeadings) "center" else "left"
        sb.append("h1,h2,h3,h4,h5,h6{font-weight:700;line-height:1.2;page-break-after:avoid;margin:1.1em 0 0.5em 0;}")
        sb.append("h1{font-size:1.75em;text-align:").append(hAlign).append(";margin-top:1.6em;}h2{font-size:1.45em;text-align:").append(hAlign).append(";}")
        sb.append("h3{font-size:1.25em;}h4{font-size:1.1em;}h5,h6{font-size:1em;}")
        sb.append("blockquote{margin:0.6em 1.6em;color:#3a3a3c;}")
        sb.append("pre{font-family:Menlo,Courier,monospace;font-size:0.86em;white-space:pre-wrap;background:#f2f2f4;padding:0.5em 0.7em;border-radius:4px;margin:0 0 0.4em 0;}")
        sb.append("code{font-family:Menlo,Courier,monospace;font-size:0.9em;}")
        sb.append(".verse{text-align:left;margin:0 0 0 2em;}.cap{text-align:center;font-size:0.88em;color:#555;font-style:italic;}")
        sb.append("a{color:#1a5fb4;text-decoration:underline;}")
        sb.append("img{display:block;margin:0.6em auto;max-width:100%;max-height:90vh;page-break-inside:avoid;}")
        sb.append("hr{border:0;border-top:1px solid #bbb;width:30%;margin:1.2em auto;}")
        sb.append("table{border-collapse:collapse;width:100%;margin:0.5em 0;font-size:0.92em;page-break-inside:auto;}")
        sb.append("td,th{border:1px solid #c7c7cc;padding:3pt 5pt;vertical-align:top;text-align:left;}th{background:#f2f2f4;}")
        sb.append(".pb{page-break-after:always;height:0;}")
        sb.append("li{margin:0;}")
        sb.append("</style></head><body>")
        for (b in doc.blocks) {
            when (b) {
                is Para -> para(sb, b, st)
                is Picture -> {
                    val mime = imageMime(b.bytes) ?: continue
                    sb.append("<img src=\"data:").append(mime).append(";base64,").append(base64Encode(b.bytes)).append("\"")
                    b.caption?.let { sb.append(" alt=\"").append(esc(it)).append('"') }
                    sb.append(">")
                }
                is Table -> {
                    sb.append("<table>")
                    for ((ri, row) in b.rows.withIndex()) {
                        sb.append("<tr>")
                        val tag = if (ri == 0 && b.header) "th" else "td"
                        for (cell in row) {
                            sb.append('<').append(tag).append('>')
                            spans(sb, cell)
                            sb.append("</").append(tag).append('>')
                        }
                        sb.append("</tr>")
                    }
                    sb.append("</table>")
                }
                PageBreak -> sb.append("<div class=\"pb\"></div>")
                Rule -> sb.append("<hr>")
            }
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun para(sb: StringBuilder, p: Para, st: PageStyle) {
        if (p.text.isBlank()) {
            sb.append("<p>&nbsp;</p>")
            return
        }
        val align = when (p.align) {
            Align.CENTER -> "center"
            Align.END -> "right"
            Align.JUSTIFY -> "justify"
            Align.START -> "left"
            null -> null
        }
        val style = StringBuilder()
        align?.let { style.append("text-align:").append(it).append(';') }
        if (p.listLevel > 0) style.append("margin-left:").append(1.4f * p.listLevel).append("em;text-indent:-1.1em;")
        val styleAttr = if (style.isNotEmpty()) " style=\"$style\"" else ""
        when (p.kind) {
            ParaKind.HEADING -> {
                val l = p.level.coerceIn(1, 6)
                sb.append("<h").append(l).append(styleAttr).append('>')
                spans(sb, p.spans)
                sb.append("</h").append(l).append('>')
            }
            ParaKind.QUOTE -> {
                sb.append("<blockquote><p").append(styleAttr).append('>')
                spans(sb, p.spans)
                sb.append("</p></blockquote>")
            }
            ParaKind.CODE -> {
                sb.append("<pre>")
                for (s in p.spans) sb.append(esc(s.text))
                sb.append("</pre>")
            }
            ParaKind.CAPTION -> {
                sb.append("<p class=\"cap\"").append(styleAttr).append('>')
                spans(sb, p.spans)
                sb.append("</p>")
            }
            ParaKind.VERSE -> {
                sb.append("<p class=\"verse\"").append(styleAttr).append('>')
                spans(sb, p.spans)
                sb.append("</p>")
            }
            ParaKind.BODY -> {
                sb.append(if (p.listLevel == 0 && p.align == null && st.firstLineIndent > 0f) "<p class=\"b\"" else "<p").append(styleAttr).append('>')
                p.marker?.let { sb.append(esc(it)).append("&nbsp;&nbsp;") }
                spans(sb, p.spans)
                sb.append("</p>")
            }
        }
    }

    private fun spans(sb: StringBuilder, spans: List<Span>) {
        for (s in spans) {
            val open = StringBuilder()
            val close = StringBuilder()
            fun wrap(tag: String) {
                open.append('<').append(tag).append('>')
                close.insert(0, "</$tag>")
            }
            if (s.bold) wrap("b")
            if (s.italic) wrap("i")
            if (s.underline) wrap("u")
            if (s.strike) wrap("s")
            if (s.mono) wrap("code")
            if (s.sup) wrap("sup")
            if (s.sub) wrap("sub")
            if (s.link) {
                open.append("<a>")
                close.insert(0, "</a>")
            }
            sb.append(open).append(esc(s.text).replace("\n", "<br>").replace("\t", "&emsp;")).append(close)
        }
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '&' -> sb.append("&amp;")
                '"' -> sb.append("&quot;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
