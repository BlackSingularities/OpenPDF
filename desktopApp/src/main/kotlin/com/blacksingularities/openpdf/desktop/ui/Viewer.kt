@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import com.blacksingularities.openpdf.desktop.PageLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.DocTab
import com.blacksingularities.openpdf.desktop.Flow
import com.blacksingularities.openpdf.desktop.INK_COLORS
import com.blacksingularities.openpdf.desktop.InputRequest
import com.blacksingularities.openpdf.desktop.MARKER_COLORS
import com.blacksingularities.openpdf.desktop.Paper
import com.blacksingularities.openpdf.desktop.SideTab
import com.blacksingularities.openpdf.desktop.TextSelection
import com.blacksingularities.openpdf.desktop.Tool
import com.blacksingularities.openpdf.desktop.ToolStyles
import com.blacksingularities.openpdf.desktop.ZoomMode
import com.blacksingularities.openpdf.pdf.AnnotInfo
import com.blacksingularities.openpdf.pdf.ImageMark
import com.blacksingularities.openpdf.pdf.InkMark
import com.blacksingularities.openpdf.pdf.LinkInfo
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.MarkupMark
import com.blacksingularities.openpdf.pdf.NoteMark
import com.blacksingularities.openpdf.pdf.PageText
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.RectN
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.pdf.ShapeMark
import com.blacksingularities.openpdf.pdf.TEXT_LEADING
import com.blacksingularities.openpdf.pdf.TEXT_PAD
import com.blacksingularities.openpdf.pdf.TextMark
import com.blacksingularities.openpdf.pdf.simplify
import com.blacksingularities.openpdf.ui.ToolStyle
import com.blacksingularities.openpdf.ui.makeShape
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

/** Stan widoku potrzebny poza nim (skróty klawiszowe, pasek narzędzi). */
class ViewerHandle {
    var viewportH by mutableStateOf(0f)
    var viewport by mutableStateOf(Rect.Zero)
    /** Trwa przeciąganie zaznaczenia tekstu (pasek nad zaznaczeniem jest wtedy ukryty). */
    var selecting by mutableStateOf(false)
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
    // Przewracanie kartek jak w książce: rozkładówki (dwie strony, książka) mieszczące się w oknie.
    val flipOn = app.settings.pageFlip && tab.layout != PageLayout.SINGLE
    fun fits(r: Int) = r in rows.indices && geo.rowW[r] * scale <= vw - 2 * pad + 1f && geo.rowH[r] * scale <= vh - 2 * pad + 1f
    var shown by remember(tab.id, tab.layout) { mutableStateOf(ri) }
    var turning by remember(tab.id, tab.layout) { mutableStateOf<FlipAnim?>(null) }
    if (!flipOn && shown != ri) shown = ri
    LaunchedEffect(ri, flipOn) {
        if (!flipOn) return@LaunchedEffect
        // Poprzednia kartka jeszcze w ruchu – kolejna zaczyna się od jej docelowej rozkładówki.
        val from = turning?.to ?: shown
        turning = null
        shown = from
        if (ri == from) return@LaunchedEffect
        if (abs(ri - from) != 1 || !fits(from) || !fits(ri)) {
            shown = ri
            return@LaunchedEffect
        }
        val f = FlipAnim(from, ri, ri > from)
        turning = f
        animate(0f, 1f, animationSpec = tween(620, easing = FastOutSlowInEasing)) { v, _ -> f.progress = v }
        if (turning === f) {
            shown = ri
            turning = null
        }
    }
    val page: @Composable (Int) -> Unit = { r ->
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
    Box(
        modifier.then(
            if (presentation) Modifier.onPointerEvent(PointerEventType.Press) { e ->
                if (e.button == PointerButton.Secondary) flip(-1) else if (e.button == PointerButton.Primary) flip(1)
            } else Modifier,
        ),
    ) {
        val f = turning
        if (flipOn && f != null) {
            FlipView(app, tab, rows, geo, scale, vw, vh, f, shadow = !presentation)
        } else if (flipOn) {
            key(shown) { page(shown) }
        } else {
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
            ) { r -> page(r) }
        }
    }
}

/** Przewracana kartka: z rozkładówki [from] na [to]; [progress] 0..1 – obrót wokół grzbietu. */
private class FlipAnim(val from: Int, val to: Int, val forward: Boolean) {
    var progress by mutableFloatStateOf(0f)
}

