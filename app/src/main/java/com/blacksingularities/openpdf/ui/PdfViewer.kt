package com.blacksingularities.openpdf.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.data.PaperTheme
import com.blacksingularities.openpdf.data.pageCrops
import com.blacksingularities.openpdf.data.effectiveScroll
import com.blacksingularities.openpdf.pdf.AnnotationWriter
import com.blacksingularities.openpdf.pdf.ImageMark
import com.blacksingularities.openpdf.pdf.InkMark
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.MarkupMark
import com.blacksingularities.openpdf.pdf.NoteMark
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.RectN
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.pdf.ShapeMark
import com.blacksingularities.openpdf.pdf.SignatureMark
import com.blacksingularities.openpdf.pdf.TextMark
import com.blacksingularities.openpdf.pdf.TileRequest
import com.blacksingularities.openpdf.pdf.resizable
import com.blacksingularities.openpdf.ui.theme.GlassLevel
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.ui.theme.glass
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class GestureMode { PENDING, PAN, DRAW, MOVE, RESIZE, SELECT_TEXT, HANDLE_START, HANDLE_END }

/**
 * Widok dokumentu: strony (pojedynczo albo rozkładówkami), powiększanie i przesuwanie całości,
 * przewijanie z bezwładnością, rysowanie i zaznaczanie. [book] – czy pokazywać rozkładówki.
 */
