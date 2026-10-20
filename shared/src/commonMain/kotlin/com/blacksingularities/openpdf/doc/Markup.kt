package com.blacksingularities.openpdf.doc

/** Węzeł drzewa XML/HTML. */
sealed class XNode

class XText(val text: String) : XNode()

/** Element; [name] – małymi literami, z przedrostkiem przestrzeni nazw (np. „w:p”). */
class XElement(val name: String, val attrs: Map<String, String>) : XNode() {
    val children = ArrayList<XNode>()

    /** Nazwa bez przedrostka („w:p” → „p”). */
    val local: String get() = name.substringAfter(':')

    /** Atrybut o nazwie lokalnej [localName] (wielkość liter bez znaczenia – nazwy są zapisane małymi literami). */
    fun attr(localName: String): String? {
        val q = localName.lowercase()
        return attrs[q] ?: attrs.entries.firstOrNull { it.key.substringAfter(':') == q }?.value
    }

    fun elements(): List<XElement> = children.filterIsInstance<XElement>()

    fun child(localName: String): XElement? {
        val q = localName.lowercase()
        return children.firstOrNull { it is XElement && it.local == q } as XElement?
    }

    fun all(localName: String): List<XElement> {
        val q = localName.lowercase()
        return children.filter { it is XElement && it.local == q }.map { it as XElement }
    }

    /** Pierwszy potomek (w głąb) o nazwie [localName]. */
    fun find(localName: String): XElement? = findLower(localName.lowercase())

    private fun findLower(q: String): XElement? {
        for (c in children) if (c is XElement) {
            if (c.local == q) return c
            c.findLower(q)?.let { return it }
        }
        return null
    }

    fun findAll(localName: String): List<XElement> {
        val out = ArrayList<XElement>()
        collect(localName.lowercase(), out)
        return out
    }

    private fun collect(q: String, out: MutableList<XElement>) {
        for (c in children) if (c is XElement) {
            if (c.local == q) out += c
            c.collect(q, out)
        }
    }

    /** Cały tekst w elemencie. */
    fun text(): String {
        val sb = StringBuilder()
        fun walk(n: XNode) {
            when (n) {
                is XText -> sb.append(n.text)
                is XElement -> n.children.forEach { walk(it) }
            }
        }
        walk(this)
        return sb.toString()
    }
}

/**
 * Wyrozumiały parser XML i HTML: znaczniki bez zamknięcia, elementy puste HTML, encje nazwane
 * i liczbowe. Zwraca sztuczny korzeń „#root” z całą zawartością.
 */
