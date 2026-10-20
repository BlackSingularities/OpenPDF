package com.blacksingularities.openpdf.doc

/**
 * Dokument tekstowy (EPUB, DOCX, ODT, FB2, HTML, Markdown, RTF, TXT…) w postaci prostych bloków.
 * Platforma składa go w strony PDF – dzięki temu czytanie, wyszukiwanie, adnotacje i wszystkie
 * tryby wyświetlania działają tak samo jak dla zwykłych plików PDF.
 */
class TextDocument(
    val title: String?,
    val author: String?,
    /** Kod języka (np. „pl”) – dzielenie wyrazów. */
    val language: String?,
    val blocks: List<Block>,
    val style: PageStyle,
)

/** Fragment tekstu o jednolitym wyglądzie. */
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val mono: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
    val link: Boolean = false,
)

enum class ParaKind { BODY, HEADING, QUOTE, CODE, CAPTION, VERSE }

enum class Align { START, CENTER, END, JUSTIFY }

sealed class Block

/**
 * Akapit. [level] – poziom nagłówka (1–6), [listLevel] i [marker] – element listy
 * (wcięcie i znak „•” albo „3.”).
 */
class Para(
    val spans: List<Span>,
    val kind: ParaKind = ParaKind.BODY,
    val level: Int = 0,
    val align: Align? = null,
    val listLevel: Int = 0,
    val marker: String? = null,
) : Block() {
    val text: String get() = spans.joinToString("") { it.text }
}

/** Obraz osadzony w dokumencie (zakodowane bajty PNG/JPEG/GIF/WebP). */
class Picture(val bytes: ByteArray, val caption: String? = null) : Block()

/** Tabela: wiersze komórek, każda komórka to fragmenty tekstu ([header] – pierwszy wiersz jest nagłówkiem). */
class Table(val rows: List<List<List<Span>>>, val header: Boolean = false) : Block()

/** Nowa strona (np. początek rozdziału książki). */
data object PageBreak : Block()

/** Linia oddzielająca (sceny, sekcje). */
data object Rule : Block()

/**
 * Wygląd stron: rozmiar w punktach, marginesy, krój i rozmiar pisma. Książki (EPUB, FB2, TXT)
 * dostają format A5 z justowaniem i wcięciami akapitów, dokumenty biurowe – A4.
 */
data class PageStyle(
    val widthPt: Float,
    val heightPt: Float,
    val marginX: Float,
    val marginTop: Float,
    val marginBottom: Float,
    val fontSize: Float,
    val lineHeight: Float,
    val serif: Boolean,
    val justify: Boolean,
    /** Wcięcie pierwszego wiersza akapitu (w wielokrotnościach rozmiaru pisma). */
    val firstLineIndent: Float,
    /** Odstęp po akapicie (w wielokrotnościach rozmiaru pisma). */
    val paragraphSpacing: Float,
    val pageNumbers: Boolean,
    val centerHeadings: Boolean,
) {
    companion object {
        val BOOK = PageStyle(
            widthPt = 420f, heightPt = 595f,
            marginX = 38f, marginTop = 44f, marginBottom = 50f,
            fontSize = 11.5f, lineHeight = 1.4f,
            serif = true, justify = true,
            firstLineIndent = 1.4f, paragraphSpacing = 0.15f,
            pageNumbers = true, centerHeadings = true,
        )
        val DOCUMENT = PageStyle(
            widthPt = 595f, heightPt = 842f,
            marginX = 62f, marginTop = 60f, marginBottom = 62f,
            fontSize = 11f, lineHeight = 1.35f,
            serif = false, justify = false,
            firstLineIndent = 0f, paragraphSpacing = 0.6f,
            pageNumbers = true, centerHeadings = false,
        )
        /** Pliki „techniczne” (kod, logi, JSON) – pismo o stałej szerokości, bez justowania. */
        val PLAIN = DOCUMENT.copy(fontSize = 9.5f, lineHeight = 1.3f, paragraphSpacing = 0f, marginX = 48f)
    }
}