@Composable
fun PdfViewer(vm: PdfViewModel, state: ViewerState, book: Boolean, modifier: Modifier = Modifier, topInset: Float = 0f) {
    val session = vm.render ?: return
    val settings = vm.settings
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val margin = if (settings.edgeToEdge) 0f else with(density) { 10.dp.toPx() }
    val gap = with(density) { if (settings.pageGap) 12.dp.toPx() else 2.dp.toPx() }
    val spec = LayoutSpec(
        book = book,
        coverAlone = settings.coverAlone,
        rtl = settings.rtl,
        fit = if (book) settings.fitBook else settings.fitSingle,
        scroll = settings.effectiveScroll(book),
        margin = margin,
        gap = if (book) gap else gap,
        topInset = topInset,
    )
    // Przycięcie każdej strony zależy od jej miejsca w rozkładówce (zewnętrzna krawędź czy grzbiet).
    val crops = remember(
        session, book, spec.coverAlone, spec.rtl, settings.cropSpread,
        settings.cropLeft, settings.cropTop, settings.cropRight, settings.cropBottom,
        settings.spreadCropLeft, settings.spreadCropTop, settings.spreadCropRight, settings.spreadCropBottom,
    ) {
        val n = session.sizes.size
        settings.pageCrops(DocLayout.group(n, book, spec.coverAlone, spec.rtl), n, book)
    }
    state.crops = crops
    // Proporcje stron po przycięciu marginesów – układ liczy tylko widoczną część.
    val aspects = remember(session, crops) {
        session.sizes.mapIndexed { i, size ->
            val c = crops[i]
            size.aspect * (1f - c[0] - c[2]) / (1f - c[1] - c[3])
        }
    }
    val vp = state.viewport

    // Nowy układ po zmianie rozmiaru okna, trybu książki lub dokumentu – z zachowaniem bieżącej strony.
    LaunchedEffect(aspects, vp, spec) {
        if (vp.width <= 0f || vp.height <= 0f) return@LaunchedEffect
        val anchor = if (state.layout == null) vm.currentPage else state.currentPage()
        state.setLayout(DocLayout.build(aspects, vp.width, vp.height, spec), anchor)
    }
    // Skoki (spis treści, wyszukiwanie, odnośniki).
    LaunchedEffect(vm.jump) {
        val j = vm.jump ?: return@LaunchedEffect
        snapshotFlow { state.layout }.first { it != null }
        state.scrollToPage(j.page, j.region, animate = true)
    }
    LaunchedEffect(vm.step) {
        val s = vm.step ?: return@LaunchedEffect
        state.step(s.direction)
    }
    // Bieżąca strona.
    LaunchedEffect(state) {
        snapshotFlow { if (state.layout == null) -1 else state.currentPage() }
            .distinctUntilChanged()
            .collect { if (it >= 0) vm.reportPage(it) }
    }
    // Zlecenia renderowania dla widocznego obszaru.
    LaunchedEffect(state, session) {
        snapshotFlow { Triple(state.scale, state.offset, state.interacting) to state.layout }
            .collectLatest { (t, layout) ->
                val (scale, _, interacting) = t
                if (layout == null) return@collectLatest
                val visible = state.visibleRows()
                val around = state.visibleRows(extra = max(vp.width, vp.height) * 2f)
                val base = ArrayList<Pair<Int, Int>>()
                val zoomBase = scale.coerceIn(0.35f, 1f)
                for (row in visible) for (slot in row.slots) {
                    val w = vm.images.bucket(state.full(slot).width * zoomBase)
                    if (base.none { it.first == slot.page }) base += slot.page to w
                }
                val visibleCount = base.size
                for (row in around) for (slot in row.slots) {
                    val w = vm.images.bucket(state.full(slot).width * zoomBase)
                    if (base.none { it.first == slot.page }) base += slot.page to w
                }
                val tiles = ArrayList<TileRequest>()
                if (!interacting && scale > 1.15f) {
                    for (row in visible) for (slot in row.slots) {
                        val cr = state.toScreen(slot.rect)
                        val sr = state.pageScreen(slot)
                        val l = max(0f, cr.left)
                        val t2 = max(0f, cr.top)
                        val r = min(vp.width, cr.right)
                        val b = min(vp.height, cr.bottom)
                        if (r <= l || b <= t2) continue
                        val ex = (r - l) * 0.15f
                        val ey = (b - t2) * 0.15f
                        val region = RectN(
                            ((l - ex - sr.left) / sr.width).coerceIn(0f, 1f),
                            ((t2 - ey - sr.top) / sr.height).coerceIn(0f, 1f),
                            ((r + ex - sr.left) / sr.width).coerceIn(0f, 1f),
                            ((b + ey - sr.top) / sr.height).coerceIn(0f, 1f),
                        )
                        val pw = ((sr.width / 64f).roundToInt() * 64).coerceAtLeast(64)
                        tiles += TileRequest(slot.page, region, pw)
                    }
                }
                vm.images.request(base, tiles, visibleCount)
                // Miniatury sąsiednich stron renderują się błyskawicznie i służą za podgląd,
                // zanim powstanie pełny obraz strony.
                vm.images.requestThumbs(around.flatMap { r -> r.slots.map { it.page } })
            }
    }

    val paperFilter = remember(settings.paper) { paperFilter(settings.paper) }
    val paperColor = when (settings.paper) {
        PaperTheme.NORMAL -> Color.White
        PaperTheme.NIGHT -> Color(0xFF1C1C1E)
        PaperTheme.DARK -> Color.Black
        PaperTheme.SEPIA -> Color(0xFFF7EBD0)
    }
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT } }
    val handleRadius = with(density) { 11.dp.toPx() }
    val shadowOffset = with(density) { 2.dp.toPx() }

    val currentBook by rememberUpdatedState(book)
    state.flipEnabled = book && settings.pageFlip

    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Desk)
            .onSizeChanged { state.viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(state, vm) {
                var lastTapTime = 0L
                var lastTapPos = Offset.Zero
                var pendingTap: Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    state.stop()
                    state.interacting = true
                    val tool = vm.tool
                    val slop = viewConfiguration.touchSlop
                    val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    val hitDown = state.hit(down.position)
                    val slot = hitDown?.first
                    val downPoint = hitDown?.second ?: PointN(0f, 0f)
                    var mode = GestureMode.PENDING
                    val sel = vm.selection
                    if (sel != null && slot != null && slot.page == sel.page && sel.rects.isNotEmpty()) {
                        val sr = state.pageScreen(slot)
                        val a = sel.rects.first()
                        val b = sel.rects.last()
                        val hs = Offset(sr.left + a.left * sr.width, sr.top + a.bottom * sr.height)
                        val he = Offset(sr.left + b.right * sr.width, sr.top + b.bottom * sr.height)
                        if ((down.position - hs).getDistance() < handleRadius * 2.4f) mode = GestureMode.HANDLE_START
                        else if ((down.position - he).getDistance() < handleRadius * 2.4f) mode = GestureMode.HANDLE_END
                    }
                    if (mode == GestureMode.PENDING && slot != null) {
                        val p = downPoint
                        if (tool.drags && (!vm.settings.stylusOnly || stylus || tool == Tool.MARKUP)) {
                            mode = GestureMode.DRAW
                            vm.dragStart(slot.page, p)
                        } else if (tool == Tool.SELECT) {
                            val selected = vm.marks.firstOrNull { it.id == vm.selectedId && it.page == slot.page }
                            val sr = state.pageScreen(slot)
                            val handle = selected?.takeIf { it.resizable }?.bounds()?.let { b ->
                                Offset(sr.left + b.right * sr.width, sr.top + b.bottom * sr.height)
                            }
                            if (handle != null && (down.position - handle).getDistance() < handleRadius * 2.4f) {
                                mode = GestureMode.RESIZE
                                vm.beginMove(selected!!.id)
                            } else {
                                val m = vm.markAt(slot.page, p)
                                if (m != null) {
                                    mode = GestureMode.MOVE
                                    vm.beginMove(m.id)
                                }
                            }
                        }
                    }
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moved = false
                    var multi = false
                    while (true) {
                        val waitLong = mode == GestureMode.PENDING && !moved && !multi && (tool == Tool.VIEW || tool == Tool.SELECT) && slot != null
                        val event = if (waitLong) {
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // Przytrzymanie: zaznaczenie słowa (dalej – przeciąganie zaznaczenia).
                            if (slot != null) {
                                mode = GestureMode.SELECT_TEXT
                                vm.beginTextSelection(slot.page, downPoint, word = true)
                                if (vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            continue
                        }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            val up = event.changes.firstOrNull()
                            when (mode) {
                                GestureMode.DRAW -> vm.dragEnd()
                                GestureMode.MOVE, GestureMode.RESIZE -> {
                                    if (!moved && mode == GestureMode.MOVE) vm.editTap(slot!!.page, downPoint)
                                    vm.endMove()
                                }
                                GestureMode.SELECT_TEXT, GestureMode.HANDLE_START, GestureMode.HANDLE_END -> Unit
                                GestureMode.PENDING, GestureMode.PAN -> {
                                    if (!moved && !multi && up != null) {
                                        val pos = up.position
                                        val now = up.uptimeMillis
                                        if (vm.editing && tool != Tool.VIEW) {
                                            if (slot != null) vm.editTap(slot.page, downPoint)
                                        } else if (vm.selection != null) {
                                            vm.clearSelection()
                                        } else if (now - lastTapTime < viewConfiguration.doubleTapTimeoutMillis && (pos - lastTapPos).getDistance() < slop * 4) {
                                            pendingTap?.cancel()
                                            lastTapTime = 0L
                                            state.doubleTap(pos)
                                        } else {
                                            lastTapTime = now
                                            lastTapPos = pos
                                            pendingTap?.cancel()
                                            pendingTap = scope.launch {
                                                delay(viewConfiguration.doubleTapTimeoutMillis)
                                                singleTap(vm, state, pos, currentBook)
                                            }
                                        }
                                    } else if (!multi && state.flip != null) {
                                        state.flipRelease(tracker.calculateVelocity().x)
                                    } else if (!multi) {
                                        state.fling(tracker.calculateVelocity().let { Offset(it.x, it.y) })
                                    } else if (state.paged && state.scale <= 1.01f) {
                                        state.snapPaged()
                                    }
                                }
                            }
                            event.changes.forEach { it.consume() }
                            break
                        }
                        if (pressed.size >= 2) {
                            when (mode) {
                                GestureMode.DRAW -> vm.dragCancel()
                                GestureMode.MOVE, GestureMode.RESIZE -> vm.endMove()
                                else -> Unit
                            }
                            mode = GestureMode.PAN
                            multi = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid.isSpecified()) state.transformBy(centroid, zoom, pan)
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val ch = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
                        if (!moved && (ch.position - down.position).getDistance() > slop) moved = true
                        when (mode) {
                            GestureMode.DRAW -> slot?.let { vm.dragMove(it.page, state.pointIn(it, ch.position)) }
                            GestureMode.MOVE -> if (moved && slot != null) {
                                val d = ch.positionChange()
                                val sr = state.pageScreen(slot)
                                vm.moveSelected(d.x / sr.width, d.y / sr.height)
                            }
                            GestureMode.RESIZE -> slot?.let { vm.resizeSelected(state.pointIn(it, ch.position)) }
                            GestureMode.SELECT_TEXT -> slot?.let { vm.moveTextSelection(it.page, state.pointIn(it, ch.position)) }
                            GestureMode.HANDLE_START, GestureMode.HANDLE_END -> slot?.let {
                                vm.dragSelectionHandle(mode == GestureMode.HANDLE_START, it.page, state.pointIn(it, ch.position))
                            }
                            GestureMode.PENDING, GestureMode.PAN -> if (moved) {
                                mode = GestureMode.PAN
                                if (!multi && (state.flip != null || state.canFlip())) {
                                    state.flipDrag(ch.position.x - down.position.x)
                                    tracker.addPosition(ch.uptimeMillis, ch.position)
                                } else if (!multi) {
                                    state.panBy(ch.positionChange())
                                    tracker.addPosition(ch.uptimeMillis, ch.position)
                                } else {
                                    state.panBy(ch.positionChange())
                                }
                            }
                        }
                        ch.consume()
                    }
                    state.interacting = false
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            vm.images.version.intValue
            val layout = state.layout ?: return@Canvas
            val marksByPage = vm.marks.groupBy { it.page }
            val selectedId = vm.selectedId
            val live = vm.live
            val liveShape = vm.liveShape
            val sel = vm.selection
            val hits = vm.searchHits
            val hitIndex = vm.searchIndex
            val erased = vm.erasedPreview
            val geos = vm.index.geos
            val showFields = vm.index.fields.isNotEmpty() && vm.sheet == Sheet.FORM
            val flip = state.flip
            if (flip != null) {
                drawFlip(vm, state, layout, flip, paperColor, paperFilter, shadowOffset)
                return@Canvas
            }
            for (row in state.visibleRows()) {
                val rowRect = state.toScreen(row.rect)
                for (slot in row.slots) {
                    // c – widoczny (przycięty) fragment strony, r – cała strona.
                    val c = state.toScreen(slot.rect)
                    val r = state.pageScreen(slot)
                    drawRect(Color.Black.copy(alpha = 0.35f), Offset(c.left, c.top + shadowOffset), c.size)
                    drawRect(paperColor, c.topLeft, c.size)
                    val bmp = vm.images.base(slot.page) ?: vm.images.thumb(slot.page)
                    clipRect(c.left, c.top, c.right, c.bottom) {
                    if (bmp != null) {
                        drawImage(
                            bmp.asImageBitmap(),
                            dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                            dstSize = IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
                            filterQuality = FilterQuality.Medium,
                            colorFilter = paperFilter,
                        )
                    }
                    for (t in vm.images.tiles(slot.page)) {
                        val l = r.left + t.region.left * r.width
                        val tp = r.top + t.region.top * r.height
                        drawImage(
                            t.bitmap.asImageBitmap(),
                            dstOffset = IntOffset(l.roundToInt(), tp.roundToInt()),
                            dstSize = IntSize((t.region.width * r.width).roundToInt().coerceAtLeast(1), (t.region.height * r.height).roundToInt().coerceAtLeast(1)),
                            filterQuality = FilterQuality.Medium,
                            colorFilter = paperFilter,
                        )
                    }
                    }
                    val pageWidthPt = geos.getOrNull(slot.page)?.displayWidth ?: 595f
                    clipRect(c.left, c.top, c.right, c.bottom) {
                        if (showFields) {
                            for (f in vm.index.fields) for (w in f.widgets) if (w.page == slot.page) {
                                drawRect(Color(0x332196F3), rectOf(r, w.rect).topLeft, rectOf(r, w.rect).size)
                            }
                        }
                        for (a in erased) if (a.page == slot.page) {
                            val rr = rectOf(r, a.rect)
                            drawRect(Color(0x55FF3B47), rr.topLeft, rr.size)
                        }
                        hits.forEachIndexed { i, h ->
                            if (h.page == slot.page) for (hr in h.rects) {
                                val rr = rectOf(r, hr)
                                drawRect(if (i == hitIndex) Color(0x99FF9800) else Color(0x66FFEB3B), rr.topLeft, rr.size, blendMode = BlendMode.Multiply)
                            }
                        }
                        marksByPage[slot.page]?.forEach { m -> drawMark(vm, m, r, pageWidthPt, textPaint) }
                        if (liveShape != null && liveShape.page == slot.page) drawMark(vm, liveShape, r, pageWidthPt, textPaint)
                        if (live != null && live.page == slot.page) {
                            val st = vm.style(vm.tool)
                            drawInk(listOf(live.points.toList()), r, st.width * r.width / pageWidthPt, Color(st.color), vm.tool == Tool.HIGHLIGHTER)
                        }
                        if (sel != null && sel.page == slot.page) {
                            for (sr in sel.rects) {
                                val rr = rectOf(r, sr)
                                drawRect(Color(0x552196F3), rr.topLeft, rr.size)
                            }
                        }
                    }
                    if (sel != null && sel.page == slot.page && sel.rects.isNotEmpty()) {
                        val a = sel.rects.first()
                        val b = sel.rects.last()
                        drawSelectionHandle(Offset(r.left + a.left * r.width, r.top + a.bottom * r.height), handleRadius, true)
                        drawSelectionHandle(Offset(r.left + b.right * r.width, r.top + b.bottom * r.height), handleRadius, false)
                    }
                    val selected = marksByPage[slot.page]?.firstOrNull { it.id == selectedId }
                    if (selected != null) {
                        val br = rectOf(r, selected.bounds()).inflate(4.dp.toPx())
                        drawRect(
                            Color(0xFF2196F3),
                            br.topLeft,
                            br.size,
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
                        )
                        if (selected.resizable) {
                            drawCircle(Color.White, handleRadius * 0.75f, Offset(br.right, br.bottom))
                            drawCircle(Color(0xFF2196F3), handleRadius * 0.55f, Offset(br.right, br.bottom))
                        }
                    }
                }
                // Cień grzbietu otwartej książki.
                val spine = row.spine
                if (spine != null && row.slots.size == 2 && settings.spineShadow) {
                    val x = spine * state.scale + state.offset.x
                    val w = rowRect.width * 0.045f
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f)), startX = x - w, endX = x),
                        Offset(x - w, rowRect.top),
                        Size(w, rowRect.height),
                    )
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent), startX = x, endX = x + w * 0.8f),
                        Offset(x, rowRect.top),
                        Size(w * 0.8f, rowRect.height),
                    )
                }
            }
        }
        FastScroller(state, vm)
    }
}