object Markup {
    private val VOID = setOf("br", "img", "hr", "meta", "link", "input", "col", "area", "base", "wbr", "source", "param", "embed", "track")
    private val RAW = setOf("script", "style")
    /** Otwarcie któregoś z tych elementów zamyka niedomknięty akapit HTML. */
    private val CLOSES_P = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "blockquote", "pre", "section", "hr", "dl", "figure")

    fun parse(src: String, html: Boolean): XElement {
        val root = XElement("#root", emptyMap())
        val stack = ArrayList<XElement>()
        stack += root
        val n = src.length
        var i = 0
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                stack.last().children += XText(decodeEntities(text.toString()))
                text.setLength(0)
            }
        }
        fun close(name: String) {
            val idx = stack.indexOfLast { it.name == name }
            if (idx > 0) while (stack.size > idx) stack.removeAt(stack.size - 1)
        }
        while (i < n) {
            val c = src[i]
            if (c != '<') {
                text.append(c)
                i++
                continue
            }
            when {
                src.startsWith("<!--", i) -> {
                    flushText()
                    val e = src.indexOf("-->", i + 4)
                    i = if (e < 0) n else e + 3
                }
                src.startsWith("<![CDATA[", i) -> {
                    flushText()
                    val e = src.indexOf("]]>", i + 9)
                    val end = if (e < 0) n else e
                    stack.last().children += XText(src.substring(i + 9, end))
                    i = if (e < 0) n else e + 3
                }
                src.startsWith("<!", i) || src.startsWith("<?", i) -> {
                    flushText()
                    val e = src.indexOf('>', i + 2)
                    i = if (e < 0) n else e + 1
                }
                i + 1 < n && src[i + 1] == '/' -> {
                    flushText()
                    val e = src.indexOf('>', i + 2)
                    val name = src.substring(i + 2, if (e < 0) n else e).trim().lowercase()
                    close(name)
                    i = if (e < 0) n else e + 1
                }
                i + 1 < n && (src[i + 1].isLetter() || src[i + 1] == '_') -> {
                    flushText()
                    val end = tagEnd(src, i + 1)
                    val body = src.substring(i + 1, end)
                    i = if (end < n) end + 1 else n
                    val selfClosing = body.endsWith("/")
                    val inner = if (selfClosing) body.dropLast(1) else body
                    var k = 0
                    while (k < inner.length && !inner[k].isWhitespace()) k++
                    val name = inner.substring(0, k).lowercase()
                    val el = XElement(name, parseAttrs(inner.substring(k)))
                    if (html) {
                        if (name in CLOSES_P && stack.any { it.name == "p" }) close("p")
                        if (name == "li") closeUntil(stack, "li", setOf("ul", "ol"))
                        if (name == "td" || name == "th") closeUntil(stack, name, setOf("tr", "table"))
                        if (name == "tr") closeUntil(stack, "tr", setOf("table", "tbody", "thead"))
                    }
                    stack.last().children += el
                    if (html && name in RAW) {
                        val close = src.indexOf("</$name", i, ignoreCase = true)
                        val stop = if (close < 0) n else close
                        el.children += XText(src.substring(i, stop))
                        val gt = if (close < 0) -1 else src.indexOf('>', close)
                        i = if (gt < 0) n else gt + 1
                    } else if (!selfClosing && !(html && name in VOID)) {
                        stack += el
                    }
                }
                else -> {
                    text.append(c)
                    i++
                }
            }
        }
        flushText()
        return root
    }

    private fun closeUntil(stack: ArrayList<XElement>, name: String, boundary: Set<String>) {
        for (j in stack.indices.reversed()) {
            val e = stack[j]
            if (e.name in boundary) return
            if (e.name == name) {
                while (stack.size > j) stack.removeAt(stack.size - 1)
                return
            }
        }
    }

    /** Koniec znacznika – pierwszy „>” poza cudzysłowami. */
    private fun tagEnd(s: String, from: Int): Int {
        var q = 0.toChar()
        var i = from
        while (i < s.length) {
            val c = s[i]
            if (q != 0.toChar()) {
                if (c == q) q = 0.toChar()
            } else if (c == '"' || c == '\'') {
                q = c
            } else if (c == '>') {
                return i
            }
            i++
        }
        return s.length
    }

    private fun parseAttrs(s: String): Map<String, String> {
        if (s.isBlank()) return emptyMap()
        val out = HashMap<String, String>()
        var i = 0
        val n = s.length
        while (i < n) {
            while (i < n && (s[i].isWhitespace() || s[i] == '/')) i++
            val start = i
            while (i < n && !s[i].isWhitespace() && s[i] != '=' && s[i] != '/') i++
            if (i == start) {
                i++
                continue
            }
            val name = s.substring(start, i).lowercase()
            while (i < n && s[i].isWhitespace()) i++
            var value = ""
            if (i < n && s[i] == '=') {
                i++
                while (i < n && s[i].isWhitespace()) i++
                if (i < n && (s[i] == '"' || s[i] == '\'')) {
                    val q = s[i]
                    val e = s.indexOf(q, i + 1)
                    val end = if (e < 0) n else e
                    value = s.substring(i + 1, end)
                    i = end + 1
                } else {
                    val vs = i
                    while (i < n && !s[i].isWhitespace()) i++
                    value = s.substring(vs, i)
                }
            }
            out[name] = decodeEntities(value)
        }
        return out
    }

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "laquo" to "«", "raquo" to "»",
        "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“", "rdquo" to "”", "bdquo" to "„",
        "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°", "middot" to "·", "bull" to "•",
        "times" to "×", "divide" to "÷", "plusmn" to "±", "sect" to "§", "para" to "¶", "shy" to "­",
        "euro" to "€", "pound" to "£", "cent" to "¢", "yen" to "¥", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
        "minus" to "−", "prime" to "′", "Prime" to "″", "thinsp" to " ", "ensp" to " ", "emsp" to " ",
        "zwnj" to "‌", "zwj" to "‍", "iexcl" to "¡", "iquest" to "¿", "dagger" to "†", "Dagger" to "‡",
        "larr" to "←", "rarr" to "→", "uarr" to "↑", "darr" to "↓", "hearts" to "♥", "star" to "☆",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë", "aacute" to "á", "agrave" to "à",
        "acirc" to "â", "auml" to "ä", "atilde" to "ã", "aring" to "å", "oacute" to "ó", "ograve" to "ò",
        "ocirc" to "ô", "ouml" to "ö", "otilde" to "õ", "oslash" to "ø", "uacute" to "ú", "ugrave" to "ù",
        "ucirc" to "û", "uuml" to "ü", "iacute" to "í", "igrave" to "ì", "icirc" to "î", "iuml" to "ï",
        "ccedil" to "ç", "ntilde" to "ñ", "szlig" to "ß", "aelig" to "æ", "oelig" to "œ", "yacute" to "ý",
        "Eacute" to "É", "Aacute" to "Á", "Oacute" to "Ó", "Uacute" to "Ú", "Iacute" to "Í", "Ccedil" to "Ç",
        "Ntilde" to "Ñ", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "AElig" to "Æ", "Oslash" to "Ø", "Aring" to "Å",
    )

    fun decodeEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i + 1)
                if (semi > i + 1 && semi - i <= 12) {
                    val name = s.substring(i + 1, semi)
                    val decoded = if (name.startsWith("#")) {
                        val code = if (name.length > 1 && (name[1] == 'x' || name[1] == 'X')) name.substring(2).toIntOrNull(16) else name.substring(1).toIntOrNull()
                        code?.takeIf { it in 1..0x10FFFF }?.let { codePointToString(it) }
                    } else {
                        NAMED[name]
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    fun codePointToString(cp: Int): String = if (cp < 0x10000) {
        cp.toChar().toString()
    } else {
        val v = cp - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }
}

// ------------------------------------------------------------------ kodowanie znaków i Base64

/** Tekst w zadanym kodowaniu (np. „windows-1250”); null – kodowanie nieznane platformie. */
expect fun decodeCharset(bytes: ByteArray, charset: String): String?

/** Czy bajty są poprawnym UTF-8. */
internal fun isValidUtf8(b: ByteArray, from: Int = 0): Boolean {
    var i = from
    val n = b.size
    while (i < n) {
        val c = b[i].toInt() and 0xFF
        val extra = when {
            c < 0x80 -> 0
            c in 0xC2..0xDF -> 1
            c in 0xE0..0xEF -> 2
            c in 0xF0..0xF4 -> 3
            else -> return false
        }
        // Obcięty ostatni znak (np. fragment pliku) nie przekreśla całości.
        if (i + extra >= n) return true
        for (k in 1..extra) if ((b[i + k].toInt() and 0xC0) != 0x80) return false
        i += extra + 1
    }
    return true
}

/**
 * Tekst z bajtów: BOM, deklaracja w pliku ([declared]), poprawny UTF-8, a na końcu zgadywanie
 * między Windows-1250 (polski) i Windows-1251 (cyrylica).
 */
fun decodeText(bytes: ByteArray, declared: String? = null): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return bytes.decodeToString(3, bytes.size)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return utf16(bytes, 2, littleEndian = true)
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return utf16(bytes, 2, littleEndian = false)
    val d = declared?.trim()?.lowercase()
    if (d != null && d != "utf-8" && d != "utf8") decodeCharset(bytes, d)?.let { return it }
    if (isValidUtf8(bytes)) return bytes.decodeToString()
    // UTF-16 bez BOM: co drugi bajt zerowy.
    if (bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()) return utf16(bytes, 0, littleEndian = true)
    var high = 0
    var cyr = 0
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        if (v >= 0x80) high++
        if (v >= 0xC0) cyr++
    }
    val guess = if (high > 0 && cyr * 10 > high * 9 && high * 5 > bytes.size) "windows-1251" else "windows-1250"
    return decodeCharset(bytes, guess) ?: bytes.decodeToString()
}