/**
 * Położenie stron wiersza na ekranie podczas przewracania. Rozkładówka leży jak w widoku
 * (wyśrodkowana, grzbiet między stronami); pojedyncza strona staje przy grzbiecie – okładka
 * po prawej, ostatnia strona po lewej (w kolejności od prawej do lewej odwrotnie).
 */
private fun flipRects(tab: DocTab, row: IntArray, first: Boolean, geo: RowGeo, scale: Float, vh: Float, spine: Float): List<Pair<Int, Rect>> {
    val pages = if (tab.rtl && row.size == 2) row.reversedArray() else row
    val sizes = pages.map { p -> (geo.disp[p] ?: tab.pageSize(p)).let { Size(it.width * scale, it.height * scale) } }
    val h = sizes.maxOf { it.height }
    var x = when {
        pages.size == 2 -> spine - sizes[0].width
        first != tab.rtl -> spine
        else -> spine - sizes[0].width
    }
    return pages.mapIndexed { i, p ->
        val sz = sizes[i]
        val top = (vh - h) / 2f + (h - sz.height) / 2f
        val r = Rect(x, top, x + sz.width, top + sz.height)
        x += sz.width
        p to r
    }
}

/** Widok w trakcie przewracania kartki: strony leżące płasko i kartka obracana w perspektywie. */
@Composable
private fun FlipView(app: AppState, tab: DocTab, rows: List<IntArray>, geo: RowGeo, scale: Float, vw: Float, vh: Float, f: FlipAnim, shadow: Boolean) {
    val fromRow = rows.getOrNull(f.from) ?: return
    val toRow = rows.getOrNull(f.to) ?: return
    // Grzbiet z rozkładówki (wyśrodkowanej w oknie); same pojedyncze strony – środek okna.
    val spreadIdx = listOf(f.from, f.to).firstOrNull { rows[it].size == 2 }
    val spine = if (spreadIdx != null) {
        val r = rows[spreadIdx]
        val pages = if (tab.rtl) r.reversedArray() else r
        (vw - geo.rowW[spreadIdx] * scale) / 2f + (geo.disp[pages[0]]?.width ?: tab.pageSize(pages[0]).width) * scale
    } else vw / 2f
    val fromR = flipRects(tab, fromRow, f.from == 0, geo, scale, vh, spine)
    val toR = flipRects(tab, toRow, f.to == 0, geo, scale, vh, spine)
    val rot = tab.rotation
    val images = HashMap<Int, ImageBitmap?>()
    for ((p, r) in fromR + toR) {
        key(p) {
            val w = r.width.roundToInt().coerceAtLeast(1)
            val h = r.height.roundToInt().coerceAtLeast(1)
            images[p] = if (rot % 180 != 0) pageImage(tab, p, h, w) else pageImage(tab, p, w, h)
        }
    }
    val paper = app.settings.paper
    val filter = remember(paper) { paperFilter(paper) }
    Canvas(Modifier.fillMaxSize()) {
        drawFlip(f, fromR, toR, spine, images, rot, paper, filter, tab.rtl, shadow)
    }
}

private fun black(a: Float) = Color.Black.copy(alpha = a.coerceIn(0f, 1f))

private fun DrawScope.pageInto(img: ImageBitmap?, r: Rect, rot: Int, paper: Paper, filter: ColorFilter?) {
    drawRect(paperColor(paper), r.topLeft, r.size)
    if (img != null) translate(r.left, r.top) { drawPage(img, rot, r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1), filter) }
}

/** Zagięcie przy grzbiecie jak w [PageView]; [spineRight] – grzbiet przy prawej krawędzi strony, [k] – siła. */
private fun DrawScope.gutterShade(r: Rect, spineRight: Boolean, paper: Paper, k: Float) {
    if (k <= 0.01f) return
    val w = r.width * 0.07f
    val dark = black((if (paper == Paper.NIGHT) 0.5f else 0.18f) * k)
    if (spineRight) {
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, dark), startX = r.right - w, endX = r.right), Offset(r.right - w, r.top), Size(w, r.height))
    } else {
        drawRect(Brush.horizontalGradient(listOf(dark, Color.Transparent), startX = r.left, endX = r.left + w), r.topLeft, Size(w, r.height))
    }
}

