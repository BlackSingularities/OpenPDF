package com.blacksingularities.openpdf.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

/** Ostry fragment strony wyrenderowany przy powiększeniu. */
class Tile(val page: Int, val region: RectN, val pageWidthPx: Int, val bitmap: Bitmap)

/** Zlecenie kafelka: fragment [region] strony, gdy cała strona ma [pageWidthPx] pikseli szerokości. */
data class TileRequest(val page: Int, val region: RectN, val pageWidthPx: Int)

/**
 * Obrazy stron dla przeglądarki: podstawowa bitmapa każdej strony (ostra przy powiększeniu 1),
 * kafelki z widocznymi fragmentami przy większym powiększeniu oraz miniatury. Renderuje w tle,
 * najpierw to, co widać. [version] rośnie po każdej gotowej bitmapie – ekran wie, że ma się odświeżyć.
 */
class PageImages(private val scope: CoroutineScope) {
    val version = mutableIntStateOf(0)
    val thumbVersion = mutableIntStateOf(0)

    private var session: RenderSession? = null
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 5).coerceAtMost(256L * 1024 * 1024).toInt()

    private val base = object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    private val thumbs = object : LruCache<Int, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    @Volatile private var tiles: Map<Int, List<Tile>> = emptyMap()
    @Volatile private var wantedBase: List<Pair<Int, Int>> = emptyList()
    @Volatile private var wantedTiles: List<TileRequest> = emptyList()
    @Volatile private var wantedThumbs: List<Int> = emptyList()
    private val failed = HashSet<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loopStarted = false

    fun attach(s: RenderSession?) {
        session = s
        base.evictAll()
        thumbs.evictAll()
        tiles = emptyMap()
        synchronized(failed) { failed.clear() }
        wantedBase = emptyList()
        wantedTiles = emptyList()
        wantedThumbs = emptyList()
        version.intValue++
        thumbVersion.intValue++
        if (!loopStarted) {
            loopStarted = true
            scope.launch { loop() }
        }
    }

    fun base(page: Int): Bitmap? = base.get(page)

    fun tiles(page: Int): List<Tile> = tiles[page].orEmpty()

    fun thumb(page: Int): Bitmap? {
        val t = thumbs.get(page)
        if (t != null) return t
        // Z braku miniatury – podstawowy obraz strony, jeśli jest.
        return base.get(page)
    }

    /** Szerokości w pikselach są zaokrąglane w górę do wielokrotności 128 – mniej ponownych renderów. */
    fun bucket(px: Float): Int = (ceil(px / 128f) * 128).toInt().coerceIn(128, 2560)

    /** Zlecenia dla widocznego obszaru (w kolejności ważności). */
    fun request(baseWanted: List<Pair<Int, Int>>, tileWanted: List<TileRequest>) {
        wantedBase = baseWanted
        wantedTiles = tileWanted
        // Kafelki niepotrzebne już na ekranie – zwalniamy.
        val keepPages = tileWanted.map { it.page }.toSet()
        if (tiles.keys.any { it !in keepPages }) tiles = tiles.filterKeys { it in keepPages }
        wake.trySend(Unit)
    }

    fun requestThumbs(pages: List<Int>) {
        wantedThumbs = pages
        wake.trySend(Unit)
    }

    private suspend fun loop() {
        while (true) {
            val did = runCatching { step() }.getOrDefault(false)
            if (!did) wake.receive()
        }
    }

    /** Wykonuje jedno najważniejsze zadanie; false = nic do zrobienia. */
    private suspend fun step(): Boolean {
        val s = session ?: return false
        if (s.closed) return false
        // 1. Kafelki widocznych stron (ostre przy powiększeniu).
        for (req in wantedTiles) {
            if (covered(req)) continue
            val key = "t${req.page}:${req.pageWidthPx}:${req.region}"
            if (synchronized(failed) { key in failed }) continue
            val size = s.sizes.getOrNull(req.page) ?: continue
            val pageH = req.pageWidthPx / size.aspect
            val w = (req.region.width * req.pageWidthPx).toInt()
            val h = (req.region.height * pageH).toInt()
            if (w <= 0 || h <= 0) continue
            val m = Matrix()
            val k = req.pageWidthPx / size.width
            m.setScale(k, k)
            m.postTranslate(-req.region.left * req.pageWidthPx, -req.region.top * pageH)
            val bmp = s.render(req.page, w, h, m)
            if (session !== s) return true
            if (bmp == null) {
                synchronized(failed) { failed += key }
                return true
            }
            val tile = Tile(req.page, req.region, req.pageWidthPx, bmp)
            val current = tiles[req.page].orEmpty().filter { t ->
                // Zostają tylko kafelki w tej samej rozdzielczości, które nie są zakryte przez nowy.
                t.pageWidthPx == req.pageWidthPx && !(req.region.contains(PointN(t.region.left, t.region.top)) && req.region.contains(PointN(t.region.right, t.region.bottom)))
            }.takeLast(3)
            tiles = tiles + (req.page to current + tile)
            version.intValue++
            return true
        }
        // 2. Podstawowe obrazy stron.
        for ((page, width) in wantedBase) {
            val have = base.get(page)
            if (have != null && have.width >= width * 0.9f) continue
            val key = "b$page:$width"
            if (synchronized(failed) { key in failed }) continue
            val bmp = s.renderPage(page, width)
            if (session !== s) return true
            if (bmp == null) {
                synchronized(failed) { failed += key }
                return true
            }
            base.put(page, bmp)
            version.intValue++
            thumbVersion.intValue++
            return true
        }
        // 3. Miniatury (siatka stron, wyszukiwanie).
        for (page in wantedThumbs) {
            if (thumbs.get(page) != null) continue
            val key = "m$page"
            if (synchronized(failed) { key in failed }) continue
            val size = s.sizes.getOrNull(page) ?: continue
            val w = if (size.aspect > 1f) 360 else max(160, (300 * size.aspect).toInt())
            val bmp = s.renderPage(page, w)
            if (session !== s) return true
            if (bmp == null) {
                synchronized(failed) { failed += key }
                return true
            }
            thumbs.put(page, bmp)
            thumbVersion.intValue++
            return true
        }
        return false
    }

    private fun covered(req: TileRequest): Boolean = tiles[req.page].orEmpty().any { t ->
        t.pageWidthPx == req.pageWidthPx &&
            t.region.left <= req.region.left + 1e-4f && t.region.top <= req.region.top + 1e-4f &&
            t.region.right >= req.region.right - 1e-4f && t.region.bottom >= req.region.bottom - 1e-4f
    }
}
