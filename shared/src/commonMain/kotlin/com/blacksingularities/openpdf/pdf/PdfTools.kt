package com.blacksingularities.openpdf.pdf

import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import kotlin.math.max
import kotlin.math.min

/*
 * Wspólna logika narzędzi PDF (podział, przeplatanie, przycinanie, pola formularza, eksport do
 * Worda). Silniki platform wykonują tylko proste operacje na plikach – plan powstaje tutaj, więc
 * Android, iOS i Windows dzielą, przeplatają i przycinają dokument dokładnie tak samo.
 */

/** Nowe pole formularza: strona, prostokąt na wyświetlanej stronie, rodzaj (tekst / pole wyboru) i nazwa. */
data class NewField(val page: Int, val rect: RectN, val kind: FieldKind, val name: String)

/** Domyślny prostokąt nowego pola wokół punktu [at] strony o wymiarach [wPt]×[hPt] punktów. */
fun newFieldRect(kind: FieldKind, at: PointN, wPt: Float, hPt: Float): RectN {
    val (w, h) = if (kind == FieldKind.CHECKBOX) 16f to 16f else min(220f, wPt * 0.6f) to 22f
    val wn = w / wPt
    val hn = h / hPt
    val left = (at.x - if (kind == FieldKind.CHECKBOX) wn / 2 else 0f).coerceIn(0f, max(0f, 1f - wn))
    val top = (at.y - hn / 2).coerceIn(0f, max(0f, 1f - hn))
    return RectN(left, top, left + wn, top + hn)
}

/** Unikalna nazwa nowego pola: „Pole 1”, „Pole wyboru 2”… (bez kropek – kropka oddziela poziomy nazw). */
fun newFieldName(kind: FieldKind, existing: Collection<String>, textBase: String, checkBase: String): String {
    val base = if (kind == FieldKind.CHECKBOX) checkBase else textBase
    var i = 1
    while ("$base $i" in existing) i++
    return "$base $i".replace('.', ' ')
}

// ------------------------------------------------------------------ zakresy stron

/** Zakres stron w postaci „1-3, 5, 8-10, 12-” → posortowane indeksy (od zera) bez powtórzeń. */
fun parsePageRanges(s: String, count: Int): List<Int> {
    val out = HashSet<Int>()
    for (part in s.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }) {
        val ab = part.split('-', '–').map { it.trim().toIntOrNull() }
        val a = ab.getOrNull(0) ?: if (ab.size > 1) 1 else continue
        val b = if (ab.size > 1) ab[1] ?: count else a
        for (i in minOf(a, b)..maxOf(a, b)) if (i in 1..count) out += i - 1
    }
    return out.sorted()
}

/** Zakres stron do podpisu: „1”, „3-7”, „1-2, 5”. */
fun describePages(pages: List<Int>): String {
    if (pages.isEmpty()) return ""
    val sorted = pages.sorted()
    val parts = ArrayList<String>()
    var start = sorted[0]
    var prev = start
    for (p in sorted.drop(1) + Int.MIN_VALUE) {
        if (p == prev + 1) {
            prev = p
            continue
        }
        parts += if (start == prev) "${start + 1}" else "${start + 1}-${prev + 1}"
        start = p
        prev = p
    }
    return parts.joinToString(", ")
}

// ------------------------------------------------------------------ podział

/** Część dokumentu po podziale: strony (indeksy od zera, rosnąco) i opcjonalny tytuł (np. z zakładki). */
data class SplitPart(val pages: List<Int>, val title: String? = null)

/** Sposób podziału dokumentu na osobne pliki. */
enum class SplitMode { RANGES, EVERY, BOOKMARKS, SIZE, TEXT }

object SplitPlan {
    /** Co [n] stron (n = 1 – każda strona osobno). */
    fun every(count: Int, n: Int): List<SplitPart> = (0 until count).chunked(n.coerceAtLeast(1)).map { SplitPart(it) }

    /** Każdy zakres oddzielony przecinkiem to osobny plik: „1-3, 4-10, 11-”. */
    fun ranges(spec: String, count: Int): List<SplitPart> =
        spec.split(',', ';').mapNotNull { part -> parsePageRanges(part, count).takeIf { it.isNotEmpty() }?.let { SplitPart(it) } }