private fun utf16(b: ByteArray, from: Int, littleEndian: Boolean): String {
    val sb = StringBuilder((b.size - from) / 2)
    var i = from
    while (i + 1 < b.size) {
        val lo = b[i].toInt() and 0xFF
        val hi = b[i + 1].toInt() and 0xFF
        sb.append((if (littleEndian) (hi shl 8) or lo else (lo shl 8) or hi).toChar())
        i += 2
    }
    return sb.toString()
}

/** Kodowanie zadeklarowane w nagłówku XML albo w znaczniku meta HTML. */
internal fun declaredCharset(bytes: ByteArray): String? {
    val head = bytes.copyOfRange(0, minOf(bytes.size, 2048)).decodeToString()
    val m = Regex("""(?:encoding|charset)\s*=\s*["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE).find(head)
    return m?.groupValues?.get(1)
}

private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** Dekodowanie Base64 z pominięciem białych znaków i znaków spoza alfabetu. */
fun base64Decode(s: String): ByteArray {
    val lookup = IntArray(128) { -1 }
    for ((i, c) in B64.withIndex()) lookup[c.code] = i
    lookup['-'.code] = 62
    lookup['_'.code] = 63
    val out = ArrayList<Byte>(s.length * 3 / 4)
    var buf = 0
    var bits = 0
    for (c in s) {
        if (c == '=') break
        val v = if (c.code < 128) lookup[c.code] else -1
        if (v < 0) continue
        buf = (buf shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out += ((buf shr bits) and 0xFF).toByte()
        }
    }
    return out.toByteArray()
}

fun base64Encode(b: ByteArray): String {
    val sb = StringBuilder((b.size + 2) / 3 * 4)
    var i = 0
    while (i < b.size) {
        val b0 = b[i].toInt() and 0xFF
        val b1 = if (i + 1 < b.size) b[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < b.size) b[i + 2].toInt() and 0xFF else 0
        sb.append(B64[b0 shr 2])
        sb.append(B64[((b0 and 3) shl 4) or (b1 shr 4)])
        sb.append(if (i + 1 < b.size) B64[((b1 and 15) shl 2) or (b2 shr 6)] else '=')
        sb.append(if (i + 2 < b.size) B64[b2 and 63] else '=')
        i += 3
    }
    return sb.toString()
}

/** Rodzaj obrazu po pierwszych bajtach; null – format, którego nie wyświetlimy. */
fun imageMime(b: ByteArray): String? {
    if (b.size < 12) return null
    fun at(i: Int) = b[i].toInt() and 0xFF
    return when {
        at(0) == 0xFF && at(1) == 0xD8 -> "image/jpeg"
        at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png"
        at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code -> "image/gif"
        at(0) == 'R'.code && at(1) == 'I'.code && at(8) == 'W'.code && at(9) == 'E'.code -> "image/webp"
        at(0) == 'B'.code && at(1) == 'M'.code -> "image/bmp"
        else -> null
    }
}
