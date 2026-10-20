package com.blacksingularities.openpdf

import com.blacksingularities.openpdf.doc.DocFormat
import com.blacksingularities.openpdf.doc.DocHtml
import com.blacksingularities.openpdf.doc.DocumentReader
import com.blacksingularities.openpdf.doc.PageBreak
import com.blacksingularities.openpdf.doc.Para
import com.blacksingularities.openpdf.doc.ParaKind
import com.blacksingularities.openpdf.doc.Picture
import com.blacksingularities.openpdf.doc.Table
import com.blacksingularities.openpdf.doc.TextDocument
import com.blacksingularities.openpdf.doc.base64Decode
import com.blacksingularities.openpdf.doc.base64Encode
import com.blacksingularities.openpdf.doc.decodeText
import com.blacksingularities.openpdf.ui.baseName
import com.blacksingularities.openpdf.ui.pdfFileName
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocReaderTest {
    private val fs = FileSystem.SYSTEM

    /** Najmniejszy poprawny PNG 1×1. */
    private val png = base64Decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")

    private fun zip(name: String, entries: Map<String, ByteArray>): Path {
        val f = File.createTempFile("doc", name)
        f.deleteOnExit()
        ZipOutputStream(f.outputStream()).use { z ->
            for ((k, v) in entries) {
                z.putNextEntry(ZipEntry(k))
                z.write(v)
                z.closeEntry()
            }
        }
        return f.toOkioPath()
    }

    private fun file(name: String, bytes: ByteArray): Path {
        val f = File.createTempFile("doc", name)
        f.deleteOnExit()
        f.writeBytes(bytes)
        return f.toOkioPath()
    }

    private fun open(path: Path, name: String): Pair<DocFormat, TextDocument> {
        val head = fs.read(path) { readByteArray(minOf(4096L, fs.metadata(path).size ?: 0L)) }
        val format = DocumentReader.detect(name, head, fs, path)
        return format to DocumentReader.read(fs, path, format, name)
    }

    private fun TextDocument.paras() = blocks.filterIsInstance<Para>()

    @Test
    fun epubChaptersImagesAndMetadata() {
        val opf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Pan Tadeusz</dc:title><dc:creator>Adam Mickiewicz</dc:creator><dc:language>pl</dc:language></metadata>
              <manifest>
                <item id="c1" href="Text/rozdzial%201.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="Text/r2.xhtml" media-type="application/xhtml+xml"/>
                <item id="img" href="Images/a.png" media-type="image/png"/>
              </manifest>
              <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
            </package>"""
        val c1 = """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>x</title><style>p{}</style></head><body>
            <h1>Księga pierwsza</h1><p>Litwo! <em>Ojczyzno</em> moja!&nbsp;ty jesteś jak zdrowie.</p>
            <p>Ile cię trzeba<br/>cenić</p><img src="../Images/a.png" alt="ryc"/></body></html>"""
        val c2 = """<html><body><h2>Księga druga</h2><ul><li>jeden</li><li>dwa<ol><li>a</li></ol></li></ul>
            <table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td><b>2</b></td></tr></table></body></html>"""
        val path = zip(
            ".epub",
            linkedMapOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""".toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
                "OEBPS/Text/rozdzial 1.xhtml" to c1.toByteArray(),
                "OEBPS/Text/r2.xhtml" to c2.toByteArray(),
                "OEBPS/Images/a.png" to png,
            ),
        )
        val (format, doc) = open(path, "pan.epub")
        assertEquals(DocFormat.EPUB, format)
        assertEquals("Pan Tadeusz", doc.title)
        assertEquals("Adam Mickiewicz", doc.author)
        val paras = doc.paras()
        assertEquals(ParaKind.HEADING, paras[0].kind)
        assertEquals("Księga pierwsza", paras[0].text)
        assertEquals("Litwo! Ojczyzno moja! ty jesteś jak zdrowie.", paras[1].text)
        assertTrue(paras[1].spans.any { it.italic && it.text == "Ojczyzno" })
        assertEquals("Ile cię trzeba\ncenić", paras[2].text)
        assertTrue(doc.blocks.any { it is Picture })
        assertTrue(doc.blocks.any { it is PageBreak })
        assertEquals(listOf("•", "•", "1."), paras.mapNotNull { it.marker })
        val table = doc.blocks.filterIsInstance<Table>().single()
        assertTrue(table.header)
        assertEquals("2", table.rows[1][1].joinToString("") { it.text })
        assertTrue(table.rows[1][1].first().bold)
        // HTML dla iOS powstaje bez błędów i zawiera obraz.
        assertTrue(DocHtml.render(doc).contains("data:image/png;base64,"))
    }

    @Test
    fun docxHeadingsListsTablesAndFormatting() {
        val w = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"r\" xmlns:a=\"a\""
        val document = """<?xml version="1.0" encoding="UTF-8"?><w:document $w><w:body>
            <w:p><w:pPr><w:pStyle w:val="Nagwek1"/></w:pPr><w:r><w:t>Wstęp</w:t></w:r></w:p>
            <w:p><w:pPr><w:jc w:val="both"/></w:pPr><w:r><w:t xml:space="preserve">Zwykły </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>gruby</w:t></w:r><w:r><w:rPr><w:b w:val="0"/><w:i/></w:rPr><w:t xml:space="preserve"> pochyły</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>pierwszy</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>drugi</w:t></w:r></w:p>
            <w:p><w:r><w:t>przed</w:t></w:r><w:r><w:br w:type="page"/></w:r><w:r><w:t>po</w:t></w:r></w:p>
            <w:p><w:r><w:drawing><a:blip r:embed="rId5"/></w:drawing></w:r></w:p>
            <w:tbl><w:tr><w:tc><w:p><w:r><w:t>k1</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>k2</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            </w:body></w:document>"""
        val styles = """<w:styles $w><w:style w:type="paragraph" w:styleId="Nagwek1"><w:name w:val="heading 1"/></w:style></w:styles>"""
        val numbering = """<w:numbering $w><w:abstractNum w:abstractNumId="7"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="lowerRoman"/><w:lvlText w:val="%1)"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="7"/></w:num></w:numbering>"""
        val rels = """<Relationships><Relationship Id="rId5" Target="media/image1.png"/></Relationships>"""
        val path = zip(
            ".docx",
            linkedMapOf(
                "[Content_Types].xml" to "<Types/>".toByteArray(),
                "word/document.xml" to document.toByteArray(),
                "word/styles.xml" to styles.toByteArray(),
                "word/numbering.xml" to numbering.toByteArray(),
                "word/_rels/document.xml.rels" to rels.toByteArray(),
                "word/media/image1.png" to png,
                "docProps/core.xml" to "<cp:coreProperties xmlns:dc=\"dc\" xmlns:cp=\"cp\"><dc:title>Raport</dc:title></cp:coreProperties>".toByteArray(),
            ),
        )
        val (format, doc) = open(path, "raport.docx")
        assertEquals(DocFormat.DOCX, format)
        assertEquals("Raport", doc.title)
        val paras = doc.paras()
        assertEquals(ParaKind.HEADING, paras[0].kind)
        assertEquals(1, paras[0].level)
        assertEquals("Zwykły gruby pochyły", paras[1].text)
        assertTrue(paras[1].spans.any { it.bold && it.text == "gruby" })
        assertTrue(paras[1].spans.any { it.italic && !it.bold })
        assertEquals("i)", paras[2].marker)
        assertEquals("ii)", paras[3].marker)
        assertTrue(doc.blocks.any { it is PageBreak })
        assertTrue(doc.blocks.any { it is Picture })
        assertEquals("k2", doc.blocks.filterIsInstance<Table>().single().rows[0][1].joinToString("") { it.text })
    }

    @Test
    fun odtParagraphsStylesAndLists() {
        val content = """<office:document-content xmlns:office="o" xmlns:text="t" xmlns:style="s" xmlns:fo="f">
            <office:automatic-styles>
              <style:style style:name="T1" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>
              <style:style style:name="P1" style:family="paragraph"><style:paragraph-properties fo:text-align="center"/></style:style>
              <text:list-style style:name="L1"><text:list-level-style-number text:level="1"/></text:list-style>
            </office:automatic-styles>
            <office:body><office:text>
              <text:h text:outline-level="2">Rozdział</text:h>
              <text:p text:style-name="P1">Środek <text:span text:style-name="T1">tłusty</text:span><text:s/>tekst</text:p>
              <text:list text:style-name="L1"><text:list-item><text:p>raz</text:p></text:list-item><text:list-item><text:p>dwa</text:p></text:list-item></text:list>
            </office:text></office:body></office:document-content>"""
        val path = zip(
            ".odt",
            linkedMapOf("mimetype" to "application/vnd.oasis.opendocument.text".toByteArray(), "content.xml" to content.toByteArray()),
        )
        val (format, doc) = open(path, "a.odt")
        assertEquals(DocFormat.ODT, format)
        val paras = doc.paras()
        assertEquals(ParaKind.HEADING, paras[0].kind)
        assertEquals(2, paras[0].level)
        assertTrue(paras[1].spans.any { it.bold && it.text == "tłusty" })
        assertEquals(com.blacksingularities.openpdf.doc.Align.CENTER, paras[1].align)
        assertEquals(listOf("1.", "2."), paras.drop(2).map { it.marker })
    }

    @Test
    fun fictionBookSectionsAndCover() {
        val img = base64Encode(png)
        val fb2 = """<?xml version="1.0" encoding="windows-1250"?>
            <FictionBook xmlns:l="http://www.w3.org/1999/xlink"><description><title-info><author><first-name>Bolesław</first-name><last-name>Prus</last-name></author>
            <book-title>Lalka</book-title><lang>pl</lang><coverpage><image l:href="#c.png"/></coverpage></title-info></description>
            <body><section><title><p>Tom I</p></title><p>Pierwszy <emphasis>akapit</emphasis>.</p><empty-line/><p>Drugi</p></section>
            <section><title><p>Tom II</p></title><poem><stanza><v>wers</v></stanza></poem></section></body>
            <binary id="c.png" content-type="image/png">$img</binary></FictionBook>"""
        val path = file(".fb2", fb2.toByteArray(charset("windows-1250")))
        val (format, doc) = open(path, "lalka.fb2")
        assertEquals(DocFormat.FB2, format)
        assertEquals("Lalka", doc.title)
        assertEquals("Bolesław Prus", doc.author)
        assertTrue(doc.blocks.first() is Picture)
        val paras = doc.paras().filter { it.text.isNotBlank() }
        assertEquals("Tom I", paras[0].text)
        assertEquals(ParaKind.HEADING, paras[0].kind)
        assertEquals(ParaKind.VERSE, paras.last().kind)
        assertTrue(doc.blocks.count { it is PageBreak } >= 2)
    }

    @Test
    fun rtfWithCodepageAndUnicode() {
        val rtf = "{\\rtf1\\ansi\\ansicpg1250{\\fonttbl{\\f0 Arial;}}{\\*\\generator x;}\\pard\\qc{\\b Za\\'bf\\'f3\\'b3\\'e6} g\\u281?\\'9cl\\'b9 ja\\'9f\\'f1\\par Druga\\par}"
        val (format, doc) = open(file(".rtf", rtf.toByteArray(Charsets.ISO_8859_1)), "x.rtf")
        assertEquals(DocFormat.RTF, format)
        val paras = doc.paras()
        assertEquals("Zażółć gęślą jaźń", paras[0].text)
        assertTrue(paras[0].spans.first().bold)
        assertEquals("Druga", paras[1].text)
    }

    @Test
    fun markdownBlocks() {
        val md = "# Tytuł\n\nTekst **gruby** i *pochyły* z `kodem` oraz [linkiem](http://x).\n\n- a\n- b\n\n1. raz\n\n```\nkod()\n```\n\n| A | B |\n|---|---|\n| 1 | 2 |\n"
        val (format, doc) = open(file(".md", md.toByteArray()), "notatki.md")
        assertEquals(DocFormat.MARKDOWN, format)
        val p = doc.paras()
        assertEquals(ParaKind.HEADING, p[0].kind)
        assertEquals("Tekst gruby i pochyły z kodem oraz linkiem.", p[1].text)
        assertTrue(p[1].spans.any { it.bold && it.text == "gruby" })
        assertTrue(p[1].spans.any { it.mono && it.text == "kodem" })
        assertTrue(p[1].spans.any { it.link && it.text == "linkiem" })
        assertEquals(listOf("•", "•", "1."), p.mapNotNull { it.marker })
        assertTrue(p.any { it.kind == ParaKind.CODE && it.text == "kod()" })
        assertTrue(doc.blocks.any { it is Table })
    }

    @Test
    fun plainTextEncodingsAndWrappedParagraphs() {
        assertEquals("zażółć", decodeText("zażółć".toByteArray(charset("windows-1250"))))
        assertEquals("привет мир", decodeText("привет мир".toByteArray(charset("windows-1251"))))
        assertEquals("zażółć", decodeText(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "zażółć".toByteArray()))
        val book = (1..30).joinToString("\n\n") { "To jest wiersz numer $it, ktory zostal zawiniety\nw pliku tekstowym na dwie linie tekstu." }
        val (format, doc) = open(file(".txt", book.toByteArray()), "ksiazka.txt")
        assertEquals(DocFormat.TEXT, format)
        assertEquals(30, doc.paras().size)
        assertTrue(doc.paras()[0].text.contains("zawiniety w pliku"))
    }

    @Test
    fun csvAndFileNames() {
        val (format, doc) = open(file(".csv", "a;b;c\n1;\"x;y\";3\n".toByteArray()), "dane.csv")
        assertEquals(DocFormat.CSV, format)
        val t = doc.blocks.filterIsInstance<Table>().single()
        assertEquals("x;y", t.rows[1][1].single().text)
        assertEquals("Książka.pdf", pdfFileName("Książka.epub"))
        assertEquals("plik.pdf", pdfFileName("plik.pdf"))
        assertEquals("lalka", baseName("lalka.fb2.zip"))
        assertEquals("v1.2 notatki", baseName("v1.2 notatki.md"))
    }
}
