package com.blacksingularities.openpdf.ui

import kotlin.math.max
import kotlin.math.min

/** Prostokąt w pikselach treści (przy powiększeniu 1). */
data class LRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
}

/** Strona umieszczona w rzędzie. */
data class Slot(val page: Int, val rect: LRect)

/**
 * Rząd: jedna strona albo rozkładówka (dwie strony obok siebie jak w otwartej książce).
 * [spine] – pozycja X grzbietu (dla cienia), gdy rząd ma dwa miejsca.
 */
data class Row(val index: Int, val rect: LRect, val slots: List<Slot>, val spine: Float?)

enum class FitMode { WIDTH, PAGE }
enum class ScrollMode { VERTICAL, PAGED }

/** Parametry układu stron. */
data class LayoutSpec(
    val book: Boolean,
    /** Okładka (pierwsza strona) osobno – wtedy rozkładówki to 2–3, 4–5… */
    val coverAlone: Boolean,
    /** Kolejność od prawej do lewej (manga, języki RTL). */
    val rtl: Boolean,
    val fit: FitMode,
    val scroll: ScrollMode,
    val margin: Float,
    val gap: Float,
    /** Miejsce pod przypiętym paskiem u góry (początek treści, nie przesuwa widoku). */
    val topInset: Float = 0f,
)

/** Kompletny układ dokumentu w pikselach treści. */
class DocLayout(
    val rows: List<Row>,
    val width: Float,
    val height: Float,
    val spec: LayoutSpec,
    val viewportW: Float,
    val viewportH: Float,
    private val pageRow: IntArray,
) {
    fun rowOf(page: Int): Row? = pageRow.getOrNull(page)?.let { rows.getOrNull(it) }

    fun slotOf(page: Int): Slot? = rowOf(page)?.slots?.firstOrNull { it.page == page }

    /** Rzędy przecinające zakres [from, to] wzdłuż osi przewijania (wyszukiwanie binarne). */
    fun rowsBetween(from: Float, to: Float): List<Row> {
        if (rows.isEmpty()) return emptyList()
        val vertical = spec.scroll == ScrollMode.VERTICAL
        fun start(r: Row) = if (vertical) r.rect.top else r.rect.left
        fun end(r: Row) = if (vertical) r.rect.bottom else r.rect.right
        var lo = 0
        var hi = rows.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (end(rows[mid]) < from) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<Row>()
        var i = lo
        while (i < rows.size && start(rows[i]) <= to) {
            if (end(rows[i]) >= from) out += rows[i]
            i++
        }
        return out
    }

    companion object {
        /**
         * Grupuje strony w rzędy; null = puste miejsce (np. okładka po prawej stronie grzbietu,
         * jak w prawdziwej książce).
         */
        fun group(count: Int, book: Boolean, coverAlone: Boolean, rtl: Boolean): List<List<Int?>> {
            if (count <= 0) return emptyList()
            if (!book) return (0 until count).map { listOf(it) }
            val rows = ArrayList<List<Int?>>()
            var i = 0
            if (coverAlone) {
                rows += listOf(null, 0)
                i = 1
            }
            while (i < count) {
                rows += if (i + 1 < count) listOf(i, i + 1) else listOf(i, null)
                i += 2
            }
            return if (rtl) rows.map { it.reversed() } else rows
        }

        fun build(aspects: List<Float>, viewportW: Float, viewportH: Float, spec: LayoutSpec): DocLayout {
            val groups = group(aspects.size, spec.book, spec.coverAlone, spec.rtl)
            val rows = ArrayList<Row>(groups.size)
            val pageRow = IntArray(aspects.size)
            val availW = max(1f, viewportW - 2 * spec.margin)
            val availH = max(1f, viewportH - 2 * spec.margin - spec.topInset)
            val vertical = spec.scroll == ScrollMode.VERTICAL
            var cursor = if (vertical) spec.margin + spec.topInset else 0f
            for ((ri, g) in groups.withIndex()) {
                // Puste miejsce ma proporcje sąsiedniej strony – grzbiet zostaje na środku.
                val real = g.filterNotNull()
                val fallback = real.firstOrNull()?.let { aspects[it] } ?: 0.707f
                val slotAspects = g.map { p -> p?.let { aspects[it] } ?: fallback }
                val rowAspect = slotAspects.sum()
                val fitW = availW / rowAspect
                val rowH = if (vertical && spec.fit == FitMode.WIDTH) fitW else min(fitW, availH)
                val rowW = rowH * rowAspect
                val left: Float
                val top: Float
                if (vertical) {
                    left = (viewportW - rowW) / 2f
                    top = cursor
                    cursor += rowH + spec.gap
                } else {
                    left = ri * viewportW + (viewportW - rowW) / 2f
                    top = spec.topInset + (viewportH - spec.topInset - rowH) / 2f
                }
                var x = left
                val slots = ArrayList<Slot>(2)
                for ((si, p) in g.withIndex()) {
                    val w = rowH * slotAspects[si]
                    if (p != null) {
                        slots += Slot(p, LRect(x, top, x + w, top + rowH))
                        pageRow[p] = ri
                    }
                    x += w
                }
                val spine = if (g.size == 2) left + rowH * slotAspects[0] else null
                rows += Row(ri, LRect(left, top, left + rowW, top + rowH), slots, spine)
            }
            val width: Float
            val height: Float
            if (vertical) {
                width = viewportW
                // Krótszy niż ekran dokument zostaje wyśrodkowany przy ograniczaniu przesunięcia.
                height = max(1f, cursor - spec.gap + spec.margin)
            } else {
                width = groups.size * viewportW
                height = viewportH
            }
            return DocLayout(rows, width, height, spec, viewportW, viewportH, pageRow)
        }
    }
}
