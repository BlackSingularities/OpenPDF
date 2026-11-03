@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.DocTab
import com.blacksingularities.openpdf.desktop.Flow
import com.blacksingularities.openpdf.desktop.Paper
import com.blacksingularities.openpdf.desktop.TextSelection
import com.blacksingularities.openpdf.desktop.ZoomMode
import com.blacksingularities.openpdf.pdf.LinkInfo
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.RectN
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/** Punkty PDF na piksel przy powiększeniu 100% (96 dpi). */
const val PT_TO_DP = 96f / 72f

val ZOOM_STEPS = floatArrayOf(0.1f, 0.25f, 0.33f, 0.5f, 0.67f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f, 5f, 6f, 8f)

enum class Tool { SELECT, HAND }

/** Stan widoku potrzebny poza nim (skróty klawiszowe, pasek narzędzi). */
class ViewerHandle {
    var viewportH by mutableStateOf(0f)
    var tool by mutableStateOf(Tool.SELECT)
    var viewport by mutableStateOf(Rect.Zero)
}

private fun paperFilter(p: Paper): ColorFilter? = when (p) {
    Paper.NORMAL -> null
    // Odwrócenie z lekko obniżonym kontrastem – czytanie w nocy.
    Paper.NIGHT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.84f, 0f, 0f, 0f, 232f,
                0f, -0.84f, 0f, 0f, 232f,
                0f, 0f, -0.84f, 0f, 228f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    Paper.SEPIA -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.72f, 0f, 0f, 0f, 62f,
                0f, 0.74f, 0f, 0f, 48f,
                0f, 0f, 0.73f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

private fun paperColor(p: Paper) = when (p) {
    Paper.NORMAL -> Color.White
    Paper.NIGHT -> Color(0xFF1B1B1C)
    Paper.SEPIA -> Color(0xFFF4ECD8)
}

/** Rozmiar strony po obrocie widoku (punkty). */
private fun DocTab.pageSize(p: Int): Size {
    val s = renderer?.sizes?.getOrNull(p) ?: return Size(612f, 792f)
    return if (rotation % 180 != 0) Size(s.height, s.width) else Size(s.width, s.height)
}

/**
 * Geometria wierszy w punktach. Strony jednej rozkładówki dostają wspólną wysokość (jak
 * w otwartej książce), więc różne formaty stron nie „schodkują”.
 */
private class RowGeo(rows: List<IntArray>, size: (Int) -> Size) {
    val disp = HashMap<Int, Size>()
    val rowW = FloatArray(rows.size)
    val rowH = FloatArray(rows.size)

    init {
        rows.forEachIndexed { i, r ->
            val h = r.maxOf { size(it).height }.coerceAtLeast(1f)
            var w = 0f
            for (p in r) {
                val s = size(p)
                val d = Size(s.width * h / s.height.coerceAtLeast(1f), h)
                disp[p] = d
                w += d.width
            }
            rowW[i] = w
            rowH[i] = h
        }
    }

    val maxW = rowW.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val maxH = rowH.maxOrNull()?.coerceAtLeast(1f) ?: 1f
}

@Composable
fun Viewer(app: AppState, tab: DocTab, handle: ViewerHandle, presentation: Boolean) {
    val density = LocalDensity.current.density
    val bg = if (presentation) Color.Black else P.canvas
    BoxWithConstraints(Modifier.fillMaxSize().background(bg).clipToBounds().onGloballyPositioned { handle.viewport = it.boundsInWindow() }) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        SideEffect { handle.viewportH = vh }
        val pad = (if (presentation) 0f else 20f) * density
        val gap = 16f * density
        val bar = 14f * density
        val rows = remember(tab.layout, tab.pageCount) { tab.rows() }
        val geo = remember(rows, tab.rotation, tab.renderer) { RowGeo(rows) { tab.pageSize(it) } }
        val paged = presentation || tab.flow == Flow.PAGED
        val mode = if (presentation) ZoomMode.FIT_PAGE else tab.zoomMode
        val scale = when (mode) {
            ZoomMode.FIT_PAGE -> min((vw - 2 * pad - (if (paged) 0f else bar)) / geo.maxW, (vh - 2 * pad) / geo.maxH)
            ZoomMode.FIT_WIDTH -> (vw - 2 * pad - bar) / geo.maxW
            ZoomMode.CUSTOM -> tab.zoom * PT_TO_DP * density
        }.coerceAtLeast(0.02f)
        LaunchedEffect(scale, density) { tab.effectiveZoom = scale / (PT_TO_DP * density) }

        val zoomScope = rememberCoroutineScope()
        // Ctrl+kółko: powiększanie wokół kursora.
        val wheelZoom = Modifier.onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
            if (!e.keyboardModifiers.isCtrlPressed) return@onPointerEvent
            val ch = e.changes.firstOrNull() ?: return@onPointerEvent
            val dy = ch.scrollDelta.y
            if (dy == 0f) return@onPointerEvent
            e.changes.forEach { it.consume() }
            val old = tab.effectiveZoom
            val next = (old * if (dy < 0) 1.12f else 1 / 1.12f).coerceIn(0.1f, 8f)
            val k = next / old
            val anchor = ch.position
            tab.zoomMode = ZoomMode.CUSTOM
            tab.zoom = next
            if (!paged) {
                val idx = tab.list.firstVisibleItemIndex
                val off = tab.list.firstVisibleItemScrollOffset
                zoomScope.launch {
                    tab.list.scrollToItem(idx, ((off + anchor.y) * k - anchor.y).roundToInt().coerceAtLeast(0))
                    tab.hscroll.scrollTo(((tab.hscroll.value + anchor.x) * k - anchor.x).roundToInt().coerceAtLeast(0))
                }
            }
        }

        CompositionLocalProvider(
            LocalScrollbarStyle provides ScrollbarStyle(
                minimalHeight = 32.dp, thickness = 8.dp, shape = RoundedCornerShape(8.dp), hoverDurationMillis = 250,
                unhoverColor = P.text.copy(alpha = 0.18f), hoverColor = P.text.copy(alpha = 0.38f),
            ),
        ) {
            if (paged) {
                PagedView(app, tab, handle, rows, geo, scale, pad, vw, vh, presentation, Modifier.fillMaxSize().then(wheelZoom))
            } else {
                ContinuousView(app, tab, handle, rows, geo, scale, pad, gap, vw, Modifier.fillMaxSize().then(wheelZoom))
            }
        }
    }
}

