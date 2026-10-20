package com.blacksingularities.openpdf.doc

import com.blacksingularities.openpdf.i18n.tr
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/** Rodzaj otwieranego pliku. */
enum class DocFormat {
    PDF, IMAGE, DJVU, EPUB, FB2, DOCX, ODT, RTF, HTML, MARKDOWN, TEXT, CSV, PLAIN;

    /** Czy plik trzeba przed otwarciem złożyć w strony PDF. */
    val converted: Boolean get() = this != PDF
}

/** Plik w formacie, którego aplikacja nie umie odczytać. */
class UnsupportedFormatException(message: String) : Exception(message)

/** Rozpoznawanie formatu i odczyt dokumentów tekstowych do [TextDocument]. */
object DocumentReader {
    /** Rozszerzenia „technicznych” plików tekstowych – pokazywane pismem o stałej szerokości. */
    private val CODE = setOf(
        "log", "json", "xml", "yml", "yaml", "ini", "cfg", "conf", "toml", "properties", "kt", "kts", "java", "js", "ts",
        "py", "c", "h", "cpp", "hpp", "cs", "go", "rs", "swift", "sh", "bat", "ps1", "sql", "gradle", "css", "srt", "tex",
    )

    fun detect(name: String, head: ByteArray, fs: FileSystem, file: Path): DocFormat {
        val ext = name.substringAfterLast('.', "").lowercase()
        fun at(i: Int) = head.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        val start = head.copyOfRange(0, minOf(head.size, 512)).decodeToString().trimStart('﻿', ' ', '\n', '\r', '\t')
        when {
            at(0) == '%'.code && at(1) == 'P'.code && at(2) == 'D'.code && at(3) == 'F'.code -> return DocFormat.PDF
            imageMime(head) != null -> return DocFormat.IMAGE
            at(0) == 'A'.code && at(1) == 'T'.code && at(2) == '&'.code && at(3) == 'T'.code && at(4) == 'F'.code -> return DocFormat.DJVU
            at(0) == 0xD0 && at(1) == 0xCF && at(2) == 0x11 && at(3) == 0xE0 ->
                throw UnsupportedFormatException(tr("Stary format Worda (.doc) nie jest obsługiwany – zapisz plik jako DOCX"))
            head.size > 68 && head.copyOfRange(60, 68).decodeToString() == "BOOKMOBI" ->
                throw UnsupportedFormatException(tr("Pliki MOBI/AZW nie są obsługiwane – przekonwertuj książkę do EPUB"))
            at(0) == 'P'.code && at(1) == 'K'.code -> return zipFormat(fs, file, ext)
            start.startsWith("{\\rtf") -> return DocFormat.RTF
        }
        if (start.startsWith("%PDF")) return DocFormat.PDF
        val lower = start.lowercase()
        return when {
            ext == "fb2" || "<fictionbook" in lower -> DocFormat.FB2
            ext in setOf("html", "htm", "xhtml", "xht") || lower.startsWith("<!doctype html") || lower.startsWith("<html") -> DocFormat.HTML
            ext in setOf("md", "markdown", "mdown", "mkd") -> DocFormat.MARKDOWN
            ext == "csv" || ext == "tsv" -> DocFormat.CSV
            ext in CODE -> DocFormat.PLAIN
            ext == "pdf" -> DocFormat.PDF
            looksBinary(head) -> throw UnsupportedFormatException(tr("Nieobsługiwany format pliku"))
            else -> DocFormat.TEXT
        }
    }

    private fun looksBinary(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        // UTF-16 ma zera co drugi bajt – to wciąż tekst.
        if (head.size >= 2 && ((head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) || (head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()))) return false
        var zeros = 0
        var control = 0
        for (b in head) {
            val v = b.toInt() and 0xFF
            if (v == 0) zeros++ else if (v < 9 || (v in 14..31)) control++
        }
        return zeros > head.size / 3 || (zeros == 0 && control > head.size / 20) || (zeros > 0 && !(head.size > 3 && head[1] == 0.toByte()))
    }