/** Strona narysowana w dowolnym prostokącie (podstawowa bitmapa, bez kafelków). */
private fun DrawScope.drawPageInto(vm: PdfViewModel, page: Int, box: Rect, paper: Color, filter: ColorFilter?, crop: FloatArray) {
    if (box.width < 0.5f) return
    drawRect(paper, box.topLeft, box.size)
    val bmp = vm.images.base(page) ?: vm.images.thumb(page) ?: return
    // Przycięte marginesy wychodzą poza widoczne pole i są obcinane.
    val w = box.width / (1f - crop[0] - crop[2])
    val h = box.height / (1f - crop[1] - crop[3])
    val r = Rect(box.left - crop[0] * w, box.top - crop[1] * h, box.left - crop[0] * w + w, box.top - crop[1] * h + h)
    clipRect(box.left, box.top, box.right, box.bottom) {
        drawImage(
            bmp.asImageBitmap(),
            dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
            dstSize = IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium,
            colorFilter = filter,
        )
    }
}

/**
 * Przewracanie kartki jak w książce: kartka obraca się wokół grzbietu – w pierwszej połowie
 * zwęża się bieżąca strona, w drugiej rozwija się strona z kolejnej rozkładówki.
 * Pod spodem widać już stronę z rozkładówki docelowej; cienie podkreślają głębię.
 */