@Composable
private fun ContinuousView(
    app: AppState,
    tab: DocTab,
    handle: ViewerHandle,
    rows: List<IntArray>,
    geo: RowGeo,
    scale: Float,
    pad: Float,
    gap: Float,
    vw: Float,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    val contentW = max(vw, geo.maxW * scale + 2 * pad)
    // Bieżąca strona: wiersz na środku okna.
    LaunchedEffect(tab, rows) {
        snapshotFlow {
            val info = tab.list.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val item = info.visibleItemsInfo.firstOrNull { it.offset <= mid && it.offset + it.size + gap >= mid }
                ?: info.visibleItemsInfo.firstOrNull()
            item?.index
        }.distinctUntilChanged().collect { ri ->
            if (ri != null && ri in rows.indices && tab.nav == null) {
                val row = rows[ri]
                if (tab.page !in row) tab.page = row.first()
                app.saveSoon()
            }
        }
    }
    // Przejście do strony na żądanie (spis treści, wyszukiwanie, klawisze).
    LaunchedEffect(tab.nav, rows, scale) {
        val req = tab.nav ?: return@LaunchedEffect
        val ri = tab.rowOf(req.page).coerceIn(0, rows.size - 1)
        val off = req.yFrac?.let { (it * geo.rowH[ri] * scale - 60 * density).roundToInt().coerceAtLeast(0) } ?: 0
        val target = tab.list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == ri }
        if (req.animate && target != null) {
            tab.list.animateScrollBy((target.offset + off).toFloat() - pad)
        } else {
            tab.list.scrollToItem(ri, off)
        }
        tab.nav = null
    }
    Box(modifier) {
        Box(Modifier.fillMaxSize().horizontalScroll(tab.hscroll)) {
            LazyColumn(
                state = tab.list,
                modifier = Modifier.width((contentW / density).dp).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = (pad / density).dp),
                verticalArrangement = Arrangement.spacedBy((gap / density).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(rows.size, key = { "${tab.layout}-${rows[it].first()}" }) { ri ->
                    Spread(app, tab, handle, rows[ri], geo, scale, shadow = true)
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(tab.list), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
        if (contentW > vw + 1) {
            HorizontalScrollbar(rememberScrollbarAdapter(tab.hscroll), Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(2.dp))
        }
    }
}

@Composable
private fun PagedView(
    app: AppState,
    tab: DocTab,
    handle: ViewerHandle,
    rows: List<IntArray>,
    geo: RowGeo,
    scale: Float,
    pad: Float,
    vw: Float,
    vh: Float,
    presentation: Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    val ri = tab.rowOf(tab.page).coerceIn(0, max(0, rows.size - 1))
    LaunchedEffect(tab.nav) { if (tab.nav != null) tab.nav = null }
    LaunchedEffect(ri) { app.saveSoon() }
    var lastFlip by remember { mutableStateOf(0L) }
    fun flip(d: Int) {
        val now = System.currentTimeMillis()
        if (now - lastFlip < 220) return
        lastFlip = now
        tab.step(d)
    }
    Box(
        modifier.then(
            if (presentation) Modifier.onPointerEvent(PointerEventType.Press) { e ->
                if (e.button == PointerButton.Secondary) flip(-1) else if (e.button == PointerButton.Primary) flip(1)
            } else Modifier,
        ),
    ) {
        AnimatedContent(
            targetState = ri,
            transitionSpec = {
                if (!app.settings.smoothScroll) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val d = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(220)) { it / 8 * d } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it / 8 * d } + fadeOut(tween(160)))
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "page",
        ) { r ->
            val vs = rememberScrollState()
            val hs = rememberScrollState()
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .onPointerEvent(PointerEventType.Scroll) { e ->
                            if (e.keyboardModifiers.isCtrlPressed || e.changes.any { it.isConsumed }) return@onPointerEvent
                            val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy == 0f) return@onPointerEvent
                            if ((dy > 0 && vs.value >= vs.maxValue) || (dy < 0 && vs.value <= 0)) flip(dy.sign.toInt())
                        }
                        .verticalScroll(vs)
                        .horizontalScroll(hs),
                ) {
                    // Najmniej tyle, co okno – rozkładówka zawsze na środku.
                    Box(
                        Modifier.sizeIn(minWidth = (vw / density).dp, minHeight = (vh / density).dp).padding((pad / density).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (r in rows.indices) Spread(app, tab, handle, rows[r], geo, scale, shadow = !presentation)
                    }
                }
                if (!presentation && vs.maxValue > 0) {
                    VerticalScrollbar(rememberScrollbarAdapter(vs), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
                }
                if (!presentation && hs.maxValue > 0) {
                    HorizontalScrollbar(rememberScrollbarAdapter(hs), Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(2.dp))
                }
            }
        }
    }
}