    private fun zipFormat(fs: FileSystem, file: Path, ext: String): DocFormat {
        val zip = runCatching { fs.openZip(file) }.getOrNull() ?: throw UnsupportedFormatException(tr("Uszkodzone archiwum ZIP"))
        val mime = runCatching { zip.read("/mimetype".toPath()) { readUtf8() }.trim() }.getOrNull()
        return when {
            mime == "application/epub+zip" -> DocFormat.EPUB
            mime == "application/vnd.oasis.opendocument.text" -> DocFormat.ODT
            mime?.startsWith("application/vnd.oasis.opendocument") == true ->
                throw UnsupportedFormatException(tr("Obsługiwane są tylko dokumenty tekstowe OpenDocument (ODT)"))
            zip.exists("/word/document.xml".toPath()) -> DocFormat.DOCX
            zip.exists("/META-INF/container.xml".toPath()) -> DocFormat.EPUB
            zip.exists("/content.xml".toPath()) -> DocFormat.ODT
            zip.exists("/xl/workbook.xml".toPath()) || zip.exists("/ppt/presentation.xml".toPath()) ->
                throw UnsupportedFormatException(tr("Arkusze i prezentacje nie są obsługiwane – tylko dokumenty tekstowe"))
            ext == "epub" -> DocFormat.EPUB
            fb2InZip(zip) != null -> DocFormat.FB2
            else -> throw UnsupportedFormatException(tr("Nieobsługiwany format pliku"))
        }
    }

    private fun fb2InZip(zip: FileSystem): Path? =
        runCatching { zip.list("/".toPath()).firstOrNull { it.name.endsWith(".fb2", ignoreCase = true) } }.getOrNull()

    /** Odczyt pliku [file] (nazwa pliku [name] – do rozpoznania formatu i tytułu). */
    fun read(fs: FileSystem, file: Path, format: DocFormat, name: String): TextDocument {
        val base = name.substringBeforeLast('.')
        return when (format) {
            DocFormat.EPUB -> Epub.read(fs.openZip(file))
            DocFormat.DOCX -> Docx.read(fs.openZip(file))
            DocFormat.ODT -> Odt.read(fs.openZip(file))
            DocFormat.FB2 -> {
                val bytes = if (isZip(fs, file)) {
                    val zip = fs.openZip(file)
                    val inner = fb2InZip(zip) ?: throw UnsupportedFormatException(tr("Nieobsługiwany format pliku"))
                    zip.read(inner) { readByteArray() }
                } else {
                    fs.read(file) { readByteArray() }
                }
                fb2(bytes)
            }
            DocFormat.HTML -> {
                val bytes = fs.read(file) { readByteArray() }
                val dir = file.parent
                html(decodeText(bytes, declaredCharset(bytes)), base) { src ->
                    if (src.startsWith("data:")) dataUri(src) else dir?.let { d -> runCatching { fs.read(d / src.substringBefore('?')) { readByteArray() } }.getOrNull() }
                }
            }
            DocFormat.MARKDOWN -> Markdown.read(decodeText(fs.read(file) { readByteArray() }), base)
            DocFormat.RTF -> Rtf.read(fs.read(file) { readByteArray() }, base)
            DocFormat.CSV -> csv(decodeText(fs.read(file) { readByteArray() }), base, tab = name.endsWith(".tsv", true))
            DocFormat.PLAIN -> plain(decodeText(fs.read(file) { readByteArray() }), base)
            DocFormat.TEXT -> text(decodeText(fs.read(file) { readByteArray() }), base)
            DocFormat.PDF, DocFormat.IMAGE, DocFormat.DJVU -> error("Plik nie wymaga składania")
        }
    }

