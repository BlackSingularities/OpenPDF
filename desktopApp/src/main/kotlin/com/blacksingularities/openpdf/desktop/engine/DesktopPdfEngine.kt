package com.blacksingularities.openpdf.desktop.engine

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.blacksingularities.openpdf.doc.TextDocument
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.pdf.AnnotInfo
import com.blacksingularities.openpdf.pdf.DocIndex
import com.blacksingularities.openpdf.pdf.DocInfo
import com.blacksingularities.openpdf.pdf.FieldEdit
import com.blacksingularities.openpdf.pdf.InfoEdit
import com.blacksingularities.openpdf.pdf.LinkInfo
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.OutlineEntry
import com.blacksingularities.openpdf.pdf.PageGeo
import com.blacksingularities.openpdf.pdf.PageText
import com.blacksingularities.openpdf.pdf.normalizeRotation
import com.blacksingularities.openpdf.platform.PageRenderer
import com.blacksingularities.openpdf.platform.PageSize
import com.blacksingularities.openpdf.platform.PageTextSource
import com.blacksingularities.openpdf.platform.PasswordRequiredException
import com.blacksingularities.openpdf.platform.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toOkioPath
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.rendering.RenderDestination
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.io.StringWriter
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Silnik PDF Windows (PDFBox) – tylko to, czego potrzebuje przeglądarka: otwieranie (także
 * zaszyfrowanych plików), struktura dokumentu, rysowanie stron, tekst oraz składanie PDF z innych
 * formatów. Operacje zmieniające dokument nie są dostępne.
 */
class DesktopPdfEngine : PdfEngine {
    private val Path.f: File get() = toFile()

    private fun unsupported(): Nothing = throw UnsupportedOperationException(tr("Ta wersja jest tylko przeglądarką"))

    override fun needsPassword(file: Path): Boolean = try {
        load(file.f).close()
        false
    } catch (_: InvalidPasswordException) {
        true
    }

    override fun prepareWorking(original: Path, password: String?, working: Path): Boolean {
        val doc = try {
            load(original.f, password)
        } catch (e: InvalidPasswordException) {
            throw PasswordRequiredException(e.message)
        }
        doc.use {
            if (!it.isEncrypted) {
                original.f.copyTo(working.f, overwrite = true)
                return false
            }
            it.isAllSecurityToBeRemoved = true
            it.save(working.f)
            return true
        }
    }

    override fun rewrite(file: Path, out: Path) {
        load(file.f).use { it.save(out.f) }
    }

    override fun buildIndex(source: Path, display: Path): Pair<DocIndex, Boolean> = load(source.f).use { doc ->
        val geos = ArrayList<PageGeo>()
        val links = ArrayList<List<LinkInfo>>()
        for (page in doc.pages) {
            val crop = page.cropBox
            val geo = PageGeo(crop.lowerLeftX, crop.lowerLeftY, crop.width.coerceAtLeast(1f), crop.height.coerceAtLeast(1f), normalizeRotation(page.rotation))
            geos += geo
            val pageLinks = ArrayList<LinkInfo>()
            runCatching { page.annotations }.getOrNull().orEmpty().forEach { a ->
                if (a !is PDAnnotationLink) return@forEach
                val r = a.rectangle ?: return@forEach
                val (target, uri) = resolveLink(doc, a)
                if (target != null || uri != null) {
                    pageLinks += LinkInfo(geo.rectFromPdf(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY), target, uri)
                }
            }
            links += pageLinks
        }
        val outline = ArrayList<OutlineEntry>()
        runCatching { doc.documentCatalog.documentOutline?.let { readOutline(doc, it, 0, outline) } }
        val info = runCatching {
            val i = doc.documentInformation
            DocInfo(
                title = i.title ?: "",
                author = i.author ?: "",
                subject = i.subject ?: "",
                keywords = i.keywords ?: "",
                creator = i.creator ?: "",
                producer = i.producer ?: "",
                version = doc.version.toString(),
                created = runCatching { i.creationDate?.timeInMillis }.getOrNull(),
                modified = runCatching { i.modificationDate?.timeInMillis }.getOrNull(),
            )
        }.getOrDefault(DocInfo())
        DocIndex(geos, geos.map { emptyList<AnnotInfo>() }, links, outline, emptyList(), info) to false
    }

    private fun resolveLink(doc: PDDocument, link: PDAnnotationLink): Pair<Int?, String?> = try {
        when (val action = link.action) {
            is PDActionURI -> null to action.uri
            is PDActionGoTo -> destinationPage(doc, action.destination) to null
            else -> destinationPage(doc, link.destination) to null
        }
    } catch (_: Exception) {
        null to null
    }

    private fun destinationPage(doc: PDDocument, dest: PDDestination?): Int? {
        val d = when (dest) {
            is PDNamedDestination -> doc.documentCatalog.findNamedDestinationPage(dest)
            is PDPageDestination -> dest
            else -> null
        } ?: return null
        val n = d.retrievePageNumber()
        if (n >= 0) return n
        return d.page?.let { doc.pages.indexOf(it) }?.takeIf { it >= 0 }
    }