/**
 * Przewracanie kartki jak w książce (ta sama geometria co na Androidzie): kartka obraca się
 * w przestrzeni wokół grzbietu, z perspektywą – unosząc się, rośnie ku patrzącemu, a za połową
 * obrotu widać jej drugą stronę. Kartka ustawiona bokiem do światła ciemnieje i rzuca miękki
 * cień na stronę, nad którą przechodzi.
 */
private fun DrawScope.drawFlip(
    f: FlipAnim,
    fromR: List<Pair<Int, Rect>>,
    toR: List<Pair<Int, Rect>>,
    spine: Float,
    images: Map<Int, ImageBitmap?>,
    rot: Int,
    paper: Paper,
    filter: ColorFilter?,
    rtl: Boolean,
    shadow: Boolean,
) {
    fun side(rs: List<Pair<Int, Rect>>, left: Boolean) = rs.firstOrNull { (_, r) -> if (left) r.center.x < spine else r.center.x >= spine }
    // Kartka odchodzi od prawej krawędzi (do przodu) albo od lewej (wstecz); w RTL odwrotnie.
    val fromRight = f.forward != rtl
    val t = f.progress.coerceIn(0f, 1f)
    val theta = PI.toFloat() * t
    val staticSide = side(fromR, left = fromRight)
    val revealed = side(toR, left = !fromRight)
    val front = side(fromR, left = !fromRight)
    val back = side(toR, left = fromRight)
    val flats = listOfNotNull(staticSide, revealed)
    if (shadow) {
        for ((_, r) in flats) drawRect(black(0.10f), Offset(r.left - 2f, r.top + 2f), Size(r.width + 4f, r.height + 4f))
    }
    for ((p, r) in flats) {
        pageInto(images[p], r, rot, paper, filter)
        gutterShade(r, spineRight = r.center.x < spine, paper = paper, k = 1f)
    }
    val firstHalf = t < 0.5f
    val leaf = (if (firstHalf) front else back) ?: return
    val (leafPage, flat) = leaf
    val s = if (flat.center.x >= spine) 1f else -1f
    // Kąt kartki od jej płaskiego położenia: 0 – leży, π/2 – stoi pionowo.
    val a = if (firstHalf) theta else PI.toFloat() - theta
    val cosA = cos(a)
    val sinA = sin(a)
    val w = flat.width
    val camera = w * 5f
    val k = sinA / camera
    // Rzut zewnętrznej krawędzi kartki na ekran.
    val edgeX = spine + s * w * cosA / (1f - w * k)
    // Strona pod kartką dostaje miękki cień – tym szerszy i mocniejszy, im wyżej uniesiona kartka.
    val beneath = if (firstHalf) revealed else staticSide
    if (beneath != null) {
        val b = beneath.second
        val shadowW = w * (0.05f + 0.32f * sinA)
        val alpha = 0.38f * sinA * (2f - sinA)
        val x1 = edgeX + s * shadowW
        val l = min(edgeX, x1).coerceIn(b.left, b.right)
        val r = max(edgeX, x1).coerceIn(b.left, b.right)
        if (r > l && alpha > 0.005f) {
            drawRect(
                Brush.horizontalGradient(
                    0f to black(alpha),
                    0.25f to black(alpha * 0.55f),
                    0.55f to black(alpha * 0.2f),
                    1f to Color.Transparent,
                    startX = edgeX,
                    endX = x1,
                ),
                Offset(l, b.top),
                Size(r - l, b.height),
            )
        }
    }
    // Kartka w perspektywie: punkt (x, y) płaskiej strony (względem grzbietu i środka wysokości)
    // trafia w (x·cos a, y) / w, gdzie w = 1 − d·sin a / D (d – odległość od grzbietu, D – kamera).
    val cy = flat.center.y
    val local = Rect(flat.left - spine, flat.top - cy, flat.right - spine, flat.bottom - cy)
    val m = Matrix(
        floatArrayOf(
            cosA, 0f, 0f, -s * k,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        ),
    )
    withTransform({
        translate(spine, cy)
        transform(m)
    }) {
        pageInto(images[leafPage], local, rot, paper, filter)
        // Światło: kartka ustawiona bokiem ciemnieje, mocniej przy zewnętrznej krawędzi.
        val shade = 0.36f * (1f - cosA)
        val fold = shade * 0.45f + 0.05f * sinA
        drawRect(
            Brush.horizontalGradient(0f to black(fold), 0.35f to black(shade * 0.75f), 1f to black(shade), startX = 0f, endX = s * w),
            local.topLeft,
            local.size,
        )
        // Cieniowanie przy grzbiecie wygaszane płynnie wraz z uniesieniem kartki.
        gutterShade(local, spineRight = s < 0f, paper = paper, k = cosA * cosA)
        // Cienka krawędź kartki – oddziela ją od strony pod spodem.
        drawLine(black(0.2f * sinA), Offset(s * w, local.top), Offset(s * w, local.bottom), 1.2f)
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
 * powiększeniu, wyniki wyszukiwania, zaznaczenie tekstu, odnośniki, pola formularza oraz
 * niezapisane adnotacje i narzędzia rysowania.
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
    val tool = app.tool
    val styles = app.styles
    val pageWpt = tab.pageWidthPt(page)
    val pageHpt = tab.pageHeightPt(page)

    // Ostry fragment strony, gdy pełny obraz byłby za duży.
    var tile by remember(tab.id, page, tab.revision) { mutableStateOf<Tile?>(null) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val needsTile = rot == 0 && imgW.toLong() * imgH > PageCache.MAX_PIXELS
    if (needsTile) {
        LaunchedEffect(bounds, handle.viewport, imgW, tab.revision) {
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
    var hoverErase by remember { mutableStateOf<RectN?>(null) }
    val links = tab.index.links.getOrNull(page).orEmpty()
    val hits = tab.hits.filter { it.page == page }
    val current = tab.hits.getOrNull(tab.hitIndex)?.takeIf { it.page == page }
    val sel = tab.selection?.takeIf { it.page == page }
    val pageMarks = tab.marks.filter { it.page == page }
    val showFields = app.settings.sidebar && app.settings.sideTab == SideTab.FORM
    val widgets = if (showFields) tab.index.fields.flatMap { f -> f.widgets.filter { it.page == page }.map { f to it.rect } } else emptyList()
    var text by remember(tab.id, page, tab.revision) { mutableStateOf(tab.pageTexts[page]) }
    LaunchedEffect(tab.id, page, tool, tab.revision) {
        if (text == null && (tool == Tool.SELECT || tool == Tool.MARKUP)) {
            delay(250)
            text = tab.pageText(page)
        }
    }
    // Rysowanie na żywo: pociągnięcie pióra albo kształt.
    val live = remember { mutableStateListOf<PointN>() }
    var liveShape by remember { mutableStateOf<ShapeMark?>(null) }

    fun toN(o: Offset): PointN {
        val x = (o.x / boxW).coerceIn(0f, 1f)
        val y = (o.y / boxH).coerceIn(0f, 1f)
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

    /** Element pod punktem do wymazania: niezapisany (id) albo adnotacja z pliku. */
    fun eraseTarget(n: PointN): Pair<Mark?, AnnotInfo?> {
        val tol = 6f / boxW.coerceAtLeast(1)
        // Bieżące listy (nie te z chwili uruchomienia obsługi myszy).
        val m = tab.marks.lastOrNull { it.page == page && it.hit(n, tol) }
        if (m != null) return m to null
        val a = tab.index.annots.getOrNull(page).orEmpty().lastOrNull { it.rect.contains(n, tol) }
        return null to a
    }

    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val p = P
    Box(
        Modifier
            .size((boxW / density).dp, (boxH / density).dp)
            .background(paperColor(paper))
            .onGloballyPositioned { c -> bounds = Rect(c.positionInWindow(), Size(boxW.toFloat(), boxH.toFloat())) }
            .pointerHoverIcon(
                when {
                    tool == Tool.PEN || tool == Tool.MARKER || tool == Tool.SHAPE || tool == Tool.NOTE -> PointerIcon.Crosshair
                    tool == Tool.ERASER -> if (hoverErase != null) PointerIcon.Hand else PointerIcon.Crosshair
                    tool == Tool.TEXT -> PointerIcon.Text
                    hoverLink != null -> PointerIcon.Hand
                    tool == Tool.HAND -> PointerIcon.Hand
                    (text?.length ?: 0) > 0 -> PointerIcon.Text
                    else -> PointerIcon.Default
                },
            )
            .onPointerEvent(PointerEventType.Move) { e ->
                val pos = e.changes.firstOrNull()?.position ?: return@onPointerEvent
                val n = toN(pos)
                hoverLink = if (tool.annotates) null else links.firstOrNull { it.rect.contains(n, 0.002f) }
                hoverErase = if (tool == Tool.ERASER) eraseTarget(n).let { (m, a) -> m?.bounds() ?: a?.rect } else null
            }
            .onPointerEvent(PointerEventType.Exit) {
                hoverLink = null
                hoverErase = null
            }
            .pointerInput(tab.id, page, rot, tool, boxW, boxH, tab.revision) {
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
                        val pan = tool == Tool.HAND || middle
                        val now = System.currentTimeMillis()
                        clicks = if (now - lastClick < 400) clicks + 1 else 1
                        lastClick = now
                        val n0 = toN(start)
                        val canEdit = tab.editable

                        // Rączka i środkowy przycisk: przesuwanie widoku.
                        if (pan) {
                            var last = start
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull() ?: break
                                if (e.type == PointerEventType.Release || !c.pressed) break
                                val d = c.position - last
                                last = c.position
                                tab.list.dispatchRawDelta(-d.y)
                                scope.launch { tab.hscroll.scrollBy(-d.x) }
                                c.consume()
                            }
                            continue
                        }

                        when (tool) {
                            Tool.PEN, Tool.MARKER -> {
                                if (!canEdit) continue
                                ch.consume()
                                live.clear()
                                live += n0
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull() ?: break
                                    if (e.type == PointerEventType.Release || !c.pressed) break
                                    val n = toN(c.position)
                                    val lastN = live.last()
                                    if (abs(n.x - lastN.x) + abs(n.y - lastN.y) > 0.0015f) live += n
                                    c.consume()
                                }
                                val pts = simplify(live.toList(), 0.0007f)
                                live.clear()
                                val marker = tool == Tool.MARKER
                                tab.addMark(
                                    InkMark(
                                        tab.newMarkId(), page, listOf(pts),
                                        if (marker) styles.markerColor else styles.penColor,
                                        if (marker) styles.markerWidth else styles.penWidth,
                                        marker, pageWpt,
                                    ),
                                )
                            }
                            Tool.SHAPE -> {
                                if (!canEdit) continue
                                ch.consume()
                                val style = ToolStyle(styles.shapeColor, styles.shapeWidth, styles.shapeFill)
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull() ?: break
                                    if (e.type == PointerEventType.Release || !c.pressed) break
                                    liveShape = makeShape(0, page, styles.shapeKind, n0, toN(c.position), style, pageWpt)
                                    c.consume()
                                }
                                val shape = liveShape
                                liveShape = null
                                if (shape != null && abs(shape.end.x - shape.start.x) + abs(shape.end.y - shape.start.y) > 0.01f) {
                                    tab.addMark(shape.copy(id = tab.newMarkId()))
                                }
                            }
                            Tool.TEXT, Tool.NOTE -> {
                                if (!canEdit) continue
                                ch.consume()
                                while (true) {
                                    val e = awaitPointerEvent()
                                    if (e.type == PointerEventType.Release || e.changes.none { it.pressed }) break
                                }
                                if (tool == Tool.TEXT) {
                                    app.ask(
                                        InputRequest(L("Pole tekstowe", "Text box"), multiline = true, hint = L("Treść pola", "Text"), confirm = L("Wstaw", "Insert")) { value ->
                                            if (value.isBlank()) return@InputRequest
                                            val size = styles.textSize
                                            val lines = value.split('\n')
                                            val wPt = lines.maxOf { it.length } * size * 0.56f + 2 * TEXT_PAD + size * 0.4f
                                            val hPt = lines.size * size * TEXT_LEADING + 2 * TEXT_PAD + size * 0.25f
                                            val r = RectN(n0.x, n0.y, (n0.x + wPt / pageWpt).coerceAtMost(1f), (n0.y + hPt / pageHpt).coerceAtMost(1f))
                                            tab.addMark(TextMark(tab.newMarkId(), page, r, value, styles.textColor, size, if (styles.textBackground) 0xFFFFFFFF.toInt() else null))
                                        },
                                    )
                                } else {
                                    app.ask(
                                        InputRequest(L("Notatka", "Note"), multiline = true, hint = L("Treść notatki", "Note text"), confirm = L("Dodaj", "Add")) { value ->
                                            if (value.isBlank()) return@InputRequest
                                            tab.addMark(NoteMark(tab.newMarkId(), page, n0, value, styles.noteColor, 22f / pageWpt, pageWpt / pageHpt))
                                        },
                                    )
                                }
                            }
                            Tool.ERASER -> {
                                ch.consume()
                                val (m, a) = eraseTarget(n0)
                                when {
                                    m != null -> tab.removeMark(m.id)
                                    a != null && canEdit -> tab.deleteAnnotation(a)
                                }
                                // Przeciąganie gumką usuwa kolejne niezapisane elementy.
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull() ?: break
                                    if (e.type == PointerEventType.Release || !c.pressed) break
                                    val n = toN(c.position)
                                    val tol = 6f / boxW.coerceAtLeast(1)
                                    tab.marks.lastOrNull { it.page == page && it.hit(n, tol) }?.let { tab.removeMark(it.id) }
                                    c.consume()
                                }
                            }
                            else -> {
                                // Zaznaczanie tekstu (i wyróżnianie – po puszczeniu przycisku).
                                val link = if (tool == Tool.SELECT) links.firstOrNull { it.rect.contains(n0, 0.002f) } else null
                                val field = if (tool == Tool.SELECT && app.settings.sidebar && app.settings.sideTab == SideTab.FORM) {
                                    tab.index.fields.firstOrNull { f -> f.widgets.any { it.page == page && it.rect.contains(n0, 0.002f) } }
                                } else null
                                val t = text
                                val anchor = if (t != null) t.nearest(n0, 0.03f) else null
                                if (t != null && anchor != null && clicks >= 2) {
                                    // Dwuklik – słowo, trzy kliknięcia – cała linia.
                                    val r = if (clicks == 2) t.wordAt(anchor) else lineAt(t.text, anchor)
                                    tab.selection = TextSelection(page, r.first, r.last)
                                } else if (clicks == 1) {
                                    tab.selection = null
                                }
                                var moved = false
                                handle.selecting = true
                                try {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val c = e.changes.firstOrNull() ?: break
                                        if (e.type == PointerEventType.Release || !c.pressed) break
                                        if ((c.position - start).getDistance() > 4f) moved = true
                                        if (t != null && anchor != null && moved && clicks == 1) {
                                            val f = t.nearest(toN(c.position)) ?: continue
                                            tab.selection = TextSelection(page, anchor, f)
                                            c.consume()
                                        }
                                    }
                                } finally {
                                    handle.selecting = false
                                }
                                val s = tab.selection
                                if (tool == Tool.MARKUP && t != null && s != null && s.page == page && canEdit) {
                                    addMarkup(tab, t, s, styles.markupKind, styles.markupColor)
                                } else if (!moved && clicks == 1) {
                                    if (field != null) {
                                        app.focusField = field.name
                                    } else if (link != null) {
                                        openLink(tab, link)
                                    }
                                }
                            }
                        }
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
            for ((f, r) in widgets) {
                val rr = fromN(r)
                val focused = f.name == app.focusField
                drawRect(p.accent.copy(alpha = if (focused) 0.30f else 0.14f), rr.topLeft, rr.size)
                drawRect(p.accent.copy(alpha = 0.7f), rr.topLeft, rr.size, style = Stroke(if (focused) 2f else 1f))
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
            drawMarks(pageMarks, live, styles, tool, liveShape, rot, boxW, boxH, pageWpt, measurer, density)
            hoverErase?.let { r ->
                val rr = fromN(r.inflate(0.004f))
                drawRect(p.err.copy(alpha = 0.12f), rr.topLeft, rr.size)
                drawRect(p.err, rr.topLeft, rr.size, style = Stroke(1.5f))
            }
        }
        // Pasek nad zaznaczonym tekstem: kopiowanie i wyróżnianie.
        val t = text
        if (sel != null && t != null && tool == Tool.SELECT && !handle.selecting) {
            val rects = t.rects(sel.range)
            val first = rects.firstOrNull()
            if (first != null) {
                val rr = fromN(first)
                val x = (rr.left / density).coerceIn(0f, (boxW / density - 190f).coerceAtLeast(0f))
                val y = (rr.top / density - 40f).let { if (it < 0f) (fromN(rects.last()).bottom / density + 6f) else it }
                SelectionBar(app, tab, t, sel, Modifier.offset(x.dp, y.dp))
            }
        }
    }
}

/** Zakreślenie, podkreślenie albo przekreślenie zaznaczonego tekstu (jako niezapisany element). */
private fun addMarkup(tab: DocTab, t: PageText, s: TextSelection, kind: MarkupKind, color: Int) {
    val rects = t.rects(s.range)
    if (rects.isEmpty()) return
    tab.addMark(MarkupMark(tab.newMarkId(), s.page, kind, rects, color, t.substring(s.range)))
    tab.selection = null
}

@Composable
private fun SelectionBar(app: AppState, tab: DocTab, t: PageText, s: TextSelection, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.shadow(10.dp, shape).background(P.raised, shape).border(1.dp, P.line2, shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Rounded.ContentCopy, L("Kopiuj", "Copy"), {
            copyToClipboard(t.substring(s.range))
            app.showToast(L("Skopiowano tekst", "Text copied"))
        }, shortcut = "Ctrl+C", size = 28.dp)
        if (tab.editable) {
            val hl = if (app.styles.markupColor in MARKER_COLORS) app.styles.markupColor else MARKER_COLORS[0]
            IconBtn(Icons.Rounded.BorderColor, L("Zakreśl", "Highlight"), { addMarkup(tab, t, s, MarkupKind.HIGHLIGHT, hl) }, size = 28.dp, tint = Color(hl))
            IconBtn(Icons.Rounded.FormatUnderlined, L("Podkreśl", "Underline"), { addMarkup(tab, t, s, MarkupKind.UNDERLINE, INK_COLORS[1]) }, size = 28.dp)
            IconBtn(Icons.Rounded.FormatStrikethrough, L("Przekreśl", "Strike out"), { addMarkup(tab, t, s, MarkupKind.STRIKEOUT, INK_COLORS[1]) }, size = 28.dp)
        }
        IconBtn(Icons.Rounded.Search, L("Szukaj tego tekstu", "Search for this text"), {
            val q = t.substring(s.range).trim().take(80)
            app.settings.sidebar = true
            app.settings.sideTab = SideTab.SEARCH
            tab.query = q
            tab.search(q)
            app.focusSearch++
        }, size = 28.dp)
    }
}

