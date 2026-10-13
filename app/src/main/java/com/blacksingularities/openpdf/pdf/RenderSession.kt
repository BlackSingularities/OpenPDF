package com.blacksingularities.openpdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

/** Rozmiar wyświetlanej strony w punktach. */
data class PageSize(val width: Float, val height: Float) {
    val aspect get() = width / height
}

/**
 * Systemowy renderer PDF (pdfium) dla jednego pliku. Renderer obsługuje tylko jedną otwartą
 * stronę naraz, więc wszystkie operacje idą przez wspólny wątek i blokadę.
 */
class RenderSession private constructor(
    val file: File,
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val sizes: List<PageSize>,
) : Closeable {
    private val lock = Mutex()

    @Volatile
    var closed = false
        private set

    val pageCount get() = sizes.size

    /**
     * Renderuje fragment strony: [width]×[height] px, [matrix] przenosi punkty strony do pikseli
     * bitmapy (null = cała strona dopasowana do bitmapy).
     */
    suspend fun render(page: Int, width: Int, height: Int, matrix: Matrix? = null): Bitmap? = withContext(dispatcher) {
        lock.withLock {
            if (closed || page !in 0 until pageCount || width <= 0 || height <= 0) return@withLock null
            val bmp = try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                return@withLock null
            }
            bmp.eraseColor(Color.WHITE)
            try {
                renderer.openPage(page).use { p -> p.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                bmp
            } catch (e: Exception) {
                bmp.recycle()
                null
            }
        }
    }

    /** Cała strona o szerokości [width] px. */
    suspend fun renderPage(page: Int, width: Int): Bitmap? {
        val s = sizes.getOrNull(page) ?: return null
        val h = (width / s.aspect).toInt().coerceAtLeast(1)
        return render(page, width, h)
    }

    /** Strona do druku/eksportu w zadanej rozdzielczości (DPI). */
    suspend fun renderAtDpi(page: Int, dpi: Int): Bitmap? {
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