    /** Nowa część zaczyna się na każdej ze stron [starts]; strony przed pierwszą tworzą osobną część. */
    fun byStarts(starts: List<Pair<Int, String?>>, count: Int): List<SplitPart> {
        val s = starts.filter { it.first in 0 until count }.distinctBy { it.first }.sortedBy { it.first }
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<SplitPart>()
        if (s.first().first > 0) out += SplitPart((0 until s.first().first).toList())
        s.forEachIndexed { i, (p, title) ->
            val end = s.getOrNull(i + 1)?.first ?: count
            if (end > p) out += SplitPart((p until end).toList(), title?.trim()?.takeIf { it.isNotEmpty() })
        }
        return out
    }

    /** Według pozycji spisu treści na poziomie [depth] (0 – rozdziały najwyższego poziomu). */
    fun byOutline(outline: List<OutlineEntry>, count: Int, depth: Int): List<SplitPart> =
        byStarts(outline.filter { it.depth == depth && it.page != null }.map { it.page!! to it.title }, count)

    /** Najwyższy poziom spisu treści, na którym jest więcej niż jedna pozycja. */
    fun outlineLevels(outline: List<OutlineEntry>): Int = (outline.maxOfOrNull { it.depth } ?: -1) + 1

    /**
     * Kolejne strony trafiają do części, dopóki suma szacowanych rozmiarów nie przekroczy [maxBytes].
     * Strona większa niż limit zostaje sama w swojej części.
     */
    fun bySize(pageBytes: LongArray, maxBytes: Long): List<SplitPart> {
        val out = ArrayList<SplitPart>()
        var cur = ArrayList<Int>()
        var sum = 0L
        for ((i, b) in pageBytes.withIndex()) {
            if (cur.isNotEmpty() && sum + b > maxBytes) {
                out += SplitPart(cur)
                cur = ArrayList()
                sum = 0L
            }
            cur += i
            sum += b
        }
        if (cur.isNotEmpty()) out += SplitPart(cur)
        return out
    }
}

/** Nazwa pliku części: „Raport – część 2 (s. 5-9).pdf” albo „Raport – 2. Wstęp.pdf”. */
fun splitPartName(base: String, index: Int, total: Int, part: SplitPart, partWord: String, pagesWord: String): String {
    val n = (index + 1).toString().padStart(total.toString().length, '0')
    val title = part.title?.let { safeFileName(it).take(60).trim() }?.takeIf { it.isNotEmpty() }
    val name = if (title != null) "$base – $n. $title" else "$base – $partWord $n ($pagesWord ${describePages(part.pages)})"
    return "$name.pdf"
}

/** Znaki niedozwolone w nazwach plików zastąpione podkreśleniem. */
fun safeFileName(s: String): String = buildString {
    for (c in s) append(if (c in "\\/:*?\"<>|\n\r\t" || c.code < 32) '_' else c)
}.trim().trimEnd('.')

// ------------------------------------------------------------------ przeplatanie

/**
 * Kolejność stron przy przeplataniu plików połączonych jeden za drugim: strona 1 pliku A, strona 1
 * pliku B, strona 2 pliku A… [counts] – liczby stron kolejnych plików, [reverse] – czy brać plik
 * od końca (skan dwustronny: tylne strony zeskanowane w odwrotnej kolejności).
 */
fun mixOrder(counts: List<Int>, reverse: List<Boolean>): List<Int> {
    val offsets = IntArray(counts.size)
    for (i in 1 until counts.size) offsets[i] = offsets[i - 1] + counts[i - 1]
    val longest = counts.maxOrNull() ?: 0
    val out = ArrayList<Int>(counts.sum())
    for (k in 0 until longest) {
        for (f in counts.indices) {
            if (k >= counts[f]) continue
            val idx = if (reverse.getOrElse(f) { false }) counts[f] - 1 - k else k
            out += offsets[f] + idx
        }
    }
    return out
}

// ------------------------------------------------------------------ przycinanie

/** Marginesy (lewy, górny, prawy, dolny – ułamki wyświetlanej strony) → widoczny prostokąt. */
fun cropRect(m: FloatArray): RectN = RectN(m[0], m[1], 1f - m[2], 1f - m[3])