private fun DrawScope.drawFlip(
    vm: PdfViewModel,
    state: ViewerState,
    layout: DocLayout,
    flip: ViewerState.Flip,
    paper: Color,
    filter: ColorFilter?,
    shadow: Float,
) {
    val from = layout.rows.getOrNull(flip.from) ?: return
    val to = layout.rows.getOrNull(flip.to) ?: return
    val vw = state.viewport.width
    val oy = state.offset.y
    fun screen(row: Row, slot: Slot) = Rect(slot.rect.left - row.index * vw, slot.rect.top + oy, slot.rect.right - row.index * vw, slot.rect.bottom + oy)
    val spine = (from.spine ?: from.rect.centerX) - from.index * vw
    // Strona po lewej / prawej stronie grzbietu w danym rzędzie.
    fun side(row: Row, left: Boolean): Pair<Int, Rect>? {
        val sp = (row.spine ?: row.rect.centerX) - row.index * vw
        return row.slots.map { it.page to screen(row, it) }.firstOrNull { (_, r) -> if (left) r.center.x < sp else r.center.x >= sp }
    }
    // Kartka odchodzi od prawej krawędzi (do przodu) albo od lewej (wstecz); w RTL odwrotnie.
    val fromRight = flip.forward != layout.spec.rtl
    val t = flip.progress.coerceIn(0f, 1f)
    val c = cos(PI * t).toFloat()
    val staticSide = side(from, left = fromRight)
    val revealed = side(to, left = !fromRight)
    for ((page, r) in listOfNotNull(staticSide, revealed)) {
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(r.left, r.top + shadow), r.size)
        drawPageInto(vm, page, r, paper, filter, state.cropOf(page))
    }
    revealed?.let { (_, r) ->
        drawRect(Color.Black.copy(alpha = 0.32f * (1f - kotlin.math.abs(c))), r.topLeft, r.size)
    }
    val firstHalf = t < 0.5f
    val leaf = if (firstHalf) side(from, left = !fromRight) else side(to, left = fromRight)
    if (leaf != null) {
        val r = leaf.second
        val w = r.width * kotlin.math.abs(c)
        // Kartka zawsze przylega do grzbietu: przed połową po stronie odejścia, potem po przeciwnej.
        val onRight = firstHalf == fromRight
        val box = if (onRight) Rect(spine, r.top, spine + w, r.bottom) else Rect(spine - w, r.top, spine, r.bottom)
        drawPageInto(vm, leaf.first, box, paper, filter, state.cropOf(leaf.first))
        val dark = Color.Black.copy(alpha = 0.28f * (1f - kotlin.math.abs(c)))
        val light = Color.Black.copy(alpha = 0.04f)
        drawRect(
            Brush.horizontalGradient(if (onRight) listOf(dark, light) else listOf(light, dark), startX = box.left, endX = box.right),
            box.topLeft,
            box.size,
        )
    }
    val top = minOf(from.rect.top, to.rect.top) + oy
    val h = maxOf(from.rect.height, to.rect.height)
    drawLine(Color.Black.copy(alpha = 0.25f), Offset(spine, top), Offset(spine, top + h), 1.5f)
}