/** Wiersz widoku: jedna strona albo rozkładówka – strony stykają się grzbietem, wspólny cień. */
@Composable
private fun Spread(app: AppState, tab: DocTab, handle: ViewerHandle, row: IntArray, geo: RowGeo, scale: Float, shadow: Boolean) {
    val pages = if (tab.rtl && row.size == 2) row.reversedArray() else row
    val two = row.size == 2
    Row(if (shadow) Modifier.shadow(if (two) 10.dp else 6.dp, RectangleShape, clip = false) else Modifier) {
        pages.forEachIndexed { i, p ->
            val s = geo.disp[p] ?: tab.pageSize(p)
            val w = (s.width * scale).roundToInt().coerceAtLeast(1)
            val h = (s.height * scale).roundToInt().coerceAtLeast(1)
            PageView(app, tab, handle, p, w, h, spine = if (!two) 0 else if (i == 0) 1 else -1)
        }
    }
}

private data class Tile(val image: ImageBitmap, val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Jedna strona: obraz (z filtrem papieru i obrotem widoku), ostry fragment przy dużym
 * powiększeniu, wyniki wyszukiwania, zaznaczenie tekstu i odnośniki.
 * [spine]: 1 – grzbiet po prawej, -1 – po lewej, 0 – bez grzbietu.
 */
@Composable
private fun PageView(app: AppState, tab: DocTab, handle: ViewerHandle, page: Int, boxW: Int, boxH: Int, spine: Int) {
    val density = LocalDensity.current.density
    val rot = tab.rotation
    val quarter = rot % 180 != 0
    val imgW = if (quarter) boxH else boxW
    val imgH = if (quarter) boxW else boxH
    val img = pageImage(tab, page, imgW, imgH)
    val paper = app.settings.paper
    val filter = remember(paper) { paperFilter(paper) }

    // Ostry fragment strony, gdy pełny obraz byłby za duży.
    var tile by remember(tab.id, page) { mutableStateOf<Tile?>(null) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val needsTile = rot == 0 && imgW.toLong() * imgH > PageCache.MAX_PIXELS
    if (needsTile) {
        LaunchedEffect(bounds, handle.viewport, imgW) {
            val vis = bounds.intersect(handle.viewport)
            if (vis.width <= 0f || vis.height <= 0f) return@LaunchedEffect
            delay(160)
            val x0 = (vis.left - bounds.left).roundToInt().coerceAtLeast(0)
            val y0 = (vis.top - bounds.top).roundToInt().coerceAtLeast(0)
            val w = vis.width.roundToInt().coerceAtLeast(1)
            val h = vis.height.roundToInt().coerceAtLeast(1)
            val r = tab.renderer ?: return@LaunchedEffect
            val s = imgW / (r.sizes.getOrNull(page)?.width ?: return@LaunchedEffect)
            val bmp = r.render(page, w, h, floatArrayOf(s, 0f, 0f, s, -x0.toFloat(), -y0.toFloat())) ?: return@LaunchedEffect
            tile = Tile(bmp, x0, y0, w, h)
        }
    } else if (tile != null) {
        tile = null
    }

    var hoverLink by remember { mutableStateOf<LinkInfo?>(null) }
    val links = tab.index.links.getOrNull(page).orEmpty()
    val hits = tab.hits.filter { it.page == page }
    val current = tab.hits.getOrNull(tab.hitIndex)?.takeIf { it.page == page }
    val sel = tab.selection?.takeIf { it.page == page }
    var text by remember(tab.id, page) { mutableStateOf(tab.pageTexts[page]) }
    LaunchedEffect(tab.id, page, handle.tool) {
        if (text == null && handle.tool == Tool.SELECT) {
            delay(250)
            text = tab.pageText(page)
        }
    }

    fun toN(o: Offset): PointN {
        val x = o.x / boxW
        val y = o.y / boxH
        return when (rot) {
            90 -> PointN(y, 1 - x)
            180 -> PointN(1 - x, 1 - y)
            270 -> PointN(1 - y, x)
            else -> PointN(x, y)
        }
    }

    fun fromN(r: RectN): Rect {
        fun pt(x: Float, y: Float): Offset = when (rot) {
            90 -> Offset((1 - y) * boxW, x * boxH)
            180 -> Offset((1 - x) * boxW, (1 - y) * boxH)
            270 -> Offset(y * boxW, (1 - x) * boxH)
            else -> Offset(x * boxW, y * boxH)
        }
        val a = pt(r.left, r.top)
        val b = pt(r.right, r.bottom)
        return Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    val scope = rememberCoroutineScope()
    val p = P
    Box(
        Modifier
            .size((boxW / density).dp, (boxH / density).dp)
            .background(paperColor(paper))
            .onGloballyPositioned { c -> bounds = Rect(c.positionInWindow(), Size(boxW.toFloat(), boxH.toFloat())) }
            .pointerHoverIcon(
                when {
                    hoverLink != null -> PointerIcon.Hand
                    handle.tool == Tool.HAND -> PointerIcon.Hand
                    (text?.length ?: 0) > 0 -> PointerIcon.Text
                    else -> PointerIcon.Default
                },
            )
            .onPointerEvent(PointerEventType.Move) { e ->
                val pos = e.changes.firstOrNull()?.position ?: return@onPointerEvent
                val n = toN(pos)
                hoverLink = links.firstOrNull { it.rect.contains(n, 0.002f) }
            }
            .onPointerEvent(PointerEventType.Exit) { hoverLink = null }
            .pointerInput(tab.id, page, rot, handle.tool, boxW, boxH) {
                awaitPointerEventScope {
                    var clicks = 0
                    var lastClick = 0L
                    while (true) {
                        val down = awaitPointerEvent()
                        if (down.type != PointerEventType.Press) continue
                        val ch = down.changes.firstOrNull() ?: continue
                        val start = ch.position
                        val middle = down.buttons.isTertiaryPressed
                        if (!down.buttons.isPrimaryPressed && !middle) continue
                        val pan = handle.tool == Tool.HAND || middle
                        val now = System.currentTimeMillis()
                        clicks = if (now - lastClick < 400) clicks + 1 else 1
                        lastClick = now
                        val n0 = toN(start)
                        val link = links.firstOrNull { it.rect.contains(n0, 0.002f) }
                        val t = text
                        val anchor = if (!pan && t != null) t.nearest(n0, 0.03f) else null
                        if (!pan && t != null && anchor != null && clicks >= 2) {
                            // Dwuklik – słowo, trzy kliknięcia – cała linia.
                            val r = if (clicks == 2) t.wordAt(anchor) else lineAt(t.text, anchor)
                            tab.selection = TextSelection(page, r.first, r.last)
                        } else if (!pan && clicks == 1) {
                            tab.selection = null
                        }
                        var moved = false
                        var last = start
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull() ?: break
                            if (e.type == PointerEventType.Release || !c.pressed) break
                            val d = c.position - last
                            if ((c.position - start).getDistance() > 4f) moved = true
                            last = c.position
                            if (pan) {
                                tab.list.dispatchRawDelta(-d.y)
                                scope.launch { tab.hscroll.scrollBy(-d.x) }
                                c.consume()
                            } else if (t != null && anchor != null && moved && clicks == 1) {
                                val f = t.nearest(toN(c.position)) ?: continue
                                tab.selection = TextSelection(page, anchor, f)
                                c.consume()
                            }
                        }
                        if (!moved && link != null && !pan && clicks == 1) openLink(tab, link)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            img?.let { drawPage(it, rot, boxW, boxH, filter) }
            tile?.let { t ->
                if (needsTile) drawImage(t.image, dstOffset = IntOffset(t.x, t.y), dstSize = IntSize(t.w, t.h), colorFilter = filter, filterQuality = FilterQuality.Medium)
            }
            if (spine != 0) {
                // Zagięcie przy grzbiecie książki.
                val w = size.width * 0.07f
                val dark = Color.Black.copy(alpha = if (paper == Paper.NIGHT) 0.5f else 0.18f)
                if (spine > 0) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, dark), startX = size.width - w, endX = size.width), Offset(size.width - w, 0f), Size(w, size.height))
                } else {
                    drawRect(Brush.horizontalGradient(listOf(dark, Color.Transparent), startX = 0f, endX = w), Offset.Zero, Size(w, size.height))
                }
            }
            for (h in hits) {
                val strong = h === current
                for (r in h.rects) {
                    val rr = fromN(r.inflate(0.002f))
                    drawRoundRect(if (strong) p.highlightCurrent else p.highlight, rr.topLeft, rr.size, CornerRadius(2f, 2f), blendMode = BlendMode.Multiply)
                }
            }
            val t = text
            if (sel != null && t != null) {
                for (r in t.rects(sel.range)) {
                    val rr = fromN(r)
                    drawRect(p.selection, rr.topLeft, rr.size)
                }
            }
            hoverLink?.let { l ->
                val rr = fromN(l.rect)
                drawRect(p.selection.copy(alpha = 0.12f), rr.topLeft, rr.size)
            }
        }
    }
}

