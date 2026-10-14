package com.blacksingularities.openpdf.ios

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.pdf.PageGeo
import com.blacksingularities.openpdf.platform.PageRenderer
import com.blacksingularities.openpdf.platform.PageSize
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.*
import platform.PDFKit.*

/**
 * Rysowanie stron przez CoreGraphics: treść strony (CGPDFPage) w przestrzeni PDF, przeniesiona
 * do układu wyświetlanej strony (obrót i przycięcie), a na niej adnotacje rysowane przez PDFKit.
 */
class IosRenderer private constructor(
    override val file: Path,
    private val doc: PDFDocument,
    private val geos: List<PageGeo>,
) : PageRenderer {
    private val lock = Mutex()

    override val sizes: List<PageSize> = geos.map { PageSize(it.displayWidth, it.displayHeight) }

    override var closed = false
        private set

    override suspend fun render(page: Int, width: Int, height: Int, matrix: FloatArray?): ImageBitmap? = withContext(dispatcher) {
        lock.withLock {
            if (closed || page !in 0 until pageCount || width <= 0 || height <= 0) return@withLock null
            runCatching { draw(page, width, height, matrix) }.getOrNull()
        }
    }

    private fun draw(page: Int, width: Int, height: Int, matrix: FloatArray?): ImageBitmap? {
        val p = doc.pageAtIndex(page.toULong()) ?: return null
        val geo = geos[page]
        val bytes = try {
            ByteArray(width * height * 4)
        } catch (_: Throwable) {
            return null
        }
        val ok = bytes.usePinned { pinned ->
            val cs = CGColorSpaceCreateDeviceRGB()
            val ctx = CGBitmapContextCreate(
                pinned.addressOf(0),
                width.toULong(),
                height.toULong(),
                8u,
                (width * 4).toULong(),
                cs,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
            )
            CGColorSpaceRelease(cs)
            if (ctx == null) return@usePinned false
            CGContextSetRGBFillColor(ctx, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(ctx, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
            // Piksele z osią Y w dół (jak w obrazie).
            CGContextTranslateCTM(ctx, 0.0, height.toDouble())
            CGContextScaleCTM(ctx, 1.0, -1.0)
            // Punkty wyświetlanej strony → piksele.
            val m = matrix ?: floatArrayOf(width / geo.displayWidth, 0f, 0f, height / geo.displayHeight, 0f, 0f)
            CGContextConcatCTM(ctx, CGAffineTransformMake(m[0].toDouble(), m[1].toDouble(), m[2].toDouble(), m[3].toDouble(), m[4].toDouble(), m[5].toDouble()))
            // Przestrzeń PDF → punkty wyświetlanej strony (obrót i przycięcie).
            val o = geo.fromPdf(0f, 0f)
            val ex = geo.fromPdf(1f, 0f)
            val ey = geo.fromPdf(0f, 1f)
            val dw = geo.displayWidth.toDouble()
            val dh = geo.displayHeight.toDouble()
            CGContextConcatCTM(
                ctx,
                CGAffineTransformMake(
                    (ex.x - o.x) * dw,
                    (ex.y - o.y) * dh,
                    (ey.x - o.x) * dw,
                    (ey.y - o.y) * dh,
                    o.x * dw,
                    o.y * dh,
                ),
            )
            CGContextClipToRect(ctx, CGRectMake(geo.llx.toDouble(), geo.lly.toDouble(), geo.width.toDouble(), geo.height.toDouble()))
            CGContextDrawPDFPage(ctx, p.pageRef)
            for (a in p.annots()) {
                if (!a.shouldDisplay || a.kind == "Link" || a.kind == "Popup") continue
                runCatching { a.drawWithBox(kPDFDisplayBoxCropBox, inContext = ctx) }
            }
            CGContextRelease(ctx)
            true
        }
        if (!ok) return null
        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        return Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
    }

    override fun close() {
        closed = true
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val dispatcher = Dispatchers.Default.limitedParallelism(1)

        suspend fun open(file: Path): IosRenderer = withContext(dispatcher) {
            val doc = openDocument(file) ?: error(tr("Nie można otworzyć pliku PDF"))
            if (doc.isLocked && !doc.unlockWithPassword("")) error(tr("Nie można otworzyć pliku PDF"))
            IosRenderer(file, doc, doc.pages().map { it.geo() })
        }
    }
}