    private fun isZip(fs: FileSystem, file: Path): Boolean = runCatching {
        fs.read(file) { val b = readByteArray(2); b.size == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
    }.getOrDefault(false)

    internal fun dataUri(src: String): ByteArray? {
        val comma = src.indexOf(',')
        if (comma < 0 || !src.substring(0, comma).contains("base64")) return null
        return base64Decode(src.substring(comma + 1))
    }

    // ------------------------------------------------------------------ HTML i FictionBook

    fun html(text: String, fallbackTitle: String, image: (String) -> ByteArray?): TextDocument {
        val root = Markup.parse(text, html = true)
        val title = root.find("title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val lang = root.find("html")?.attr("lang")
        val c = HtmlConverter(image)
        c.convert(root.find("body") ?: root)
        return TextDocument(title ?: fallbackTitle, null, lang, c.sink.blocks, PageStyle.BOOK)
    }

    fun fb2(bytes: ByteArray): TextDocument {
        val root = Markup.parse(decodeText(bytes, declaredCharset(bytes)), html = false)
        val binaries = HashMap<String, XElement>()
        for (b in root.findAll("binary")) b.attr("id")?.let { binaries[it] = b }
        val info = root.find("title-info")
        val title = info?.child("book-title")?.text()?.trim()
        val author = info?.child("author")?.let { a ->
            listOfNotNull(a.child("first-name")?.text(), a.child("middle-name")?.text(), a.child("last-name")?.text())
                .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { a.child("nickname")?.text()?.trim() }
        }
        val lang = info?.child("lang")?.text()?.trim()
        val c = HtmlConverter({ href -> binaries[href.removePrefix("#")]?.let { base64Decode(it.text()) } }, fb2 = true)
        // Okładka z opisu książki.
        info?.child("coverpage")?.find("image")?.let { img ->
            img.attr("href")?.let { h -> binaries[h.removePrefix("#")]?.let { b -> c.sink.add(Picture(base64Decode(b.text()))) } }
            c.sink.add(PageBreak)
        }
        for (body in root.findAll("body")) {
            if (c.sink.blocks.isNotEmpty()) c.sink.add(PageBreak)
            c.convert(body)
        }
        return TextDocument(title, author, lang, c.sink.blocks, PageStyle.BOOK)
    }

    // ------------------------------------------------------------------ zwykły tekst

    /**
     * Plik tekstowy: akapity rozdzielone pustymi wierszami (książki z Projektu Gutenberg i podobne
     * mają zawinięte wiersze – są łączone), w innym razie każdy wiersz to akapit.
     */
    fun text(text: String, title: String): TextDocument {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val nonEmpty = lines.filter { it.isNotBlank() }
        val blankCount = lines.size - nonEmpty.size
        val avg = if (nonEmpty.isEmpty()) 0 else nonEmpty.sumOf { it.length } / nonEmpty.size
        val wrapped = blankCount > nonEmpty.size / 12 && avg in 30..95 && nonEmpty.count { it.length > avg + 20 } < nonEmpty.size / 8
        val blocks = ArrayList<Block>()
        if (wrapped) {
            val sb = StringBuilder()
            fun flush() {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) blocks += textPara(t)
                sb.setLength(0)
            }
            for (l in lines) {
                if (l.isBlank()) flush() else {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(l.trim())
                }
            }
            flush()
        } else {
            for (l in lines) {
                if (l.isBlank()) continue
                blocks += textPara(l.trimEnd())
            }
        }
        return TextDocument(title, null, null, blocks, PageStyle.BOOK)
    }

    /** Krótki wiersz pisany wielkimi literami („ROZDZIAŁ I”) to nagłówek. */
    private fun textPara(t: String): Para {
        val letters = t.filter { it.isLetter() }
        val heading = t.length <= 60 && letters.length >= 3 && letters.all { it.isUpperCase() || !it.isLetter() } && letters.any { it.isUpperCase() }
        return if (heading) Para(listOf(Span(t.trim())), ParaKind.HEADING, 2) else Para(listOf(Span(t.trim())))
    }

    /** Kod, logi, JSON – wiersz po wierszu, pismem o stałej szerokości. */
    fun plain(text: String, title: String): TextDocument {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ").split('\n')
        val blocks = lines.map { l -> Para(listOf(Span(l.ifEmpty { " " }, mono = true)), ParaKind.CODE) }
        return TextDocument(title, null, null, blocks, PageStyle.PLAIN)
    }

    fun csv(text: String, title: String, tab: Boolean): TextDocument {
        val sep = if (tab) '\t' else {
            val first = text.lineSequence().firstOrNull().orEmpty()
            listOf(',', ';', '\t').maxByOrNull { c -> first.count { it == c } } ?: ','
        }
        val rows = ArrayList<List<List<Span>>>()
        var cell = StringBuilder()
        var row = ArrayList<List<Span>>()
        var quoted = false
        var i = 0
        fun endCell() {
            row += listOf(Span(cell.toString().trim()))
            cell = StringBuilder()
        }
        fun endRow() {
            endCell()
            if (row.any { it.first().text.isNotEmpty() }) rows += row
            row = ArrayList()
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"' && i + 1 < text.length && text[i + 1] == '"') {
                    cell.append('"')
                    i++
                } else if (c == '"') quoted = false else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                sep -> endCell()
                '\n' -> endRow()
                '\r' -> Unit
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        // Bardzo szerokie tabele są dzielone na kilka węższych (po 6 kolumn).
        val blocks = ArrayList<Block>()
        val cols = rows.maxOfOrNull { it.size } ?: 0
        val chunk = 6
        var from = 0
        while (from < cols) {
            val to = minOf(cols, from + chunk)
            blocks += Table(rows.map { r -> (from until to).map { r.getOrNull(it) ?: listOf(Span("")) } }, header = true)
            from = to
        }
        return TextDocument(title, null, null, blocks, PageStyle.DOCUMENT)
    }
}

// ------------------------------------------------------------------ EPUB

internal object Epub {
    fun read(zip: FileSystem): TextDocument {
        val container = Markup.parse(zip.read("/META-INF/container.xml".toPath()) { readUtf8() }, html = false)
        val opfPath = container.find("rootfile")?.attr("full-path") ?: error(tr("Brak opisu książki w pliku EPUB"))
        val opfFile = ("/" + opfPath.trimStart('/')).toPath()
        val opf = Markup.parse(readText(zip, opfFile), html = false)
        val baseDir = opfFile.parent ?: "/".toPath()
        val meta = opf.find("metadata")
        val title = meta?.find("title")?.text()?.trim()
        val author = meta?.find("creator")?.text()?.trim()
        val lang = meta?.find("language")?.text()?.trim()
        val manifest = HashMap<String, Pair<String, String>>()
        opf.find("manifest")?.all("item")?.forEach { item ->
            val id = item.attr("id") ?: return@forEach
            manifest[id] = (item.attr("href") ?: "") to (item.attr("media-type") ?: "")
        }
        val spine = opf.find("spine")?.all("itemref")?.mapNotNull { ref ->
            if (ref.attr("linear") == "no") null else manifest[ref.attr("idref") ?: ""]
        }.orEmpty()
        val blocks = ArrayList<Block>()
        var first = true
        for ((href, type) in spine) {
            if (!(type.contains("html") || type.contains("xml") || href.endsWith("htm", true) || href.endsWith("html", true))) continue
            val path = resolve(baseDir, href) ?: continue
            val text = runCatching { readText(zip, path) }.getOrNull() ?: continue
            val dir = path.parent ?: "/".toPath()
            val root = Markup.parse(text, html = true)
            val c = HtmlConverter({ src ->
                if (src.startsWith("data:")) DocumentReader.dataUri(src) else resolve(dir, src)?.let { p -> runCatching { zip.read(p) { readByteArray() } }.getOrNull() }
            })
            c.convert(root.find("body") ?: root)
            if (c.sink.blocks.isEmpty()) continue
            // Każdy rozdział od nowej strony, jak w drukowanej książce.
            if (!first) blocks += PageBreak
            first = false
            blocks += c.sink.blocks
        }
        if (blocks.isEmpty()) error(tr("Książka nie zawiera tekstu"))
        return TextDocument(title, author, lang, blocks, PageStyle.BOOK)
    }

    private fun readText(zip: FileSystem, p: Path): String {
        val bytes = zip.read(p) { readByteArray() }
        return decodeText(bytes, declaredCharset(bytes))
    }

    /** Ścieżka względna (z kodowaniem %20, „../”) wewnątrz archiwum. */
    fun resolve(dir: Path, href: String): Path? {
        val clean = percentDecode(href.substringBefore('#').substringBefore('?'))
        if (clean.isEmpty() || clean.contains("://")) return null
        val parts = ArrayList<String>()
        if (!clean.startsWith("/")) parts += dir.toString().split('/').filter { it.isNotEmpty() }
        for (seg in clean.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += seg
            }
        }
        return ("/" + parts.joinToString("/")).toPath()
    }

    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    out += v.toByte()
                    i += 3
                    continue
                }
            }
            out += c.toString().encodeToByteArray().toList()
            i++
        }
        return out.toByteArray().decodeToString()
    }
}
