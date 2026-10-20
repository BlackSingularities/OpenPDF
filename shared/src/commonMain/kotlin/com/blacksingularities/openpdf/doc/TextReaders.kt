package com.blacksingularities.openpdf.doc

// ------------------------------------------------------------------ RTF

/** Uproszczony odczyt RTF: tekst, akapity, wyrównanie, pogrubienie, kursywa, podkreślenie, znaki Unicode. */
internal object Rtf {
    private val SKIP = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "headerl", "headerr", "headerf",
        "footerl", "footerr", "footerf", "footnote", "fldinst", "object", "themedata", "colorschememapping",
        "latentstyles", "datastore", "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "generator",
        "pgdsctbl", "mmathpr", "filetbl", "revtbl", "bkmkstart", "bkmkend", "xe", "tc", "pn", "pntext", "listtext",
        "nonshppict", "shpinst", "sp", "background", "userprops", "docvar", "wgrffmtfilter", "pnseclvl",
    )

    private data class State(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val sup: Boolean = false,
        val sub: Boolean = false,
        val skip: Boolean = false,
        val uc: Int = 1,
    )

    fun read(bytes: ByteArray, title: String): TextDocument {
        val sink = BlockSink()
        var codepage = "windows-1252"
        val stack = ArrayList<State>()
        var st = State()
        val pending = ArrayList<Byte>()
        var skipChars = 0
        var i = 0
        val n = bytes.size
        fun ink() = Ink(st.bold, st.italic, st.underline, st.strike, sup = st.sup, sub = st.sub)
        fun flushBytes() {
            if (pending.isEmpty()) return
            val arr = pending.toByteArray()
            pending.clear()
            if (!st.skip) sink.text(decodeCharset(arr, codepage) ?: arr.decodeToString(), ink())
        }
        fun emit(s: String) {
            flushBytes()
            if (!st.skip) sink.text(s, ink())
        }
        fun byteAt(k: Int) = bytes[k].toInt() and 0xFF
        while (i < n) {
            val c = byteAt(i)
            when (c) {
                '{'.code -> {
                    flushBytes()
                    stack += st
                    i++
                }
                '}'.code -> {
                    flushBytes()
                    st = stack.removeLastOrNull() ?: State()
                    i++
                }
                '\\'.code -> {
                    if (i + 1 >= n) break
                    val d = byteAt(i + 1)
                    when {
                        d == '\''.code -> {
                            val hex = if (i + 3 < n) bytes.copyOfRange(i + 2, i + 4).decodeToString().toIntOrNull(16) else null
                            i += 4
                            if (skipChars > 0) skipChars-- else if (hex != null && !st.skip) pending += hex.toByte()
                        }
                        d == '*'.code -> {
                            st = st.copy(skip = true)
                            i += 2
                        }
                        d == '\\'.code || d == '{'.code || d == '}'.code -> {
                            if (skipChars > 0) skipChars-- else emit(d.toChar().toString())
                            i += 2
                        }
                        d == '~'.code -> {
                            emit(" ")
                            i += 2
                        }
                        d == '_'.code -> {
                            emit("‑")
                            i += 2
                        }
                        d == '-'.code -> i += 2
                        d == '\n'.code || d == '\r'.code -> {
                            flushBytes()
                            if (!st.skip) sink.flush()
                            i += 2
                        }
                        (d in 'a'.code..'z'.code) || (d in 'A'.code..'Z'.code) -> {
                            var j = i + 1
                            while (j < n && (byteAt(j) in 'a'.code..'z'.code || byteAt(j) in 'A'.code..'Z'.code)) j++
                            val word = bytes.copyOfRange(i + 1, j).decodeToString().lowercase()
                            var k = j
                            if (k < n && (byteAt(k) == '-'.code || byteAt(k) in '0'.code..'9'.code)) {
                                k++
                                while (k < n && byteAt(k) in '0'.code..'9'.code) k++
                            }
                            val param = if (k > j) bytes.copyOfRange(j, k).decodeToString().toIntOrNull() else null
                            if (k < n && byteAt(k) == ' '.code) k++
                            i = k
                            if (word == "bin" && param != null) {
                                i += param.coerceAtLeast(0)
                                continue
                            }
                            if (word in SKIP) {
                                flushBytes()
                                st = st.copy(skip = true)
                                continue
                            }
                            when (word) {
                                "ansicpg" -> if (param != null) codepage = if (param == 65001) "utf-8" else "windows-$param"
                                "par", "sect" -> {
                                    flushBytes()
                                    if (!st.skip) {
                                        if (!sink.hasText) sink.blank() else sink.flush()
                                    }
                                }
                                "line" -> {
                                    flushBytes()
                                    if (!st.skip) sink.lineBreak()
                                }
                                "tab" -> emit("\t")
                                "page" -> {
                                    flushBytes()
                                    if (!st.skip) sink.add(PageBreak)
                                }
                                "emdash" -> emit("—")
                                "endash" -> emit("–")
                                "bullet" -> emit("•")
                                "lquote" -> emit("‘")
                                "rquote" -> emit("’")
                                "ldblquote" -> emit("“")
                                "rdblquote" -> emit("”")
                                "b" -> { flushBytes(); st = st.copy(bold = param != 0) }
                                "i" -> { flushBytes(); st = st.copy(italic = param != 0) }
                                "ul" -> { flushBytes(); st = st.copy(underline = param != 0) }
                                "ulnone" -> { flushBytes(); st = st.copy(underline = false) }
                                "strike" -> { flushBytes(); st = st.copy(strike = param != 0) }
                                "super" -> { flushBytes(); st = st.copy(sup = true, sub = false) }
                                "sub" -> { flushBytes(); st = st.copy(sub = true, sup = false) }
                                "nosupersub" -> { flushBytes(); st = st.copy(sup = false, sub = false) }
                                "plain" -> { flushBytes(); st = st.copy(bold = false, italic = false, underline = false, strike = false, sup = false, sub = false) }
                                "pard" -> sink.align = null
                                "qc" -> sink.align = Align.CENTER
                                "qr" -> sink.align = Align.END
                                "qj" -> sink.align = Align.JUSTIFY
                                "ql" -> sink.align = Align.START
                                "uc" -> st = st.copy(uc = param ?: 1)
                                "u" -> if (param != null) {
                                    val cp = if (param < 0) param + 65536 else param
                                    emit(Markup.codePointToString(cp))
                                    skipChars = st.uc
                                }
                            }
                        }
                        else -> i += 2
                    }
                }
                '\r'.code, '\n'.code -> i++
                else -> {
                    if (skipChars > 0) {
                        skipChars--
                    } else if (!st.skip) {
                        if (c < 0x80) {
                            flushBytes()
                            sink.text(c.toChar().toString(), ink())
                        } else {
                            pending += c.toByte()
                        }
                    }
                    i++
                }
            }
        }
        flushBytes()
        sink.flush()
        return TextDocument(title, null, null, sink.blocks.dropLastWhile { it is Para && it.text.isBlank() }, PageStyle.DOCUMENT)
    }
}

