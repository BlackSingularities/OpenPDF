package com.blacksingularities.openpdf.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.blacksingularities.openpdf.doc.TextDocument
import com.blacksingularities.openpdf.pdf.AnnotInfo
import com.blacksingularities.openpdf.pdf.DocIndex
import com.blacksingularities.openpdf.pdf.FieldEdit
import com.blacksingularities.openpdf.pdf.InfoEdit
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.PageGeo
import com.blacksingularities.openpdf.pdf.PageText
import okio.Path

/** Plik wymaga hasła (albo podane hasło jest błędne). */
class PasswordRequiredException(message: String? = null) : Exception(message)

/** Rozmiar wyświetlanej strony w punktach. */
data class PageSize(val width: Float, val height: Float) {
    val aspect get() = width / height
}

/** Rysowanie stron jednego pliku. */
interface PageRenderer {
    val file: Path
    val sizes: List<PageSize>
    val closed: Boolean
    val pageCount: Int get() = sizes.size

    /**
     * Renderuje fragment strony: [width]×[height] px. [matrix] = (a, b, c, d, e, f) przenosi punkty
     * wyświetlanej strony (początek w lewym górnym rogu, oś Y w dół) do pikseli obrazu:
     * x' = a·x + c·y + e, y' = b·x + d·y + f. Null – cała strona dopasowana do obrazu.
     */
    suspend fun render(page: Int, width: Int, height: Int, matrix: FloatArray? = null): ImageBitmap?

    fun close()
}

/** Cała strona o szerokości [width] px. */
suspend fun PageRenderer.renderPage(page: Int, width: Int): ImageBitmap? {
    val s = sizes.getOrNull(page) ?: return null
    val h = (width / s.aspect).toInt().coerceAtLeast(1)
    return render(page, width, h)
}

/** Strona do eksportu w zadanej rozdzielczości (DPI). */
suspend fun PageRenderer.renderAtDpi(page: Int, dpi: Int): ImageBitmap? {
    val s = sizes.getOrNull(page) ?: return null
    var w = (s.width / 72f * dpi).toInt()
    var h = (s.height / 72f * dpi).toInt()
    // Ograniczenie pamięci: najwyżej ok. 40 Mpx.
    val maxPx = 40_000_000f
    if (w.toFloat() * h > maxPx) {
        val k = kotlin.math.sqrt(maxPx / (w.toFloat() * h))
        w = (w * k).toInt()
        h = (h * k).toInt()
    }
    return render(page, w, h)
}

/** Tekst stron z położeniem znaków (wywołania blokujące, w tle). */
interface PageTextSource {
    fun page(index: Int, geo: PageGeo): PageText?
    fun close()
}

/**
 * Operacje na plikach PDF. Funkcje są blokujące (wywoływane w tle); funkcje zmieniające plik
 * zapisują wynik w jego miejscu.
 */
interface PdfEngine {
    fun needsPassword(file: Path): Boolean

    /**
     * Przygotowuje kopię roboczą (odszyfrowaną). Zwraca true, gdy oryginał był zaszyfrowany.
     * Rzuca [PasswordRequiredException], gdy hasło jest potrzebne albo błędne.
     */
    fun prepareWorking(original: Path, password: String?, working: Path): Boolean

    /** Zapisuje plik na nowo (naprawa plików, których nie otwiera renderer). */
    fun rewrite(file: Path, out: Path)

    /**
     * Czyta strukturę dokumentu. Gdy renderer wymaga kopii z wtopionymi adnotacjami, tworzy ją
     * w [display] i zwraca true.
     */
    fun buildIndex(source: Path, display: Path): Pair<DocIndex, Boolean>

    suspend fun openRenderer(file: Path): PageRenderer

    fun openText(file: Path): PageTextSource

    fun commitMarks(file: Path, marks: List<Mark>, author: String)
    fun deleteAnnotations(file: Path, targets: List<AnnotInfo>)
    fun deleteAllAnnotations(file: Path)

    fun rotatePages(file: Path, pages: Collection<Int>, degrees: Int)
    fun deletePages(file: Path, pages: Collection<Int>)
    fun movePages(file: Path, pages: List<Int>, target: Int)
    fun duplicatePages(file: Path, pages: Collection<Int>)
    fun insertBlank(file: Path, at: Int)
    fun insertPdf(file: Path, other: Path, otherPassword: String?, at: Int?)
    fun extractPages(file: Path, pages: Collection<Int>, out: Path)
    fun insertImages(file: Path, images: List<Path>, at: Int)
    fun createFromImages(images: List<Path>, out: Path)
    fun createBlank(out: Path, pages: Int, widthPt: Float, heightPt: Float)

    /**
     * Składa dokument tekstowy (EPUB, DOCX, ODT, FB2…) w strony PDF zapisane w [out]: łamanie
     * wierszy, dzielenie wyrazów, obrazy, tabele, spis treści z nagłówków.
     */
    suspend fun createFromDocument(doc: TextDocument, out: Path)

    /**
     * Składa PDF z [count] stron-obrazów (np. DjVu) podawanych kolejno przez [page]; plik obrazu jest
     * usuwany zaraz po osadzeniu, więc na dysku leży naraz tylko jedna strona.
     */
    suspend fun createFromImageSequence(count: Int, out: Path, page: suspend (Int) -> Path?, onProgress: (Float) -> Unit)

    /** Zwraca listę pól, których nie udało się wypełnić. */
    fun fillFields(file: Path, edits: List<FieldEdit>): List<String>
    fun flattenForm(file: Path)
    fun setInfo(file: Path, e: InfoEdit)

    /** Zapisuje dokument do [out], opcjonalnie szyfrując go hasłem. */
    fun export(file: Path, out: Path, userPassword: String?, ownerPassword: String?)

    /** Zmniejsza obrazy w pliku; zwraca liczbę zmienionych obrazów albo -1, gdy jej nie zna. */
    fun compress(file: Path, out: Path, maxSide: Int, quality: Float, onProgress: (Float) -> Unit): Int

    /** Obraz z pliku [src] przeskalowany do [maxSide] px i zapisany jako PNG w [out]; zwraca proporcje (szer./wys.). */
    fun imageToPng(src: Path, maxSide: Int, out: Path): Float
}

/** Format A4 w punktach. */
const val A4_WIDTH_PT = 595.27563f
const val A4_HEIGHT_PT = 841.8898f