    private fun readOutline(doc: PDDocument, node: PDOutlineNode, depth: Int, out: MutableList<OutlineEntry>) {
        if (depth > 12 || out.size > 5000) return
        for (item in node.children()) {
            val page = runCatching {
                item.findDestinationPage(doc)?.let { p -> doc.pages.indexOf(p).takeIf { it >= 0 } }
                    ?: (item.action as? PDActionGoTo)?.let { destinationPage(doc, it.destination) }
            }.getOrNull()
            out += OutlineEntry(item.title ?: "", page, depth)
            if (item.hasChildren()) readOutline(doc, item, depth + 1, out)
        }
    }

    override suspend fun openRenderer(file: Path): PageRenderer = DesktopRenderer.open(file.f)

    override fun openText(file: Path): PageTextSource = PdfBoxText(load(file.f))

    override fun createFromImages(images: List<Path>, out: Path) {
        PDDocument(memory).use { doc ->
            for (f in images) imagePage(doc, f.f)?.let { doc.addPage(it) }
            require(doc.numberOfPages > 0) { tr("Nie udało się odczytać żadnego obrazu") }
            doc.save(out.f)
        }
    }

    override suspend fun createFromImageSequence(count: Int, out: Path, page: suspend (Int) -> Path?, onProgress: (Float) -> Unit) {
        PDDocument(memory).use { doc ->
            for (i in 0 until count) {
                val f = page(i)?.f
                if (f != null) {
                    try {
                        imagePage(doc, f)?.let { doc.addPage(it) }
                    } finally {
                        f.delete()
                    }
                }
                onProgress((i + 1f) / count)
            }
            require(doc.numberOfPages > 0) { tr("Nie udało się odczytać żadnej strony") }
            doc.save(out.f)
        }
    }

    override suspend fun createFromDocument(doc: TextDocument, out: Path) = withContext(Dispatchers.IO) {
        DocPdfWriter(doc).write(out.f)
    }

    override fun imageToPng(src: Path, maxSide: Int, out: Path): Float {
        val img = Images.decode(src.f, maxSide) ?: error(tr("Nie można odczytać obrazu"))
        javax.imageio.ImageIO.write(img, "png", out.f)
        return img.width.toFloat() / img.height
    }

    private fun imagePage(doc: PDDocument, f: File): PDPage? {
        val img = Images.decode(f, 3000) ?: return null
        val x = if (img.colorModel.hasAlpha()) LosslessFactory.createFromImage(doc, img) else JPEGFactory.createFromImage(doc, img, 0.9f)
        // Dłuższy bok jak w A4 (842 pt), proporcje obrazu zachowane.
        val long = 842f
        val (w, h) = if (img.width >= img.height) long to long * img.height / img.width else long * img.width / img.height to long
        val page = PDPage(PDRectangle(w, h))
        PDPageContentStream(doc, page).use { cs -> cs.drawImage(x, 0f, 0f, w, h) }
        return page
    }

    // Przeglądarka nie zmienia dokumentów.
    override fun commitMarks(file: Path, marks: List<Mark>, author: String) = unsupported()
    override fun deleteAnnotations(file: Path, targets: List<AnnotInfo>) = unsupported()
    override fun deleteAllAnnotations(file: Path) = unsupported()
    override fun rotatePages(file: Path, pages: Collection<Int>, degrees: Int) = unsupported()
    override fun deletePages(file: Path, pages: Collection<Int>) = unsupported()
    override fun movePages(file: Path, pages: List<Int>, target: Int) = unsupported()
    override fun duplicatePages(file: Path, pages: Collection<Int>) = unsupported()
    override fun insertBlank(file: Path, at: Int) = unsupported()
    override fun insertPdf(file: Path, other: Path, otherPassword: String?, at: Int?) = unsupported()
    override fun extractPages(file: Path, pages: Collection<Int>, out: Path) = unsupported()
    override fun insertImages(file: Path, images: List<Path>, at: Int) = unsupported()
    override fun createBlank(out: Path, pages: Int, widthPt: Float, heightPt: Float) = unsupported()
    override fun fillFields(file: Path, edits: List<FieldEdit>): List<String> = unsupported()
    override fun flattenForm(file: Path) = unsupported()
    override fun setInfo(file: Path, e: InfoEdit) = unsupported()
    override fun export(file: Path, out: Path, userPassword: String?, ownerPassword: String?) = unsupported()
    override fun compress(file: Path, out: Path, maxSide: Int, quality: Float, onProgress: (Float) -> Unit): Int = unsupported()

    companion object {
        val memory: org.apache.pdfbox.io.RandomAccessStreamCache.StreamCacheCreateFunction
            get() = MemoryUsageSetting.setupMixed(64L * 1024 * 1024).streamCache

        fun load(file: File, password: String? = null): PDDocument =
            Loader.loadPDF(RandomAccessReadBufferedFile(file), password ?: "", null, null, memory)
    }
}

