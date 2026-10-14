package com.blacksingularities.openpdf.pdf

import kotlin.math.max
import kotlin.math.min

/** Punkt w znormalizowanych współrzędnych wyświetlanej strony: (0,0) lewy górny róg, (1,1) prawy dolny. */
data class PointN(val x: Float, val y: Float) {
    operator fun plus(o: PointN) = PointN(x + o.x, y + o.y)
    operator fun minus(o: PointN) = PointN(x - o.x, y - o.y)
}

/** Prostokąt w znormalizowanych współrzędnych wyświetlanej strony. */
data class RectN(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    fun contains(p: PointN, slop: Float = 0f) =
        p.x >= left - slop && p.x <= right + slop && p.y >= top - slop && p.y <= bottom + slop

    fun offset(dx: Float, dy: Float) = RectN(left + dx, top + dy, right + dx, bottom + dy)

    fun union(o: RectN) = RectN(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    fun inflate(d: Float) = RectN(left - d, top - d, right + d, bottom + d)

    fun intersects(o: RectN) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    companion object {
        fun of(a: PointN, b: PointN) = RectN(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

        fun bounding(points: Iterable<PointN>): RectN? {
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            var any = false
            for (p in points) {
                any = true
                l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
            }
            return if (any) RectN(l, t, r, b) else null
        }
    }
}

/**
 * Geometria strony PDF: widoczny obszar (CropBox) i obrót. Przelicza punkty między układem
 * wyświetlanej strony (znormalizowanym, z początkiem w lewym górnym rogu, po obrocie)
 * a przestrzenią użytkownika PDF (punkty, początek w lewym dolnym rogu, bez obrotu).
 */
data class PageGeo(
    val llx: Float,
    val lly: Float,
    val width: Float,
    val height: Float,
    /** Obrót strony zgodnie z ruchem wskazówek: 0, 90, 180 albo 270. */
    val rotation: Int,
) {
    val quarter: Boolean get() = rotation % 180 != 0

    /** Szerokość wyświetlanej (obróconej) strony w punktach. */
    val displayWidth: Float get() = if (quarter) height else width
    val displayHeight: Float get() = if (quarter) width else height

    /** Punkt wyświetlanej strony (znormalizowany) → przestrzeń użytkownika PDF. */
    fun toPdf(p: PointN): Pair<Float, Float> {
        val dx = p.x * displayWidth
        val dy = p.y * displayHeight
        // Współrzędne nieobróconej strony (początek w lewym górnym rogu, oś Y w dół).
        val (x, y) = when (rotation) {
            90 -> dy to height - dx
            180 -> width - dx to height - dy
            270 -> width - dy to dx
            else -> dx to dy
        }
        return (llx + x) to (lly + height - y)
    }

    /** Punkt przestrzeni użytkownika PDF → znormalizowany punkt wyświetlanej strony. */
    fun fromPdf(px: Float, py: Float): PointN {
        val x = px - llx
        val y = height - (py - lly)
        val (dx, dy) = when (rotation) {
            90 -> (height - y) to x
            180 -> (width - x) to (height - y)
            270 -> y to (width - x)
            else -> x to y
        }
        return PointN(dx / displayWidth, dy / displayHeight)
    }

    /** Prostokąt wyświetlanej strony → prostokąt PDF (lewy, dolny, prawy, górny). */
    fun rectToPdf(r: RectN): FloatArray {
        val (x1, y1) = toPdf(PointN(r.left, r.top))
        val (x2, y2) = toPdf(PointN(r.right, r.bottom))
        return floatArrayOf(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }

    /** Prostokąt PDF → znormalizowany prostokąt wyświetlanej strony. */
    fun rectFromPdf(l: Float, b: Float, r: Float, t: Float): RectN = RectN.of(fromPdf(l, b), fromPdf(r, t))

    /** Skala: ile punktów PDF przypada na jednostkę szerokości wyświetlanej strony. */
    val pointsPerUnitX: Float get() = displayWidth

    /**
     * Macierz (a, b, c, d, e, f) przenosząca treść strony do „prostej” strony [0, displayWidth] ×
     * [0, displayHeight] bez obrotu – wypiekanie obrotu i przycięcia w kopii do wyświetlania.
     */
    fun normalizingMatrix(): FloatArray = when (rotation) {
        90 -> floatArrayOf(0f, -1f, 1f, 0f, -lly, width + llx)
        180 -> floatArrayOf(-1f, 0f, 0f, -1f, width + llx, height + lly)
        270 -> floatArrayOf(0f, 1f, -1f, 0f, height + lly, -llx)
        else -> floatArrayOf(1f, 0f, 0f, 1f, -llx, -lly)
    }

    /** Kąt (w stopniach, przeciwnie do ruchu wskazówek), o jaki trzeba obrócić tekst, by na ekranie stał prosto. */
    val uprightAngle: Int get() = rotation
}

fun normalizeRotation(r: Int): Int = ((r % 360) + 360) % 360 / 90 * 90