// ------------------------------------------------------------------ Markdown

internal object Markdown {
    private val HEADING = Regex("""^ {0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val RULE = Regex("""^ {0,3}([-*_])(\s*\1){2,}\s*$""")
    private val ITEM = Regex("""^(\s*)([-*+]|\d{1,9}[.)])\s+(.*)$""")
    private val TABLE_SEP = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

    fun read(text: String, title: String): TextDocument {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = ArrayList<Block>()
        val para = StringBuilder()
        var quote = false
        fun flushPara() {
            val t = para.toString().trim()
            para.setLength(0)
            if (t.isNotEmpty()) blocks += Para(inline(t), if (quote) ParaKind.QUOTE else ParaKind.BODY)
            quote = false
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            // Blok kodu.
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flushPara()
                val fence = trimmed.take(3)
                val code = ArrayList<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) code += lines[i++]
                i++
                blocks += Para(listOf(Span(code.joinToString("\n").ifEmpty { " " }, mono = true)), ParaKind.CODE)
                continue
            }
            if (trimmed.isEmpty()) {
                flushPara()
                i++
                continue
            }
            val heading = HEADING.find(line)
            if (heading != null) {
                flushPara()
                blocks += Para(inline(heading.groupValues[2]), ParaKind.HEADING, heading.groupValues[1].length)
                i++
                continue
            }
            // Nagłówek podkreślony („===” / „---”) pod jednym wierszem tekstu.
            if (para.isNotEmpty() && !para.contains('\n') && (trimmed.matches(Regex("=+")) || trimmed.matches(Regex("-{2,}")))) {
                val t = para.toString().trim()
                para.setLength(0)
                blocks += Para(inline(t), ParaKind.HEADING, if (trimmed.startsWith("=")) 1 else 2)
                i++
                continue
            }
            if (RULE.matches(line)) {
                flushPara()
                blocks += Rule
                i++
                continue
            }
            if (trimmed.startsWith(">")) {
                if (!quote) flushPara()
                quote = true
                if (para.isNotEmpty()) para.append(' ')
                para.append(trimmed.removePrefix(">").trimStart('>', ' '))
                i++
                continue
            }
            // Tabela: wiersz z „|” i linia separatora pod nim.
            if (trimmed.contains('|') && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1])) {
                flushPara()
                val rows = ArrayList<List<List<Span>>>()
                fun cells(l: String) = l.trim().removePrefix("|").removeSuffix("|").split('|').map { inline(it.trim()) }
                rows += cells(line)
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) rows += cells(lines[i++])
                blocks += Table(rows, header = true)
                continue
            }
            val item = ITEM.find(line)
            if (item != null) {
                flushPara()
                val indent = item.groupValues[1].replace("\t", "    ").length
                val level = indent / 2 + 1
                val mark = item.groupValues[2]
                var body = item.groupValues[3]
                val task = Regex("""^\[([ xX])]\s+""").find(body)
                val marker = when {
                    task != null -> if (task.groupValues[1] == " ") "☐" else "☑"
                    mark[0].isDigit() -> mark.dropLast(1) + "."
                    else -> HtmlConverter.bullet(level)
                }
                if (task != null) body = body.substring(task.value.length)
                i++
                // Wiersze kontynuacji (wcięte, bez znacznika listy).
                while (i < lines.size && lines[i].isNotBlank() && lines[i].startsWith("  ") && ITEM.find(lines[i]) == null) body += " " + lines[i++].trim()
                blocks += Para(inline(body), listLevel = level, marker = marker)
                continue
            }
            if (Regex("""^!\[[^\]]*]\([^)]*\)\s*$""").matches(trimmed)) {
                i++
                continue
            }
            if (para.isNotEmpty()) para.append(if (line.endsWith("  ")) "\n" else " ")
            para.append(trimmed)
            i++
        }
        flushPara()
        return TextDocument(title, null, null, blocks, PageStyle.BOOK.copy(firstLineIndent = 0f, paragraphSpacing = 0.55f, justify = false, centerHeadings = false))
    }

    /** Wyróżnienia w wierszu: **pogrubienie**, *kursywa*, `kod`, ~~skreślenie~~, [odnośnik](adres). */
    fun inline(src: String): List<Span> {
        val out = ArrayList<Span>()
        var bold = false
        var italic = false
        var strike = false
        val sb = StringBuilder()
        fun push(link: Boolean = false, mono: Boolean = false) {
            if (sb.isEmpty()) return
            out += Span(sb.toString(), bold = bold, italic = italic, strike = strike, link = link, mono = mono)
            sb.setLength(0)
        }
        var i = 0
        val s = src.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && !s[i + 1].isLetterOrDigit() -> {
                    sb.append(s[i + 1])
                    i += 2
                }
                c == '`' -> {
                    val e = s.indexOf('`', i + 1)
                    if (e < 0) {
                        sb.append(c)
                        i++
                    } else {
                        push()
                        sb.append(s, i + 1, e)
                        push(mono = true)
                        i = e + 1
                    }
                }
                s.startsWith("**", i) || s.startsWith("__", i) -> {
                    push()
                    bold = !bold
                    i += 2
                }
                s.startsWith("~~", i) -> {
                    push()
                    strike = !strike
                    i += 2
                }
                (c == '*' || c == '_') && (c == '*' || i == 0 || !s[i - 1].isLetterOrDigit() || (i + 1 < s.length && !s[i + 1].isLetterOrDigit())) &&
                    !(c == '*' && i + 1 < s.length && s[i + 1] == ' ' && !italic) -> {
                    push()
                    italic = !italic
                    i++
                }
                c == '!' && i + 1 < s.length && s[i + 1] == '[' -> {
                    val m = Regex("""^!\[([^\]]*)]\([^)]*\)""").find(s.substring(i))
                    if (m != null) i += m.value.length else {
                        sb.append(c)
                        i++
                    }
                }
                c == '[' -> {
                    val m = Regex("""^\[([^\]]+)]\(([^)]*)\)""").find(s.substring(i))
                    if (m != null) {
                        push()
                        sb.append(m.groupValues[1])
                        push(link = true)
                        i += m.value.length
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                c == '<' -> {
                    // Znaczniki HTML w tekście są pomijane.
                    val e = s.indexOf('>', i)
                    if (e > i && s.substring(i + 1, e).matches(Regex("""/?[a-zA-Z][^<>]*"""))) i = e + 1 else {
                        sb.append(c)
                        i++
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        push()
        return out.map { it.copy(text = Markup.decodeEntities(it.text)) }.ifEmpty { listOf(Span(src)) }
    }
}
