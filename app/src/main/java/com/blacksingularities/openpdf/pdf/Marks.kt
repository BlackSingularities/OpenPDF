package com.blacksingularities.openpdf.pdf

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Oczekujące (jeszcze niezapisane w pliku) elementy narysowane na stronach. Współrzędne
 * znormalizowane względem wyświetlanej strony, grubości i rozmiary – w punktach PDF.
 * Przy zapisie stają się prawdziwymi adnotacjami PDF z wyglądem, widocznymi w każdym czytniku.
 */
sealed interface Mark {
    val id: Long
    val page: Int
    val color: Int

    /** Obrys elementu (znormalizowany). */
    fun bounds(): RectN

    fun moved(dx: Float, dy: Float): Mark

    /** Czy punkt [p] trafia w element; [tol] – tolerancja w jednostkach znormalizowanych. */
    fun hit(p: PointN, tol: Float): Boolean = bounds().contains(p, tol)
}

/** Pióro albo zakreślacz: zbiór pociągnięć. */
data class InkMark(
    override val id: Long,
    override val page: Int,
    val strokes: List<List<PointN>>,
    override val color: Int,
    val widthPt: Float,
    val highlighter: Boolean,
    /** Szerokość wyświetlanej strony w punktach – do przeliczenia grubości na jednostki znormalizowane. */
    val pageWidthPt: Float,
) : Mark {
    override fun bounds(): RectN {
        val r = RectN.bounding(strokes.flatten()) ?: RectN(0f, 0f, 0f, 0f)
        return r.inflate(widthPt / pageWidthPt / 2f)
    }

    override fun moved(dx: Float, dy: Float) = copy(strokes = strokes.map { s -> s.map { PointN(it.x + dx, it.y + dy) } })

    override fun hit(p: PointN, tol: Float): Boolean {
        if (!bounds().contains(p, tol)) return false
        val t = tol + widthPt / pageWidthPt / 2f
        for (s in strokes) {
            if (s.size == 1 && hypot(s[0].x - p.x, s[0].y - p.y) <= t) return true
            for (i in 1 until s.size) if (distanceToSegment(p, s[i - 1], s[i]) <= t) return true
        }
        return false
    }
}

enum class ShapeKind { RECT, ELLIPSE, LINE, ARROW }

data class ShapeMark(
    override val id: Long,
    override val page: Int,
    val kind: ShapeKind,
    val start: PointN,
    val end: PointN,
    override val color: Int,
    val widthPt: Float,
    /** Kolor wypełnienia (ARGB) albo null. */
    val fill: Int?,
    val pageWidthPt: Float,
) : Mark {
    override fun bounds(): RectN = RectN.of(start, end).inflate(widthPt / pageWidthPt * (if (kind == ShapeKind.ARROW) 3f else 0.5f))

    override fun moved(dx: Float, dy: Float) = copy(start = PointN(start.x + dx, start.y + dy), end = PointN(end.x + dx, end.y + dy))

    override fun hit(p: PointN, tol: Float): Boolean {
        val t = tol + widthPt / pageWidthPt
        return when (kind) {
            ShapeKind.LINE, ShapeKind.ARROW -> distanceToSegment(p, start, end) <= t
            ShapeKind.RECT, ShapeKind.ELLIPSE -> {
                val r = RectN.of(start, end)
                if (fill != null) r.contains(p, t) else r.contains(p, t) && !r.inflate(-t * 2).contains(p)
            }
        }
    }
}

/** Pole tekstowe (FreeText). [rect] obejmuje tekst; rozmiar czcionki w punktach. */
data class TextMark(
    override val id: Long,
    override val page: Int,
    val rect: RectN,
    val text: String,
    override val color: Int,
    val sizePt: Float,
    /** Tło pola (ARGB) albo null. */
    val background: Int?,
) : Mark {
    override fun bounds() = rect
    override fun moved(dx: Float, dy: Float) = copy(rect = rect.offset(dx, dy))
}

/** Notatka (ikona dymku z treścią widoczną po dotknięciu). */
data class NoteMark(
    override val id: Long,
    override val page: Int,
    val at: PointN,
    val text: String,
    override val color: Int,
    /** Rozmiar ikony w jednostkach znormalizowanych (szerokość). */
    val sizeN: Float,
    val aspect: Float,
) : Mark {
    override fun bounds() = RectN(at.x, at.y, at.x + sizeN, at.y + sizeN * aspect)
    override fun moved(dx: Float, dy: Float) = copy(at = PointN(at.x + dx, at.y + dy))
}