/** Czy marginesy zostawiają sensowną część strony (co najmniej 5% w każdym kierunku). */
fun validCrop(m: FloatArray): Boolean = m.all { it >= 0f } && m[0] + m[2] <= 0.95f && m[1] + m[3] <= 0.95f

/**
 * Marginesy połowy strony przy dzieleniu skanu rozkładówki: [vertical] – cięcie pionową linią
 * (lewa i prawa połowa), inaczej poziomą (górna i dolna). [second] – druga połowa.
 */
fun halfMargins(vertical: Boolean, second: Boolean): FloatArray = when {
    vertical && !second -> floatArrayOf(0f, 0f, 0.5f, 0f)
    vertical -> floatArrayOf(0.5f, 0f, 0f, 0f)
    !second -> floatArrayOf(0f, 0f, 0f, 0.5f)
    else -> floatArrayOf(0f, 0.5f, 0f, 0f)
}

/**
 * Plan dzielenia stron na połowy: strony [pages] są najpierw powielane (kopia tuż za oryginałem),
 * potem przycinane. Zwraca indeksy (po powieleniu) stron z pierwszą i z drugą połową.
 */
fun halvesPlan(pages: Collection<Int>, swap: Boolean): Pair<List<Int>, List<Int>> {
    val sorted = pages.sorted()
    val first = ArrayList<Int>()
    val second = ArrayList<Int>()
    sorted.forEachIndexed { k, p ->
        val orig = p + k
        if (swap) {
            second += orig
            first += orig + 1
        } else {
            first += orig
            second += orig + 1
        }
    }
    return first to second
}

/**
 * Wykrywa jasne marginesy na obrazie strony (piksele ARGB, [w]×[h]). Zwraca lewy, górny, prawy
 * i dolny margines jako ułamki z niewielkim zapasem; strona pusta – same zera.
 */
fun detectMargins(px: IntArray, w: Int, h: Int, threshold: Int = 232): FloatArray {
    if (w <= 0 || h <= 0 || px.size < w * h) return FloatArray(4)
    val rowInk = IntArray(h)
    val colInk = IntArray(w)
    for (y in 0 until h) {
        val o = y * w
        for (x in 0 until w) {
            val c = px[o + x]
            val a = (c ushr 24) and 0xFF
            if (a < 16) continue
            val lum = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            if (lum < threshold) {
                rowInk[y]++
                colInk[x]++
            }
        }
    }
    // Pojedyncze zabrudzenia skanu nie przesuwają granic.
    val minRow = max(1, w / 300)
    val minCol = max(1, h / 300)
    val top = rowInk.indexOfFirst { it >= minRow }
    val bottom = rowInk.indexOfLast { it >= minRow }
    val left = colInk.indexOfFirst { it >= minCol }
    val right = colInk.indexOfLast { it >= minCol }
    if (top < 0 || left < 0 || bottom <= top || right <= left) return FloatArray(4)
    val padX = w * 0.012f
    val padY = h * 0.012f
    return floatArrayOf(
        ((left - padX) / w).coerceIn(0f, 0.45f),
        ((top - padY) / h).coerceIn(0f, 0.45f),
        ((w - 1 - right - padX) / w).coerceIn(0f, 0.45f),
        ((h - 1 - bottom - padY) / h).coerceIn(0f, 0.45f),
    )
}

// ------------------------------------------------------------------ ZIP

/** Pozycja archiwum ZIP: treść w pamięci albo plik na dysku. */
class ZipItem(val name: String, val bytes: ByteArray? = null, val file: Path? = null)

private val CRC_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
    c
}

private fun crc32(crc: Int, b: ByteArray, off: Int, len: Int): Int {
    var c = crc.inv()
    for (i in off until off + len) c = CRC_TABLE[(c xor b[i].toInt()) and 0xFF] xor (c ushr 8)
    return c.inv()
}

/**
 * Zapisuje archiwum ZIP bez kompresji (PDF-y i tak są skompresowane), z nazwami w UTF-8.
 * Pliki są czytane strumieniowo – w pamięci nie leży całe archiwum.
 */