/**
 * Niezapisane elementy strony i rysowanie na żywo. Rysowane w układzie nieobróconej strony
 * (współrzędne znormalizowane × rozmiar), a całość obracana jak obraz strony.
 */
private fun DrawScope.drawMarks(
    marks: List<Mark>,
    live: List<PointN>,
    styles: ToolStyles,
    tool: Tool,
    liveShape: ShapeMark?,
    rot: Int,
    boxW: Int,
    boxH: Int,
    pageWpt: Float,
    measurer: TextMeasurer,
    density: Float,
) {
    if (marks.isEmpty() && live.isEmpty() && liveShape == null) return
    val quarter = rot % 180 != 0
    val uw = (if (quarter) boxH else boxW).toFloat()
    val uh = (if (quarter) boxW else boxH).toFloat()
    val k = uw / pageWpt.coerceAtLeast(1f)
    fun o(p: PointN) = Offset(p.x * uw, p.y * uh)
    rotate(rot.toFloat(), Offset(boxW / 2f, boxH / 2f)) {
        translate((boxW - uw) / 2f, (boxH - uh) / 2f) {
            for (m in marks) drawMark(m, ::o, uw, uh, k, measurer, density)
            if (live.isNotEmpty()) {
                val marker = tool == Tool.MARKER
                drawStroke(live, ::o, Color(if (marker) styles.markerColor else styles.penColor), (if (marker) styles.markerWidth else styles.penWidth) * k, marker)
            }
            liveShape?.let { drawMark(it, ::o, uw, uh, k, measurer, density) }
        }
    }
}

