package com.blacksingularities.openpdf.pdf

import com.blacksingularities.openpdf.platform.LruCache
import com.blacksingularities.openpdf.platform.PdfEngine
import com.blacksingularities.openpdf.platform.PageTextSource
import com.blacksingularities.openpdf.platform.ioDispatcher
import com.blacksingularities.openpdf.platform.stripAccent
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Path
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Tekst jednej strony: każdy znak z prostokątem (znormalizowanym). Odstępy i końce linii
 * wstawione przez ekstraktor mają puste pole (NaN), więc indeksy tekstu i pól są zgodne.
 */
class PageText(val text: String, private val boxes: FloatArray) {
    val length get() = text.length

    fun hasBox(i: Int) = !boxes[i * 4].isNaN()

    fun box(i: Int) = RectN(boxes[i * 4], boxes[i * 4 + 1], boxes[i * 4 + 2], boxes[i * 4 + 3])

    /** Najbliższy znak (z polem) do punktu; null, gdy strona nie ma tekstu. */
    fun nearest(p: PointN, maxDist: Float = Float.MAX_VALUE): Int? {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until length) {
            if (!hasBox(i)) continue
            val l = boxes[i * 4]
            val t = boxes[i * 4 + 1]
            val r = boxes[i * 4 + 2]
            val b = boxes[i * 4 + 3]
            val dx = if (p.x < l) l - p.x else if (p.x > r) p.x - r else 0f
            // Odległość pionowa liczy się mocniej – wybór w obrębie linii.
            val dy = if (p.y < t) t - p.y else if (p.y > b) p.y - b else 0f
            val d = dx + dy * 3f
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best.takeIf { it >= 0 && bestD <= maxDist }
    }

    /** Granice słowa wokół znaku [i]. */
    fun wordAt(i: Int): IntRange {
        var s = i
        var e = i
        while (s > 0 && text[s - 1].isLetterOrDigit() && hasBox(s - 1)) s--
        while (e < length - 1 && text[e + 1].isLetterOrDigit() && hasBox(e + 1)) e++
        return s..e
    }

    /** Prostokąty zaznaczenia zakresu – po jednym na linię tekstu. */
    fun rects(range: IntRange): List<RectN> {
        val out = ArrayList<RectN>()
        var cur: RectN? = null
        for (i in range) {
            if (i !in 0 until length || !hasBox(i)) continue
            val b = box(i)
            val c = cur
            cur = if (c != null && sameLine(c, b)) c.union(b) else {
                if (c != null) out += c
                b
            }
        }
        cur?.let { out += it }
        return out
    }

    private fun sameLine(a: RectN, b: RectN): Boolean {
        val overlap = min(a.bottom, b.bottom) - max(a.top, b.top)
        return overlap > min(a.height, b.height) * 0.5f && b.left >= a.left - a.height
    }

    fun substring(range: IntRange): String {
        val s = range.first.coerceIn(0, length)
        val e = (range.last + 1).coerceIn(s, length)
        return text.substring(s, e).trim()
    }

    /** Tekst do wyszukiwania: małe litery bez znaków diakrytycznych, długość taka jak [text]. */
    val folded: String by lazy { fold(text) }

    companion object {
        fun fold(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s) {
                val c = when (ch) {
                    'ł' -> 'l'
                    'Ł' -> 'l'
                    else -> if (ch.code < 128) ch else stripAccent(ch)
                }
                sb.append(c.lowercaseChar())
            }
            return sb.toString()
        }
    }
}

data class SearchHit(val page: Int, val rects: List<RectN>, val snippet: String)

/** Wyciąganie tekstu z pozycjami (silnik platformy), z pamięcią podręczną ostatnich stron. */
class TextIndex(private val engine: PdfEngine, private val file: Path, private val geos: List<PageGeo>) {
    private val lock = Mutex()
    private var source: PageTextSource? = null
    private val cache = LruCache<Int, PageText>(40)

    suspend fun page(index: Int): PageText? = withContext(ioDispatcher) {
        lock.withLock {
            cache.get(index)?.let { return@withLock it }
            val s = source ?: runCatching { engine.openText(file) }.getOrNull()?.also { source = it } ?: return@withLock null
            val geo = geos.getOrNull(index) ?: return@withLock null
            val pt = runCatching { s.page(index, geo) }.getOrNull() ?: PageText("", FloatArray(0))
            cache.put(index, pt)
            pt
        }
    }

    /** Szuka frazy we wszystkich stronach; [onHit] wołane na bieżąco. */
    suspend fun search(query: String, onProgress: (Float) -> Unit, onHit: (SearchHit) -> Unit) {
        val q = PageText.fold(query.trim())
        if (q.isEmpty()) return
        for (p in geos.indices) {
            coroutineContext.ensureActive()
            val text = page(p) ?: continue
            val hay = text.folded
            var from = 0
            while (true) {
                val i = hay.indexOf(q, from)
                if (i < 0) break
                val range = i until i + q.length
                val rects = text.rects(range)
                if (rects.isNotEmpty()) {
                    val s = max(0, i - 30)
                    val e = min(text.length, i + q.length + 40)
                    val snippet = (if (s > 0) "…" else "") + text.text.substring(s, e).replace('\n', ' ') + (if (e < text.length) "…" else "")
                    onHit(SearchHit(p, rects, snippet))
                }
                from = i + max(1, q.length)
            }
            onProgress((p + 1f) / geos.size)
        }
    }

    /** Cały tekst dokumentu (np. do eksportu .txt). */
    suspend fun allText(onProgress: (Float) -> Unit): String {
        val sb = StringBuilder()
        for (p in geos.indices) {
            coroutineContext.ensureActive()
            page(p)?.let { sb.append(it.text.trim()).append("\n\n") }
            onProgress((p + 1f) / geos.size)
        }
        return sb.toString()
    }

    suspend fun close() {
        lock.withLock {
            runCatching { source?.close() }
            source = null
            cache.evictAll()
        }
    }
}