fun writeZip(fs: FileSystem, out: Path, items: List<ZipItem>) {
    data class Central(val name: ByteArray, val crc: Int, val size: Long, val offset: Long)
    val central = ArrayList<Central>()
    var offset = 0L
    fs.sink(out).buffer().use { sink ->
        for (item in items) {
            val name = item.name.encodeToByteArray()
            val buf = ByteArray(64 * 1024)
            var crc = 0
            var size = 0L
            if (item.bytes != null) {
                crc = crc32(0, item.bytes, 0, item.bytes.size)
                size = item.bytes.size.toLong()
            } else if (item.file != null) {
                fs.source(item.file).buffer().use { src ->
                    while (true) {
                        val n = src.read(buf, 0, buf.size)
                        if (n <= 0) break
                        crc = crc32(crc, buf, 0, n)
                        size += n
                    }
                }
            }
            require(size < 0xFFFFFFFFL) { "ZIP > 4 GB" }
            sink.writeIntLe(0x04034b50)
            sink.writeShortLe(20)
            sink.writeShortLe(0x0800)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0x21)
            sink.writeIntLe(crc)
            sink.writeIntLe(size.toInt())
            sink.writeIntLe(size.toInt())
            sink.writeShortLe(name.size)
            sink.writeShortLe(0)
            sink.write(name)
            if (item.bytes != null) {
                sink.write(item.bytes)
            } else if (item.file != null) {
                fs.source(item.file).use { sink.writeAll(it) }
            }
            central += Central(name, crc, size, offset)
            offset += 30 + name.size + size
        }
        val cdStart = offset
        for (c in central) {
            sink.writeIntLe(0x02014b50)
            sink.writeShortLe(20)
            sink.writeShortLe(20)
            sink.writeShortLe(0x0800)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0x21)
            sink.writeIntLe(c.crc)
            sink.writeIntLe(c.size.toInt())
            sink.writeIntLe(c.size.toInt())
            sink.writeShortLe(c.name.size)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeIntLe(0)
            sink.writeIntLe(c.offset.toInt())
            sink.write(c.name)
            offset += 46 + c.name.size
        }
        sink.writeIntLe(0x06054b50)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(central.size)
        sink.writeShortLe(central.size)
        sink.writeIntLe((offset - cdStart).toInt())
        sink.writeIntLe(cdStart.toInt())
        sink.writeShortLe(0)
    }
}

// ------------------------------------------------------------------ eksport do Worda

/**
 * Dokument Word (.docx) z tekstu stron PDF: wiersze łączone w akapity (odstęp, wcięcie, przeniesienia
 * wyrazów), duże wiersze jako nagłówki, każda strona PDF od nowej strony. Układ strony z pierwszej
 * strony dokumentu. Zwraca liczbę akapitów z tekstem (0 – dokument bez warstwy tekstowej).
 */
object DocxWriter {
    private class Line(val text: String, val left: Float, val top: Float, val bottom: Float) {
        val height get() = bottom - top
    }

    private class Para(val text: String, val heading: Int)

    /** Wiersze strony z prostokątami (bez pustych). */
    private fun lines(t: PageText): List<Line> {
        val out = ArrayList<Line>()
        var start = 0
        val s = t.text
        for (i in 0..s.length) {
            if (i < s.length && s[i] != '\n') continue
            var l = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (k in start until i) {
                if (!t.hasBox(k)) continue
                val r = t.box(k)
                l = min(l, r.left)
                top = min(top, r.top)
                b = max(b, r.bottom)
            }
            val text = s.substring(start, i).trim()
            if (text.isNotEmpty()) {
                out += if (l == Float.MAX_VALUE) Line(text, 0f, out.lastOrNull()?.bottom ?: 0f, (out.lastOrNull()?.bottom ?: 0f) + 0.015f) else Line(text, l, top, b)
            }
            start = i + 1
        }
        return out
    }

