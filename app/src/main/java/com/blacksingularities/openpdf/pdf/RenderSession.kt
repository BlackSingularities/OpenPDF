package com.blacksingularities.openpdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.blacksingularities.openpdf.platform.PageRenderer
import com.blacksingularities.openpdf.platform.PageSize
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.Executors

/**
 * Systemowy renderer PDF (pdfium) dla jednego pliku. Renderer obsługuje tylko jedną otwartą
 * stronę naraz, więc wszystkie operacje idą przez wspólny wątek i blokadę.
 */
class RenderSession private constructor(
    private val source: File,
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    override val sizes: List<PageSize>,
) : PageRenderer {
    private val lock = Mutex()

    override val file: Path = source.toOkioPath()

    @Volatile
    override var closed = false
        private set

    override suspend fun render(page: Int, width: Int, height: Int, matrix: FloatArray?): ImageBitmap? = withContext(dispatcher) {
        lock.withLock {
            if (closed || page !in 0 until pageCount || width <= 0 || height <= 0) return@withLock null
            val bmp = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                return@withLock null
            }
            bmp.eraseColor(Color.WHITE)
            val m = matrix?.let { v ->
                Matrix().apply { setValues(floatArrayOf(v[0], v[2], v[4], v[1], v[3], v[5], 0f, 0f, 1f)) }
            }
            try {
                renderer.openPage(page).use { p -> p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                bmp.asImageBitmap()
            } catch (e: Exception) {
                bmp.recycle()
                null
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // Zamknięcie w kolejce wątku renderowania – po zakończeniu bieżącej strony.
        executor.execute {
            runCatching { renderer.close() }
            runCatching { fd.close() }
        }
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "pdf-render").apply { priority = Thread.NORM_PRIORITY + 1 } }
        val dispatcher = executor.asCoroutineDispatcher()

        /** Otwiera plik; rzuca wyjątek, gdy systemowy renderer go nie obsłuży. */
        suspend fun open(file: File): RenderSession = withContext(dispatcher) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(fd)
                val sizes = (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { p -> PageSize(p.width.toFloat().coerceAtLeast(1f), p.height.toFloat().coerceAtLeast(1f)) }
                }
                RenderSession(file, fd, renderer, sizes)
            } catch (e: Throwable) {
                runCatching { fd.close() }
                throw e
            }
        }
    }
}