private fun Offset.isSpecified() = !x.isNaN() && !y.isNaN()

private fun singleTap(vm: PdfViewModel, state: ViewerState, pos: Offset, book: Boolean) {
    val hit = state.hit(pos)
    if (hit != null && vm.viewTap(hit.first.page, hit.second)) return
    val w = state.viewport.width
    if (vm.settings.tapZones && state.paged && state.scale <= 1.01f) {
        val left = pos.x < w * 0.18f
        val right = pos.x > w * 0.82f
        if (left || right) {
            // W kolejności od prawej do lewej strefy są zamienione.
            val forward = if (vm.settings.rtl && book) left else right
            state.step(if (forward) 1 else -1)
            return
        }
    }
    vm.chrome = !vm.chrome
}

private fun rectOf(page: Rect, r: RectN) = Rect(
    page.left + r.left * page.width,
    page.top + r.top * page.height,
    page.left + r.right * page.width,
    page.top + r.bottom * page.height,
)

private fun Rect.inflate(d: Float) = Rect(left - d, top - d, right + d, bottom + d)

private fun DrawScope.drawSelectionHandle(at: Offset, radius: Float, start: Boolean) {
    val c = Color(0xFF2196F3)
    drawLine(c, Offset(at.x, at.y - radius * 1.6f), at, strokeWidth = 2.dp.toPx())
    drawCircle(c, radius * 0.7f, Offset(at.x + if (start) -radius * 0.45f else radius * 0.45f, at.y + radius * 0.6f))
}

