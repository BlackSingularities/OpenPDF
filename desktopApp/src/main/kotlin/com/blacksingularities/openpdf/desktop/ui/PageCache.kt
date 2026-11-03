package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.blacksingularities.openpdf.desktop.DocTab
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wyrenderowane strony w pamięci (wspólne dla wszystkich kart). Przy zmianie powiększenia od razu
 * pokazywany jest najbliższy dostępny obraz strony, a ostry renderuje się w tle.
 */
object PageCache {
    private class Entry(val tab: Int, val page: Int, val width: Int, val image: ImageBitmap) {
        val bytes = image.width.toLong() * image.height * 4
    }

    private val entries = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private var bytes = 0L
    private val limit = (Runtime.getRuntime().maxMemory() * 0.45).toLong().coerceIn(160L shl 20, 900L shl 20)

    /** Największa liczba pikseli jednego obrazu strony; większe powiększenia dorysowują ostry fragment. */
    const val MAX_PIXELS = 14_000_000

    fun put(tab: Int, page: Int, image: ImageBitmap) {
        val e = Entry(tab, page, image.width, image)
        entries.put("$tab:$page:${image.width}", e)?.let { bytes -= it.bytes }
        bytes += e.bytes
        val it = entries.entries.iterator()
        while (bytes > limit && it.hasNext()) {
            val old = it.next().value
            if (old === e) continue
            bytes -= old.bytes
            it.remove()
        }
    }

    /** Obraz najbliższy szerokości [width]: najmniejszy nie węższy, a gdy takiego brak – największy. */
    fun best(tab: Int, page: Int, width: Int): ImageBitmap? {
        var above: Entry? = null
        var below: Entry? = null
        for (e in entries.values) {
            if (e.tab != tab || e.page != page) continue
            if (e.width >= width) {
                if (above == null || e.width < above.width) above = e
            } else if (below == null || e.width > below.width) below = e
        }
        val pick = above ?: below ?: return null
        entries["$tab:$page:${pick.width}"]
        return pick.image
    }

    fun dropTab(tab: Int) {
        val it = entries.entries.iterator()
        while (it.hasNext()) {
            val e = it.next().value
            if (e.tab == tab) {
                bytes -= e.bytes
                it.remove()
            }
        }
    }
}

/** Rozmiar renderu przycięty do [PageCache.MAX_PIXELS] (z zachowaniem proporcji). */
fun cappedSize(w: Int, h: Int): Pair<Int, Int> {
    val px = w.toLong() * h
    if (px <= PageCache.MAX_PIXELS) return w to h
    val k = sqrt(PageCache.MAX_PIXELS.toDouble() / px)
    return (w * k).toInt().coerceAtLeast(1) to (h * k).toInt().coerceAtLeast(1)
}

/** Obraz strony o szerokości około [w] px; do czasu wyrenderowania – najbliższy dostępny. */
@Composable
fun pageImage(tab: DocTab, page: Int, w: Int, h: Int, quick: Boolean = false): ImageBitmap? {
    val renderer = if (quick) tab.thumbs ?: tab.renderer else tab.renderer
    val (rw, rh) = cappedSize(w.coerceAtLeast(1), h.coerceAtLeast(1))
    var img by remember(tab.id, page) { mutableStateOf(PageCache.best(tab.id, page, rw)) }
    LaunchedEffect(tab.id, page, rw, rh, renderer) {
        val r = renderer ?: return@LaunchedEffect
        val have = PageCache.best(tab.id, page, rw)
        if (have != null) img = have
        val sharp = have != null && have.width >= rw * 0.97f && have.width <= rw * 1.7f
        if (sharp) return@LaunchedEffect
        // Przy płynnej zmianie powiększenia renderujemy dopiero, gdy rozmiar się ustabilizuje.
        if (have != null && !quick) delay(140)
        if (have != null && abs(have.width - rw) < 2) return@LaunchedEffect
        val bmp = r.render(page, rw, rh) ?: return@LaunchedEffect
        PageCache.put(tab.id, page, bmp)
        img = bmp
    }
    return img
}