/**
 * Rysowanie stron jednego pliku przez PDFBox. Renderer nie jest bezpieczny wątkowo, więc każdy
 * dokument ma własny wątek; dokumenty w różnych kartach rysują się równolegle.
 */
class DesktopRenderer private constructor(
    private val doc: PDDocument,
    override val file: Path,
    override val sizes: List<PageSize>,
) : PageRenderer {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "pdf-render").apply { isDaemon = true } }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val lock = Mutex()
    private val renderer = PDFRenderer(doc).apply {
        isSubsamplingAllowed = false
        setRenderingHints(
            RenderingHints(
                mapOf(
                    RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
                    RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                    RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
                    RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                    RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE,
                ),
            ),
        )
    }

    @Volatile
    override var closed = false
        private set

    override suspend fun render(page: Int, width: Int, height: Int, matrix: FloatArray?): ImageBitmap? =
        renderAwt(page, width, height, matrix)?.toComposeImageBitmap()

    /**
     * Strona (albo jej fragment, gdy podano [matrix]) jako obraz AWT. Macierz przenosi punkty
     * wyświetlanej strony do pikseli obrazu, jak w [PageRenderer.render].
     */
    suspend fun renderAwt(page: Int, width: Int, height: Int, matrix: FloatArray? = null): BufferedImage? = withContext(dispatcher) {
        lock.withLock {
            if (closed || page !in sizes.indices || width <= 0 || height <= 0) return@withLock null
            if (matrix == null) {
                // Cała strona: standardowa ścieżka PDFBox (przycięcie, obrót strony, adnotacje).
                return@withLock try {
                    renderer.renderImage(page, width / sizes[page].width, ImageType.RGB, RenderDestination.VIEW)
                } catch (_: OutOfMemoryError) {
                    null
                } catch (_: Exception) {
                    null
                }
            }
            val img = try {
                BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            } catch (_: OutOfMemoryError) {
                return@withLock null
            }
            val g = img.createGraphics()
            try {
                g.color = Color.WHITE
                g.fillRect(0, 0, width, height)
                g.transform(AffineTransform(matrix[0], matrix[1], matrix[2], matrix[3], matrix[4], matrix[5]))
                renderer.renderPageToGraphics(page, g, 1f, 1f, RenderDestination.VIEW)
                img
            } catch (e: Exception) {
                null
            } finally {
                g.dispose()
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        executor.execute { runCatching { doc.close() } }
        executor.shutdown()
    }

    companion object {
        suspend fun open(file: File): DesktopRenderer = withContext(Dispatchers.IO) {
            val doc = DesktopPdfEngine.load(file)
            try {
                val sizes = doc.pages.map { p ->
                    val c = p.cropBox
                    val q = normalizeRotation(p.rotation) % 180 != 0
                    val w = max(1f, c.width)
                    val h = max(1f, c.height)
                    if (q) PageSize(h, w) else PageSize(w, h)
                }
                DesktopRenderer(doc, file.toOkioPath(), sizes)
            } catch (e: Throwable) {
                runCatching { doc.close() }
                throw e
            }
        }
    }
}

/** Tekst stron z położeniem znaków (PDFBox), we współrzędnych wyświetlanej strony. */
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

/** Odczyt obrazów: ImageIO, a dla formatów, których nie zna (WebP), dekoder Skia. */
object Images {
    fun decode(f: File, maxSide: Int): BufferedImage? {
        val img = runCatching { javax.imageio.ImageIO.read(f) }.getOrNull() ?: decodeSkia(f.readBytes()) ?: return null
        return if (max(img.width, img.height) <= maxSide) img else scale(img, maxSide)
    }

    /** Obraz zmniejszony tak, by dłuższy bok miał [maxSide] px. */
    fun scale(img: BufferedImage, maxSide: Int): BufferedImage {
        val k = maxSide.toDouble() / max(img.width, img.height)
        val w = (img.width * k).toInt().coerceAtLeast(1)
        val h = (img.height * k).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, if (img.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    fun decodeBytes(bytes: ByteArray): BufferedImage? =
        runCatching { javax.imageio.ImageIO.read(bytes.inputStream()) }.getOrNull() ?: decodeSkia(bytes)

    private fun decodeSkia(bytes: ByteArray): BufferedImage? = runCatching {
        val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        val bmp = org.jetbrains.skia.Bitmap.makeFromImage(image)
        val w = bmp.width
        val h = bmp.height
        val info = org.jetbrains.skia.ImageInfo.makeN32(w, h, org.jetbrains.skia.ColorAlphaType.UNPREMUL)
        val px = bmp.readPixels(info, w * 4, 0, 0) ?: return null
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val ints = IntArray(w * h)
        java.nio.ByteBuffer.wrap(px).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints)
        out.setRGB(0, 0, w, h, ints, 0, w)
        out
    }.getOrNull()
}