private fun DrawScope.drawStroke(points: List<PointN>, o: (PointN) -> Offset, color: Color, width: Float, marker: Boolean) {
    if (points.isEmpty()) return
    val path = Path()
    val first = o(points[0])
    path.moveTo(first.x, first.y)
    if (points.size == 1) path.lineTo(first.x + 0.5f, first.y)
    for (i in 1 until points.size) {
        val q = o(points[i])
        path.lineTo(q.x, q.y)
    }
    drawPath(
        path,
        if (marker) color.copy(alpha = 0.45f) else color,
        style = Stroke(width.coerceAtLeast(1f), cap = if (marker) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (marker) BlendMode.Multiply else BlendMode.SrcOver,
    )
}

private fun DrawScope.drawMark(m: Mark, o: (PointN) -> Offset, uw: Float, uh: Float, k: Float, measurer: TextMeasurer, density: Float) {
    when (m) {
        is InkMark -> for (s in m.strokes) drawStroke(s, o, Color(m.color), m.widthPt * k, m.highlighter)
        is ShapeMark -> {
            val a = o(m.start)
            val b = o(m.end)
            val c = Color(m.color)
            val w = (m.widthPt * k).coerceAtLeast(1f)
            val tl = Offset(min(a.x, b.x), min(a.y, b.y))
            val sz = Size(abs(b.x - a.x), abs(b.y - a.y))
            val fill = m.fill
            when (m.kind) {
                ShapeKind.RECT -> {
                    if (fill != null) drawRect(Color(fill), tl, sz)
                    drawRect(c, tl, sz, style = Stroke(w, join = StrokeJoin.Round))
                }
                ShapeKind.ELLIPSE -> {
                    if (fill != null) drawOval(Color(fill), tl, sz)
                    drawOval(c, tl, sz, style = Stroke(w))
                }
                ShapeKind.LINE, ShapeKind.ARROW -> {
                    drawLine(c, a, b, w, cap = StrokeCap.Round)
                    if (m.kind == ShapeKind.ARROW) {
                        val ang = kotlin.math.atan2(b.y - a.y, b.x - a.x)
                        val len = max(8f * k, w * 4f)
                        val spread = (Math.PI / 7).toFloat()
                        drawLine(c, b, Offset(b.x - len * kotlin.math.cos(ang - spread), b.y - len * kotlin.math.sin(ang - spread)), w, cap = StrokeCap.Round)
                        drawLine(c, b, Offset(b.x - len * kotlin.math.cos(ang + spread), b.y - len * kotlin.math.sin(ang + spread)), w, cap = StrokeCap.Round)
                    }
                }
            }
        }
        is MarkupMark -> {
            val c = Color(m.color)
            for (r in m.rects) {
                val tl = Offset(r.left * uw, r.top * uh)
                val sz = Size(r.width * uw, r.height * uh)
                when (m.kind) {
                    MarkupKind.HIGHLIGHT -> drawRect(c.copy(alpha = 0.5f), tl, sz, blendMode = BlendMode.Multiply)
                    MarkupKind.UNDERLINE -> {
                        val y = tl.y + sz.height * 0.94f
                        drawLine(c, Offset(tl.x, y), Offset(tl.x + sz.width, y), max(1f, sz.height * 0.075f))
                    }
                    MarkupKind.STRIKEOUT -> {
                        val y = tl.y + sz.height / 2f
                        drawLine(c, Offset(tl.x, y), Offset(tl.x + sz.width, y), max(1f, sz.height * 0.075f))
                    }
                }
            }
        }
        is TextMark -> {
            val tl = Offset(m.rect.left * uw, m.rect.top * uh)
            val sz = Size(m.rect.width * uw, m.rect.height * uh)
            m.background?.let { drawRect(Color(it), tl, sz) }
            drawRect(Color(0xFF3D8BFF).copy(alpha = 0.5f), tl, sz, style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
            val fontPx = m.sizePt * k
            val style = TextStyle(color = Color(m.color), fontSize = (fontPx / density).sp, lineHeight = (fontPx * TEXT_LEADING / density).sp, fontFamily = UiFont)
            val layout = measurer.measure(m.text, style)
            drawText(layout, topLeft = Offset(tl.x + TEXT_PAD * k, tl.y + TEXT_PAD * k))
        }
        is NoteMark -> {
            val b = m.bounds()
            val tl = Offset(b.left * uw, b.top * uh)
            val sz = Size(b.width * uw, b.height * uh)
            drawRoundRect(Color(m.color), tl, sz, CornerRadius(sz.width * 0.15f))
            drawRoundRect(Color.Black.copy(alpha = 0.55f), tl, sz, CornerRadius(sz.width * 0.15f), style = Stroke(1f))
            for (i in 1..3) {
                val y = tl.y + sz.height * i / 4.2f
                drawLine(Color.Black.copy(alpha = 0.6f), Offset(tl.x + sz.width * 0.2f, y), Offset(tl.x + sz.width * 0.8f, y), 1f)
            }
        }
        is ImageMark, is com.blacksingularities.openpdf.pdf.SignatureMark -> {
            val b = m.bounds()
            drawRect(Color(0xFF3D8BFF).copy(alpha = 0.25f), Offset(b.left * uw, b.top * uh), Size(b.width * uw, b.height * uh))
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