    private fun paragraphs(t: PageText): List<Para> {
        val ls = lines(t)
        if (ls.isEmpty()) return emptyList()
        val heights = ls.map { it.height }.sorted()
        val median = heights[heights.size / 2].coerceAtLeast(0.004f)
        val minLeft = ls.minOf { it.left }
        val out = ArrayList<Para>()
        val cur = StringBuilder()
        var curHeading = 0
        var prev: Line? = null
        fun flush() {
            val text = cur.toString().trim()
            if (text.isNotEmpty()) out += Para(text, if (text.length <= 160) curHeading else 0)
            cur.clear()
        }
        for (line in ls) {
            val level = when {
                line.height >= median * 1.7f -> 1
                line.height >= median * 1.3f -> 2
                else -> 0
            }
            val p = prev
            val newPara = p == null ||
                level != curHeading ||
                line.top - p.bottom > median * 0.75f ||
                (line.left - minLeft > median * 1.2f && line.left - p.left > median * 0.8f) ||
                p.text.endsWith(':') && line.text.firstOrNull()?.let { it == '•' || it == '-' || it == '–' || it.isDigit() } == true ||
                line.text.firstOrNull() == '•'
            if (newPara) {
                flush()
                curHeading = level
                cur.append(line.text)
            } else {
                val last = cur.lastOrNull()
                if (last == '-' && cur.length > 1 && cur[cur.length - 2].isLetter() && line.text.firstOrNull()?.isLowerCase() == true) {
                    cur.setLength(cur.length - 1)
                    cur.append(line.text)
                } else {
                    cur.append(' ').append(line.text)
                }
            }
            prev = line
        }
        flush()
        return out
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            // Znaki sterujące są niedozwolone w XML 1.0.
            c.code < 32 && c != '\t' -> append(' ')
            c == '￾' || c == '￿' -> Unit
            else -> append(c)
        }
    }

    private fun runs(text: String): String = text.split('\t').joinToString("<w:r><w:tab/></w:r>") { part ->
        if (part.isEmpty()) "" else "<w:r><w:t xml:space=\"preserve\">${esc(part)}</w:t></w:r>"
    }

    /** Zapisuje plik .docx w [out]; [pages] – tekst kolejnych stron (null – strona bez tekstu). */
    fun write(fs: FileSystem, out: Path, pages: List<PageText?>, pageWidthPt: Float, pageHeightPt: Float, title: String): Int {
        val body = StringBuilder()
        var count = 0
        pages.forEachIndexed { i, t ->
            if (i > 0) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            val paras = if (t == null) emptyList() else paragraphs(t)
            if (paras.isEmpty()) body.append("<w:p/>")
            for (p in paras) {
                count++
                body.append("<w:p>")
                if (p.heading > 0) body.append("<w:pPr><w:pStyle w:val=\"Heading${p.heading}\"/></w:pPr>")
                body.append(runs(p.text))
                body.append("</w:p>")
            }
        }
        val landscape = pageWidthPt > pageHeightPt
        val tw = (pageWidthPt * 20).toInt().coerceIn(2000, 31680)
        val th = (pageHeightPt * 20).toInt().coerceIn(2000, 31680)
        val sect = "<w:sectPr><w:pgSz w:w=\"$tw\" w:h=\"$th\"${if (landscape) " w:orient=\"landscape\"" else ""}/>" +
            "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"567\" w:footer=\"567\" w:gutter=\"0\"/></w:sectPr>"
        val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
        val document = head + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body + sect + "</w:body></w:document>"
        val styles = head + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\" w:eastAsia=\"Calibri\"/><w:sz w:val=\"22\"/><w:lang w:val=\"pl-PL\"/></w:rPr></w:rPrDefault>" +
            "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" +
            "<w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr></w:style>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" +
            "<w:pPr><w:keepNext/><w:spacing w:before=\"200\" w:after=\"80\"/><w:outlineLvl w:val=\"1\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"26\"/></w:rPr></w:style>" +
            "</w:styles>"
        val contentTypes = head + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
            "</Types>"
        val rels = head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
            "</Relationships>"
        val docRels = head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"
        val core = head + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>${esc(title)}</dc:title><dc:creator>OpenDocument</dc:creator></cp:coreProperties>"
        writeZip(
            fs,
            out,
            listOf(
                ZipItem("[Content_Types].xml", contentTypes.encodeToByteArray()),
                ZipItem("_rels/.rels", rels.encodeToByteArray()),
                ZipItem("word/document.xml", document.encodeToByteArray()),
                ZipItem("word/styles.xml", styles.encodeToByteArray()),
                ZipItem("word/_rels/document.xml.rels", docRels.encodeToByteArray()),
                ZipItem("docProps/core.xml", core.encodeToByteArray()),
            ),
        )
        return count
    }
}