enum class MarkupKind { HIGHLIGHT, UNDERLINE, STRIKEOUT }

/** Zaznaczenie fragmentu tekstu: zakreślenie, podkreślenie albo przekreślenie. */
data class MarkupMark(
    override val id: Long,
    override val page: Int,
    val kind: MarkupKind,
    val rects: List<RectN>,
    override val color: Int,
    val text: String,
) : Mark {
    override fun bounds() = rects.reduce { a, b -> a.union(b) }
    override fun moved(dx: Float, dy: Float) = this
    override fun hit(p: PointN, tol: Float) = rects.any { it.contains(p, tol) }
}

/** Obraz albo podpis (pociągnięcia w lokalnym układzie 0..1 prostokąta). */
data class ImageMark(
    override val id: Long,
    override val page: Int,
    val rect: RectN,
    /** Ścieżka do pliku PNG w katalogu sesji. */
    val path: String,
    override val color: Int = 0,
) : Mark {
    override fun bounds() = rect
    override fun moved(dx: Float, dy: Float) = copy(rect = rect.offset(dx, dy))
}

data class SignatureMark(
    override val id: Long,
    override val page: Int,
    val rect: RectN,
    /** Pociągnięcia w układzie 0..1 względem [rect]. */
    val strokes: List<List<PointN>>,
    override val color: Int,
    val widthPt: Float,
) : Mark {
    override fun bounds() = rect
    override fun moved(dx: Float, dy: Float) = copy(rect = rect.offset(dx, dy))
}

/** Zmiana rozmiaru elementów prostokątnych (obraz, podpis, tekst) – prawy dolny róg w [corner]. */
fun Mark.resized(corner: PointN, keepAspect: Boolean, pageAspect: Float): Mark {
    fun fit(r: RectN): RectN {
        val minSize = 0.02f
        var w = max(minSize, corner.x - r.left)
        var h = max(minSize, corner.y - r.top)
        if (keepAspect && r.width > 0f && r.height > 0f) {
            // Proporcje w punktach: wysokość znormalizowana zależy od proporcji strony.
            val ratio = r.height / r.width
            h = w * ratio
        }
        return RectN(r.left, r.top, r.left + w, r.top + h)
    }
    return when (this) {
        is ImageMark -> copy(rect = fit(rect))
        is SignatureMark -> copy(rect = fit(rect))
        is TextMark -> {
            val r = fit(rect)
            val scale = if (rect.height > 0f) r.height / rect.height else 1f
            copy(rect = RectN(r.left, r.top, rect.left + rect.width * scale, r.bottom), sizePt = (sizePt * scale).coerceIn(4f, 144f))
        }
        else -> this
    }
}

val Mark.resizable: Boolean get() = this is ImageMark || this is SignatureMark || this is TextMark

fun distanceToSegment(p: PointN, a: PointN, b: PointN): Float {
    val vx = b.x - a.x
    val vy = b.y - a.y
    val len2 = vx * vx + vy * vy
    if (len2 < 1e-12f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / len2).coerceIn(0f, 1f)
    return hypot(p.x - (a.x + t * vx), p.y - (a.y + t * vy))
}

/**
 * Upraszcza pociągnięcie (Ramer–Douglas–Peucker) – mniej punktów w pliku, ta sama linia.
 * [epsilon] w jednostkach znormalizowanych.
 */
fun simplify(points: List<PointN>, epsilon: Float): List<PointN> {
    if (points.size < 3) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.size - 1)
    while (stack.isNotEmpty()) {
        val (s, e) = stack.removeLast()
        var maxD = 0f
        var idx = -1
        for (i in s + 1 until e) {
            val d = distanceToSegment(points[i], points[s], points[e])
            if (d > maxD) {
                maxD = d
                idx = i
            }
        }
        if (idx >= 0 && maxD > epsilon) {
            keep[idx] = true
            stack.addLast(s to idx)
            stack.addLast(idx to e)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

/** Czy dwa kolory ARGB są praktycznie jednakowe. */
fun sameColor(a: Int, b: Int) = abs(a - b) == 0
