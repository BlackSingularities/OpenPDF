package com.blacksingularities.openpdf.pdf

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import com.blacksingularities.openpdf.platform.LruCache
import com.blacksingularities.openpdf.platform.PageRenderer
import com.blacksingularities.openpdf.platform.maxImageCacheBytes
import com.blacksingularities.openpdf.platform.renderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

/** Ostry fragment strony wyrenderowany przy powiększeniu. */
class Tile(val page: Int, val region: RectN, val pageWidthPx: Int, val bitmap: ImageBitmap)

/** Zlecenie kafelka: fragment [region] strony, gdy cała strona ma [pageWidthPx] pikseli szerokości. */
data class TileRequest(val page: Int, val region: RectN, val pageWidthPx: Int)

/**
 * Obrazy stron dla przeglądarki: podstawowa bitmapa każdej strony (ostra przy powiększeniu 1),
 * kafelki z widocznymi fragmentami przy większym powiększeniu oraz miniatury. Renderuje w tle,
 * najpierw to, co widać. [version] rośnie po każdej gotowej bitmapie – ekran wie, że ma się odświeżyć.
 * Stan zmieniany jest tylko w wątku głównym (pętla działa w [scope] modelu widoku).
 */
class PageImages(private val scope: CoroutineScope) {
    val version = mutableIntStateOf(0)
    val thumbVersion = mutableIntStateOf(0)

    private var session: PageRenderer? = null
    private val maxBytes = maxImageCacheBytes()

    private val base = LruCache<Int, ImageBitmap>(maxBytes) { _, v -> v.width * v.height * 4 }
    private val thumbs = LruCache<Int, ImageBitmap>(24 * 1024 * 1024) { _, v -> v.width * v.height * 4 }

    private var tiles: Map<Int, List<Tile>> = emptyMap()
    private var wantedBase: List<Pair<Int, Int>> = emptyList()
    private var wantedTiles: List<TileRequest> = emptyList()
    private var wantedThumbs: List<Int> = emptyList()
    private val failed = HashSet<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loopStarted = false

    fun attach(s: PageRenderer?) {
        session = s
        base.evictAll()
        thumbs.evictAll()
        tiles = emptyMap()
        failed.clear()
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

    fun base(page: Int): ImageBitmap? = base.get(page)

    fun tiles(page: Int): List<Tile> = tiles[page].orEmpty()

    fun thumb(page: Int): ImageBitmap? {
        val t = thumbs.get(page)
        if (t != null) return t
        // Z braku miniatury – podstawowy obraz strony, jeśli jest.
        return base.get(page)
    }

    /** Szerokości w pikselach są zaokrąglane w górę do wielokrotności 128 – mniej ponownych renderów. */
    fun bucket(px: Float): Int = (ceil(px / 128f) * 128).toInt().coerceIn(128, 2560)

    /** Zlecenia dla widocznego obszaru (w kolejności ważności). */
    private var visibleBase = Int.MAX_VALUE

    /** Czy szerokości w zleceniach są dokładne (obraz 1:1 z ekranem), a nie zaokrąglone. */
    private var exactBase = false

    /**
     * [visibleCount] – ile pierwszych pozycji [baseWanted] jest teraz na ekranie; [exact] – obraz
     * ma mieć dokładnie zadaną szerokość (ostry tekst bez przeskalowania).
     */
    fun request(baseWanted: List<Pair<Int, Int>>, tileWanted: List<TileRequest>, visibleCount: Int = baseWanted.size, exact: Boolean = false) {
        visibleBase = visibleCount
        exactBase = exact
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
            if (key in failed) continue
            val size = s.sizes.getOrNull(req.page) ?: continue
            val pageH = req.pageWidthPx / size.aspect
            val w = (req.region.width * req.pageWidthPx).toInt()
            val h = (req.region.height * pageH).toInt()
            if (w <= 0 || h <= 0) continue
            val k = req.pageWidthPx / size.width
            val m = floatArrayOf(k, 0f, 0f, k, -req.region.left * req.pageWidthPx, -req.region.top * pageH)
            val bmp = s.render(req.page, w, h, m)
            if (session !== s) return true
            if (bmp == null) {
                failed += key
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
        // 2. Pełne obrazy widocznych stron, 3. szybkie miniatury sąsiednich (podgląd),
        // 4. pełne obrazy sąsiednich stron.
        val wanted = wantedBase
        val visibleCount = visibleBase.coerceIn(0, wanted.size)
        if (renderBase(s, wanted.subList(0, visibleCount))) return true
        if (renderThumbs(s)) return true
        return renderBase(s, wanted.subList(visibleCount, wanted.size))
    }

    private suspend fun renderBase(s: PageRenderer, list: List<Pair<Int, Int>>): Boolean {
        for ((page, width) in list) {
            val have = base.get(page)
            if (have != null && (if (exactBase) have.width in width - 1..width + 1 else have.width >= width * 0.9f)) continue
            val key = "b$page:$width"
            if (key in failed) continue
            val bmp = s.renderPage(page, width)
            if (session !== s) return true
            if (bmp == null) {
                failed += key
                return true
            }
            base.put(page, bmp)
            version.intValue++
            thumbVersion.intValue++
            return true
        }
        return false
    }

    private suspend fun renderThumbs(s: PageRenderer): Boolean {
        for (page in wantedThumbs) {
            if (thumbs.get(page) != null) continue
            val key = "m$page"
            if (key in failed) continue
            val size = s.sizes.getOrNull(page) ?: continue
            val w = if (size.aspect > 1f) 360 else max(160, (300 * size.aspect).toInt())
            val bmp = s.renderPage(page, w)
            if (session !== s) return true
            if (bmp == null) {
                failed += key
                return true
            }
            thumbs.put(page, bmp)
            thumbVersion.intValue++
            version.intValue++
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
