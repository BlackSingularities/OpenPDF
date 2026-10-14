package com.blacksingularities.openpdf.pdf

import android.graphics.Bitmap
import com.blacksingularities.openpdf.platform.PageRenderer
import com.blacksingularities.openpdf.platform.PageTextSource
import com.blacksingularities.openpdf.platform.PasswordRequiredException
import com.blacksingularities.openpdf.platform.PdfEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import okio.Path
import java.io.File
import java.io.StringWriter

/** Silnik PDF Androida: PdfBox do zmian i odczytu struktury, systemowy PdfRenderer do rysowania. */
class AndroidPdfEngine : PdfEngine {
    private val Path.f: File get() = toFile()

    /** Błędne lub brakujące hasło PdfBoxa → wspólny wyjątek. */
    private inline fun <T> pw(block: () -> T): T = try {
        block()
    } catch (e: InvalidPasswordException) {
        throw PasswordRequiredException(e.message)
    }

    override fun needsPassword(file: Path) = PdfOps.needsPassword(file.f)

    override fun prepareWorking(original: Path, password: String?, working: Path) = pw { PdfOps.prepareWorking(original.f, password, working.f) }

    override fun rewrite(file: Path, out: Path) = PdfOps.rewrite(file.f, out.f)

    override fun buildIndex(source: Path, display: Path) = PdfOps.buildIndex(source.f, display.f)

    override suspend fun openRenderer(file: Path): PageRenderer = RenderSession.open(file.f)

    override fun openText(file: Path): PageTextSource = PdfBoxText(PdfOps.load(file.f))

    override fun commitMarks(file: Path, marks: List<Mark>, author: String) = PdfOps.commitMarks(file.f, marks, author, PdfOps.systemFonts())

    override fun deleteAnnotations(file: Path, targets: List<AnnotInfo>) = PdfOps.deleteAnnotations(file.f, targets)

    override fun deleteAllAnnotations(file: Path) = PdfOps.deleteAllAnnotations(file.f)

    override fun rotatePages(file: Path, pages: Collection<Int>, degrees: Int) = PdfOps.rotatePages(file.f, pages, degrees)

    override fun deletePages(file: Path, pages: Collection<Int>) = PdfOps.deletePages(file.f, pages)

    override fun movePages(file: Path, pages: List<Int>, target: Int) = PdfOps.movePages(file.f, pages, target)

    override fun duplicatePages(file: Path, pages: Collection<Int>) = PdfOps.duplicatePages(file.f, pages)

    override fun insertBlank(file: Path, at: Int) = PdfOps.insertBlank(file.f, at)

    override fun insertPdf(file: Path, other: Path, otherPassword: String?, at: Int?) = pw { PdfOps.insertPdf(file.f, other.f, otherPassword, at) }

    override fun extractPages(file: Path, pages: Collection<Int>, out: Path) = PdfOps.extractPages(file.f, pages, out.f)

    override fun insertImages(file: Path, images: List<Path>, at: Int) = PdfOps.insertImages(file.f, images.map { it.f }, at)

    override fun createFromImages(images: List<Path>, out: Path) = PdfOps.createFromImages(images.map { it.f }, out.f)

    override fun createBlank(out: Path, pages: Int, widthPt: Float, heightPt: Float) = PdfOps.createBlank(out.f, pages, PDRectangle(widthPt, heightPt))

    override fun fillFields(file: Path, edits: List<FieldEdit>) = PdfOps.fillFields(file.f, edits)

    override fun flattenForm(file: Path) = PdfOps.flattenForm(file.f)

    override fun setInfo(file: Path, e: InfoEdit) = PdfOps.setInfo(file.f, e)

    override fun export(file: Path, out: Path, userPassword: String?, ownerPassword: String?) = PdfOps.export(file.f, out.f, userPassword, ownerPassword)

    override fun compress(file: Path, out: Path, maxSide: Int, quality: Float, onProgress: (Float) -> Unit) = PdfOps.compress(file.f, out.f, maxSide, quality, onProgress)

    override fun imageToPng(src: Path, maxSide: Int, out: Path): Float {
        val bmp = PdfOps.decodeScaled(src.f, maxSide) ?: error("Nie można odczytać obrazu")
        try {
            out.f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bmp.width.toFloat() / bmp.height
        } finally {
            bmp.recycle()
        }
    }
}

/** Tekst stron z położeniem znaków (PdfBox). */
private class PdfBoxText(private val doc: PDDocument) : PageTextSource {
    override fun page(index: Int, geo: PageGeo): PageText? {
        if (index !in 0 until doc.numberOfPages) return null
        val sb = StringBuilder()
        val boxes = ArrayList<Float>()
        val dw = geo.displayWidth
        val dh = geo.displayHeight
        fun gap(c: Char) {
            sb.append(c)
            repeat(4) { boxes += Float.NaN }
        }
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                for (tp in textPositions) {
                    val u = tp.unicode ?: continue
                    val h = tp.heightDir.takeIf { it > 0f } ?: tp.fontSizeInPt
                    val l = tp.xDirAdj / dw
                    val r = (tp.xDirAdj + tp.widthDirAdj) / dw
                    val t = (tp.yDirAdj - h * 1.15f) / dh
                    val b = (tp.yDirAdj + h * 0.28f) / dh
                    for (ch in u) {
                        sb.append(ch)
                        boxes += l; boxes += t; boxes += r; boxes += b
                    }
                }
            }

            override fun writeWordSeparator() = gap(' ')
            override fun writeLineSeparator() = gap('\n')
        }
        stripper.sortByPosition = true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        stripper.writeText(doc, StringWriter())
        return PageText(sb.toString(), boxes.toFloatArray())
    }

    override fun close() {
        runCatching { doc.close() }
    }
}