private fun lineAt(s: String, i: Int): IntRange {
    var a = i
    var b = i
    while (a > 0 && s[a - 1] != '\n') a--
    while (b < s.length - 1 && s[b + 1] != '\n') b++
    return a..b
}

private fun openLink(tab: DocTab, l: LinkInfo) {
    val target = l.targetPage
    if (target != null) {
        tab.goTo(target)
        return
    }
    val uri = l.uri ?: return
    val scheme = uri.substringBefore(':').lowercase()
    if (scheme in setOf("http", "https", "mailto")) runCatching { Desktop.getDesktop().browse(URI(uri)) }
}

private fun DrawScope.drawPage(img: ImageBitmap, rot: Int, boxW: Int, boxH: Int, filter: ColorFilter?) {
    val quarter = rot % 180 != 0
    val uw = if (quarter) boxH else boxW
    val uh = if (quarter) boxW else boxH
    rotate(rot.toFloat(), Offset(boxW / 2f, boxH / 2f)) {
        drawImage(
            img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(((boxW - uw) / 2f).roundToInt(), ((boxH - uh) / 2f).roundToInt()),
            dstSize = IntSize(uw, uh),
            colorFilter = filter,
            filterQuality = if (abs(img.width - uw) <= 1) FilterQuality.None else FilterQuality.High,
        )
    }
}

/** Przesunięcie widoku klawiszami (ciągłe przewijanie). */
suspend fun DocTab.scrollByAnimated(px: Float, smooth: Boolean) {
    if (smooth) list.animateScrollBy(px, tween(160)) else list.scrollBy(px)
}