private fun smoothPath(points: List<PointN>, r: Rect): Path {
    val path = Path()
    if (points.isEmpty()) return path
    fun x(p: PointN) = r.left + p.x * r.width
    fun y(p: PointN) = r.top + p.y * r.height
    path.moveTo(x(points[0]), y(points[0]))
    if (points.size == 1) {
        path.lineTo(x(points[0]) + 0.1f, y(points[0]))
        return path
    }
    for (i in 1 until points.size - 1) {
        val c = points[i]
        val n = points[i + 1]
        path.quadraticTo(x(c), y(c), (x(c) + x(n)) / 2f, (y(c) + y(n)) / 2f)
    }
    path.lineTo(x(points.last()), y(points.last()))
    return path
}

private fun DrawScope.drawInk(strokes: List<List<PointN>>, r: Rect, widthPx: Float, color: Color, highlighter: Boolean) {
    for (s in strokes) {
        drawPath(
            smoothPath(s, r),
            color = if (highlighter) color.copy(alpha = 0.45f) else color,
            style = Stroke(
                width = max(0.8f, widthPx),
                cap = if (highlighter) StrokeCap.Butt else StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = if (highlighter) BlendMode.Multiply else BlendMode.SrcOver,
        )
    }
}

/** Rysuje element oczekujący tak, jak będzie wyglądał w pliku. */
private fun DrawScope.drawMark(vm: PdfViewModel, m: Mark, r: Rect, pageWidthPt: Float, paint: Paint) {
    val pxPerPt = r.width / pageWidthPt
    fun x(p: PointN) = r.left + p.x * r.width
    fun y(p: PointN) = r.top + p.y * r.height
    when (m) {
        is InkMark -> drawInk(m.strokes, r, m.widthPt * pxPerPt, Color(m.color), m.highlighter)
        is SignatureMark -> {
            val box = rectOf(r, m.rect)
            drawInk(m.strokes, box, m.widthPt * pxPerPt, Color(m.color), false)
        }
        is ShapeMark -> {
            val c = Color(m.color)
            val w = max(0.8f, m.widthPt * pxPerPt)
            val a = Offset(x(m.start), y(m.start))
            val b = Offset(x(m.end), y(m.end))
            val tl = Offset(min(a.x, b.x), min(a.y, b.y))
            val size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
            when (m.kind) {
                ShapeKind.RECT -> {
                    m.fill?.let { drawRect(Color(it), tl, size) }
                    drawRect(c, tl, size, style = Stroke(w, join = StrokeJoin.Round))
                }
                ShapeKind.ELLIPSE -> {
                    m.fill?.let { drawOval(Color(it), tl, size) }
                    drawOval(c, tl, size, style = Stroke(w))
                }
                ShapeKind.LINE -> drawLine(c, a, b, w, StrokeCap.Round)
                ShapeKind.ARROW -> {
                    drawLine(c, a, b, w, StrokeCap.Round)
                    val head = max(8f, m.widthPt * 4f) * pxPerPt
                    val ang = atan2(b.y - a.y, b.x - a.x)
                    val spread = (PI / 7).toFloat()
                    drawLine(c, b, Offset(b.x - head * cos(ang - spread), b.y - head * sin(ang - spread)), w, StrokeCap.Round)
                    drawLine(c, b, Offset(b.x - head * cos(ang + spread), b.y - head * sin(ang + spread)), w, StrokeCap.Round)
                }
            }
        }
        is TextMark -> {
            val box = rectOf(r, m.rect)
            m.background?.let { drawRect(Color(it), box.topLeft, box.size) }
            paint.color = m.color
            paint.textSize = m.sizePt * pxPerPt
            val pad = AnnotationWriter.TEXT_PAD * pxPerPt
            var baseline = box.top + pad + m.sizePt * 0.92f * pxPerPt
            drawIntoCanvas { canvas ->
                for (line in m.text.split('\n')) {
                    canvas.nativeCanvas.drawText(line, box.left + pad, baseline, paint)
                    baseline += m.sizePt * AnnotationWriter.TEXT_LEADING * pxPerPt
                }
            }
        }
        is NoteMark -> {
            val box = rectOf(r, m.bounds())
            val s = min(box.width, box.height)
            val body = Rect(box.left, box.top, box.left + s, box.top + s * 0.78f)
            drawRoundRect(Color(m.color), body.topLeft, body.size, CornerRadius(s * 0.12f))
            drawRoundRect(Color(0xFF333333), body.topLeft, body.size, CornerRadius(s * 0.12f), style = Stroke(max(1f, s * 0.05f)))
            val tail = Path().apply {
                moveTo(box.left + s * 0.2f, body.bottom)
                lineTo(box.left + s * 0.2f, box.top + s)
                lineTo(box.left + s * 0.45f, body.bottom)
                close()
            }
            drawPath(tail, Color(m.color))
            for (k in 1..3) {
                val ly = body.top + body.height * k / 4.2f
                drawLine(Color(0xFF444444), Offset(body.left + s * 0.18f, ly), Offset(body.right - s * 0.18f, ly), max(1f, s * 0.05f))
            }
        }
        is MarkupMark -> {
            val c = Color(m.color)
            for (rr in m.rects) {
                val box = rectOf(r, rr)
                when (m.kind) {
                    MarkupKind.HIGHLIGHT -> drawRect(c.copy(alpha = 0.55f), box.topLeft, box.size, blendMode = BlendMode.Multiply)
                    MarkupKind.UNDERLINE -> {
                        val t = max(1f, box.height * 0.075f)
                        drawLine(c, Offset(box.left, box.bottom - box.height * 0.08f), Offset(box.right, box.bottom - box.height * 0.08f), t)
                    }
                    MarkupKind.STRIKEOUT -> {
                        val t = max(1f, box.height * 0.075f)
                        drawLine(c, Offset(box.left, box.center.y), Offset(box.right, box.center.y), t)
                    }
                }
            }
        }
        is ImageMark -> {
            val box = rectOf(r, m.rect)
            val bmp = vm.markBitmap(m.path)
            if (bmp != null) {
                drawImage(
                    bmp.asImageBitmap(),
                    dstOffset = IntOffset(box.left.roundToInt(), box.top.roundToInt()),
                    dstSize = IntSize(box.width.roundToInt().coerceAtLeast(1), box.height.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                drawRect(Color.Gray, box.topLeft, box.size, style = Stroke(2f))
            }
        }
    }
}

private fun paperFilter(theme: PaperTheme): ColorFilter? = when (theme) {
    PaperTheme.NORMAL -> null
    PaperTheme.NIGHT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.86f, 0f, 0f, 0f, 236f,
                0f, -0.86f, 0f, 0f, 236f,
                0f, 0f, -0.86f, 0f, 236f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    // Ciemny: pełne odwrócenie – biały tekst na czarnym tle.
    PaperTheme.DARK -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    PaperTheme.SEPIA -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.97f, 0f, 0f, 0f, 0f,
                0f, 0.92f, 0f, 0f, 0f,
                0f, 0f, 0.80f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/** Szybkie przewijanie długich dokumentów: uchwyt przy prawej krawędzi z numerem strony. */
@Composable
private fun FastScroller(state: ViewerState, vm: PdfViewModel) {
    val layout = state.layout ?: return
    if (layout.spec.scroll != ScrollMode.VERTICAL) return
    if (layout.height * state.scale < state.viewport.height * 3f) return
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.offset, dragging) {
        visible = true
        if (!dragging) {
            delay(1400)
            visible = false
        }
    }
    if (!visible && !dragging) return
    val thumbH = with(density) { 44.dp.toPx() }
    val track = state.viewport.height - thumbH
    val y = state.scrollFraction * track
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, y.roundToInt()) }
                .padding(end = 2.dp)
                .size(width = 26.dp, height = 44.dp)
                .pointerInput(state) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        val pos = state.scrollFraction * track + change.positionChange().y
                        state.scrollToFraction(pos / track)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (dragging) 0.95f else 0.6f)),
            )
        }
        if (dragging) {
            Text(
                "${vm.currentPage + 1}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { 40.dp.roundToPx() }, y.roundToInt()) }
                    .glass(RoundedCornerShape(50), GlassLevel.Regular)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
