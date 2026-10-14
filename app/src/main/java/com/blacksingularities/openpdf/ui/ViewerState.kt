package com.blacksingularities.openpdf.ui

import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.RectN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Położenie i powiększenie widoku dokumentu. Cała treść (wszystkie rzędy stron) to jeden
 * obraz: punkt treści p trafia na ekran w p · [scale] + [offset]. Dzięki temu rozkładówka
 * powiększa się i przesuwa jak jedna ilustracja.
 */
@Stable
class ViewerState(private val scope: CoroutineScope) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var layout by mutableStateOf<DocLayout?>(null)
        private set
    var viewport by mutableStateOf(Size.Zero)
    var interacting by mutableStateOf(false)
    /** Bieżąca rozkładówka w trybie stronicowania (poziomo). */
    var pagedRow by mutableIntStateOf(0)
        private set

    private var anim: Job? = null

    /** Przycięcie marginesów każdej strony: lewy, górny, prawy, dolny (ułamki). */
    var crops: List<FloatArray> = emptyList()

    fun cropOf(page: Int): FloatArray = crops.getOrNull(page) ?: NO_CROP

    /** Prostokąt całej strony [page], gdy w [visible] mieści się tylko jej przycięty fragment. */
    fun full(page: Int, visible: LRect): LRect {
        val crop = cropOf(page)
        val w = visible.width / (1f - crop[0] - crop[2])
        val h = visible.height / (1f - crop[1] - crop[3])
        val left = visible.left - crop[0] * w
        val top = visible.top - crop[1] * h
        return LRect(left, top, left + w, top + h)
    }

    fun full(slot: Slot): LRect = full(slot.page, slot.rect)

    /** Cała strona na ekranie (z przyciętymi marginesami poza widocznym miejscem). */
    fun pageScreen(slot: Slot): Rect = toScreen(full(slot))

    val paged: Boolean get() = layout?.spec?.scroll == ScrollMode.PAGED
    val minScale: Float get() = if (paged) 1f else 0.35f
    val maxScale = 12f

    fun setLayout(l: DocLayout, anchorPage: Int?) {
        stop()
        val old = layout
        // Tylko zmiana rozmiaru widoku (np. pasek narzędzi schowany/pokazany) – zachowujemy
        // powiększenie i punkt na środku ekranu.
        if (old != null && old.rows.size == l.rows.size && old.spec.copy(margin = 0f, gap = 0f) == l.spec.copy(margin = 0f, gap = 0f) &&
            l.spec.scroll == ScrollMode.VERTICAL && old.rows.isNotEmpty() && old.rows[0].rect.height > 0f
        ) {
            val k = l.rows[0].rect.height / old.rows[0].rect.height
            val cx = (old.viewportW / 2f - offset.x) / scale * k
            val cy = (old.viewportH / 2f - offset.y) / scale * k
            layout = l
            offset = clamped(scale, Offset(l.viewportW / 2f - cx * scale, l.viewportH / 2f - cy * scale))
            return
        }
        layout = l
        scale = 1f
        offset = Offset.Zero
        if (anchorPage != null) scrollToPage(anchorPage, null, animate = false) else clamp()
    }

    fun stop() {
        anim?.cancel()
        anim = null
    }

    fun toScreen(r: LRect) = Rect(r.left * scale + offset.x, r.top * scale + offset.y, r.right * scale + offset.x, r.bottom * scale + offset.y)

    fun toContent(p: Offset) = Offset((p.x - offset.x) / scale, (p.y - offset.y) / scale)

    /** Rzędy widoczne na ekranie, z zapasem [extra] pikseli ekranu. */
    fun visibleRows(extra: Float = 0f): List<Row> {
        val l = layout ?: return emptyList()
        return if (l.spec.scroll == ScrollMode.VERTICAL) {
            l.rowsBetween((-offset.y - extra) / scale, (viewport.height - offset.y + extra) / scale)
        } else {
            l.rowsBetween((-offset.x - extra) / scale, (viewport.width - offset.x + extra) / scale)
        }
    }

    /** Strona pod punktem ekranu i punkt na niej (znormalizowany). */
    fun hit(pos: Offset, slop: Float = 0f): Pair<Slot, PointN>? {
        val c = toContent(pos)
        val s = slop / scale
        for (row in visibleRows()) for (slot in row.slots) {
            val r = slot.rect
            if (c.x >= r.left - s && c.x <= r.right + s && c.y >= r.top - s && c.y <= r.bottom + s) {
                val f = full(slot)
                return slot to PointN((c.x - f.left) / f.width, (c.y - f.top) / f.height)
            }
        }
        return null
    }

    /** Punkt ekranu względem konkretnej strony (bez ograniczania do strony). */
    fun pointIn(slot: Slot, pos: Offset): PointN {
        val c = toContent(pos)
        val f = full(slot)
        return PointN((c.x - f.left) / f.width, (c.y - f.top) / f.height)
    }

    fun currentPage(): Int {
        val l = layout ?: return 0
        if (l.rows.isEmpty()) return 0
        val row = if (l.spec.scroll == ScrollMode.PAGED) {
            val idx = floor((viewport.width / 2f - offset.x) / (viewport.width * scale)).toInt()
            l.rows.getOrNull(idx.coerceIn(0, l.rows.size - 1))
        } else {
            // Rząd przecinający linię na 30% wysokości ekranu (albo najbliższy).
            val y = (viewport.height * 0.3f - offset.y) / scale
            l.rowsBetween(y, y).firstOrNull() ?: l.rowsBetween(y - viewport.height / scale, y).lastOrNull() ?: l.rows.first()
        }
        return row?.slots?.firstOrNull()?.page ?: 0
    }

    // ------------------------------------------------------------------ ograniczanie

    private fun clamped(s: Float, o: Offset): Offset {
        val l = layout ?: return o
        val vw = viewport.width
        val vh = viewport.height
        val cw = l.width * s
        val ch = l.height * s
        val x = if (l.spec.scroll == ScrollMode.PAGED && s > 1.01f) {
            val row = pagedRow.coerceIn(0, max(0, l.rows.size - 1))
            o.x.coerceIn(vw - (row + 1) * vw * s, -row * vw * s)
        } else if (cw <= vw) {
            (vw - cw) / 2f
        } else {
            o.x.coerceIn(vw - cw, 0f)
        }
        val y = if (ch <= vh) (vh - ch) / 2f else o.y.coerceIn(vh - ch, 0f)
        return Offset(x, y)
    }

    fun clamp() {
        offset = clamped(scale, offset)
    }

    fun panBy(d: Offset) {
        offset = clamped(scale, offset + d)
    }

    fun transformBy(centroid: Offset, zoom: Float, pan: Offset) {
        val s = (scale * zoom).coerceIn(minScale, maxScale)
        val k = s / scale
        val o = (offset - centroid) * k + centroid + pan
        scale = s
        offset = clamped(s, o)
    }

    // ------------------------------------------------------------------ animacje

    private fun animateTo(targetScale: Float, targetOffset: Offset, duration: Int = 260) {
        stop()
        val s0 = scale
        val o0 = offset
        val o1 = clamped(targetScale, targetOffset)
        anim = scope.launch {
            animate(0f, 1f, animationSpec = tween(duration)) { t, _ ->
                scale = s0 + (targetScale - s0) * t
                offset = Offset(o0.x + (o1.x - o0.x) * t, o0.y + (o1.y - o0.y) * t)
            }
            offset = clamped(scale, offset)
        }
    }

    fun zoomAt(pos: Offset, target: Float) {
        val s = target.coerceIn(minScale, maxScale)
        val o = (offset - pos) * (s / scale) + pos
        animateTo(s, o)
    }

    /** Dwukrotne dotknięcie: przybliżenie w miejscu palca albo powrót do dopasowania. */
    fun doubleTap(pos: Offset) {
        if (scale < 1.6f) zoomAt(pos, 2.5f) else zoomAt(pos, 1f)
    }

    fun fling(velocity: Offset) {
        stop()
        if (paged && scale <= 1.01f) {
            snapPaged(velocity.x)
            return
        }
        anim = scope.launch {
            coroutineScope {
                if (abs(velocity.x) > 50f) launch {
                    var last = 0f
                    animateDecay(0f, velocity.x, FloatExponentialDecaySpec(frictionMultiplier = 1.4f)) { v, _ ->
                        panBy(Offset(v - last, 0f))
                        last = v
                    }
                }
                if (abs(velocity.y) > 50f) launch {
                    var last = 0f
                    animateDecay(0f, velocity.y, FloatExponentialDecaySpec(frictionMultiplier = 1.4f)) { v, _ ->
                        panBy(Offset(0f, v - last))
                        last = v
                    }
                }
            }
        }
    }

    /** Stronicowanie: dosunięcie do najbliższej rozkładówki (z uwzględnieniem prędkości). */
    fun snapPaged(vx: Float = 0f) {
        val l = layout ?: return
        if (l.rows.isEmpty()) return
        val pos = -offset.x / (viewport.width * scale)
        val target = when {
            vx < -400f -> ceil(pos).toInt()
            vx > 400f -> floor(pos).toInt()
            else -> pos.roundToInt()
        }.coerceIn(0, l.rows.size - 1)
        pagedRow = target
        animateTo(1f, Offset(-target * viewport.width, offset.y), 220)
    }

    /** Przewija do strony; z [region] – środek fragmentu (np. wyniku wyszukiwania) na środek ekranu. */
    fun scrollToPage(page: Int, region: RectN?, animate: Boolean) {
        val l = layout ?: return
        val row = l.rowOf(page) ?: return
        val slot = l.slotOf(page) ?: return
        if (l.spec.scroll == ScrollMode.PAGED) {
            val sameRow = row.index == pagedRow && scale > 1.01f
            pagedRow = row.index
            val s = if (sameRow) scale else 1f
            var o = Offset(-row.index * viewport.width * s, offset.y)
            if (region != null && s > 1.01f) {
                val pf = full(slot)
                val cx = pf.left + region.centerX * pf.width
                val cy = pf.top + region.centerY * pf.height
                o = Offset(viewport.width / 2f - cx * s, viewport.height / 2f - cy * s)
            }
            if (animate) animateTo(s, o, 220) else {
                stop()
                scale = s
                offset = clamped(s, o)
            }
            return
        }
        val target = if (region != null) {
            val pf = full(slot)
            val cx = pf.left + region.centerX * pf.width
            val cy = pf.top + region.centerY * pf.height
            Offset(viewport.width / 2f - cx * scale, viewport.height / 2f - cy * scale)
        } else {
            Offset(offset.x, -(row.rect.top - l.spec.margin - l.spec.topInset) * scale)
        }
        val distance = abs(clamped(scale, target).y - offset.y)
        if (animate && distance < viewport.height * 4f) animateTo(scale, target, 280) else {
            stop()
            offset = clamped(scale, target)
        }
    }

    /** Klawisze głośności / strefy dotyku: następny lub poprzedni ekran tekstu. */
    fun step(direction: Int) {
        val l = layout ?: return
        if (l.rows.isEmpty()) return
        if (canFlip()) {
            if (flip == null) startFlip(direction > 0)?.let { completeFlip(it) }
            return
        }
        if (l.spec.scroll == ScrollMode.PAGED) {
            val target = (pagedRow + direction).coerceIn(0, l.rows.size - 1)
            pagedRow = target
            animateTo(1f, Offset(-target * viewport.width, offset.y), 220)
            return
        }
        val vh = viewport.height
        val rows = visibleRows()
        if (direction > 0) {
            val first = rows.firstOrNull { it.rect.bottom * scale + offset.y > vh * 0.15f } ?: return
            val bottom = first.rect.bottom * scale + offset.y
            val target = if (bottom > vh + 1f) {
                offset.y - vh * 0.9f
            } else {
                val next = l.rows.getOrNull(first.index + 1) ?: return
                -(next.rect.top - l.spec.margin - l.spec.topInset) * scale
            }
            animateTo(scale, Offset(offset.x, target), 240)
        } else {
            val first = rows.firstOrNull { it.rect.bottom * scale + offset.y > 1f } ?: return
            val top = first.rect.top * scale + offset.y
            val target = if (top < -1f) {
                offset.y + min(vh * 0.9f, -top + l.spec.margin * scale)
            } else {
                val prev = l.rows.getOrNull(first.index - 1) ?: return
                -(prev.rect.top - l.spec.margin - l.spec.topInset) * scale
            }
            animateTo(scale, Offset(offset.x, target), 240)
        }
    }

    // ------------------------------------------------------------------ przewracanie kartek

    /**
     * Przewracana kartka: z rozkładówki [from] na [to]. [forward] – do następnej rozkładówki.
     * [progress] 0..1: kartka obraca się wokół grzbietu (0 – leży, 1 – przewrócona).
     */
    @Stable
    class Flip(val from: Int, val to: Int, val forward: Boolean) {
        var progress by mutableFloatStateOf(0f)
    }

    var flip by mutableStateOf<Flip?>(null)
        private set

    /** Ustawiane przez widok: tryb książki z przewracaniem stron i włączona animacja. */
    var flipEnabled = false

    fun canFlip(): Boolean {
        val l = layout ?: return false
        return flipEnabled && l.spec.scroll == ScrollMode.PAGED && scale <= 1.01f && l.rows.size > 1
    }

    private fun startFlip(forward: Boolean): Flip? {
        val l = layout ?: return null
        val target = pagedRow + if (forward) 1 else -1
        if (target !in l.rows.indices) return null
        stop()
        // Na czas animacji widok stoi na bieżącej rozkładówce.
        offset = Offset(-pagedRow * viewport.width, offset.y)
        return Flip(pagedRow, target, forward).also { flip = it }
    }

    private fun completeFlip(f: Flip, velocity: Float = 0f) {
        stop()
        anim = scope.launch {
            val remaining = 1f - f.progress
            animate(f.progress, 1f, initialVelocity = velocity, animationSpec = tween((520 * remaining).toInt().coerceAtLeast(120))) { v, _ ->
                f.progress = v
            }
            pagedRow = f.to
            offset = clamped(1f, Offset(-f.to * viewport.width, offset.y))
            flip = null
        }
    }

    private fun cancelFlip(f: Flip) {
        stop()
        anim = scope.launch {
            animate(f.progress, 0f, animationSpec = tween((380 * f.progress).toInt().coerceAtLeast(100))) { v, _ -> f.progress = v }
            flip = null
        }
    }

    /** Przeciąganie palcem: [dx] – przesunięcie od początku gestu (w pikselach). */
    fun flipDrag(dx: Float) {
        val rtl = layout?.spec?.rtl == true
        var f = flip
        if (f == null) {
            if (abs(dx) < 4f) return
            // Przesunięcie w lewo = dalej (w kolejności od prawej do lewej – odwrotnie).
            val forward = (dx < 0) != rtl
            f = startFlip(forward) ?: return
        }
        val towards = if (f.forward != rtl) -dx else dx
        f.progress = (towards / (viewport.width * 0.75f)).coerceIn(0f, 1f)
    }

    fun flipRelease(vx: Float) {
        val f = flip ?: return
        val rtl = layout?.spec?.rtl == true
        val v = if (f.forward != rtl) -vx else vx
        if (f.progress > 0.33f || v > 800f) completeFlip(f, (v / viewport.width).coerceIn(0f, 6f)) else cancelFlip(f)
    }

    /** Pionowy suwak szybkiego przewijania: 0..1. */
    val scrollFraction: Float
        get() {
            val l = layout ?: return 0f
            val ch = l.height * scale
            if (ch <= viewport.height) return 0f
            return (-offset.y / (ch - viewport.height)).coerceIn(0f, 1f)
        }

    fun scrollToFraction(f: Float) {
        val l = layout ?: return
        stop()
        val ch = l.height * scale
        offset = clamped(scale, Offset(offset.x, -(ch - viewport.height) * f.coerceIn(0f, 1f)))
    }
}

private val NO_CROP = floatArrayOf(0f, 0f, 0f, 0f)
