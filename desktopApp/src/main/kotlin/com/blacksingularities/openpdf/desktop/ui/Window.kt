package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.blacksingularities.openpdf.desktop.WindowsChrome
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Window
import kotlin.math.max

fun toggleMaximize(state: WindowState, window: Window) {
    if (state.placement == WindowPlacement.Maximized) {
        state.placement = WindowPlacement.Floating
    } else {
        WindowsChrome.updateMaximizedBounds(window)
        state.placement = WindowPlacement.Maximized
    }
}

/** Obszar roboczy ekranu pod punktem (bez paska zadań), we współrzędnych pulpitu. */
private fun workArea(at: java.awt.Point, window: Window): java.awt.Rectangle {
    val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    val gc = ge.screenDevices.map { it.defaultConfiguration }.firstOrNull { it.bounds.contains(at) } ?: window.graphicsConfiguration
    val b = gc.bounds
    val ins = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return java.awt.Rectangle(b.x + ins.left, b.y + ins.top, b.width - ins.left - ins.right, b.height - ins.top - ins.bottom)
}

/**
 * Przeciąganie okna za pasek tytułu jak w Windows: dwuklik maksymalizuje, przeciągnięcie
 * zmaksymalizowanego okna przywraca je pod kursorem, upuszczenie przy górnej krawędzi ekranu
 * maksymalizuje, a przy bocznej – zajmuje połowę ekranu.
 */
internal fun Modifier.windowDrag(state: WindowState, window: Window): Modifier = this.pointerInput(state, window) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val native = currentEvent.nativeEvent as? java.awt.event.MouseEvent
        if (native != null && native.button != java.awt.event.MouseEvent.BUTTON1) return@awaitEachGesture
        if ((native?.clickCount ?: 1) >= 2) {
            toggleMaximize(state, window)
            return@awaitEachGesture
        }
        val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
        val origin = window.location
        var grab = java.awt.Point(start.x - origin.x, start.y - origin.y)
        var dragging = false
        while (true) {
            val ev = awaitPointerEvent()
            if (ev.changes.none { it.pressed }) break
            val p = MouseInfo.getPointerInfo()?.location ?: continue
            if (!dragging) {
                if (kotlin.math.abs(p.x - start.x) + kotlin.math.abs(p.y - start.y) < 4) continue
                dragging = true
                if (state.placement == WindowPlacement.Maximized) {
                    // Przywrócenie z zachowaniem miejsca chwytu (proporcjonalnie do szerokości).
                    val frac = grab.x.toFloat() / window.width.coerceAtLeast(1)
                    state.placement = WindowPlacement.Floating
                    val w = state.size.width.value.toInt().coerceAtLeast(400)
                    grab = java.awt.Point((w * frac).toInt(), grab.y)
                }
            }
            ev.changes.forEach { it.consume() }
            window.setLocation(p.x - grab.x, p.y - grab.y)
        }
        if (dragging) {
            val p = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
            val wa = workArea(p, window)
            when {
                p.y <= wa.y + 2 -> {
                    WindowsChrome.updateMaximizedBounds(window)
                    state.placement = WindowPlacement.Maximized
                }
                p.x <= wa.x + 2 -> window.setBounds(wa.x, wa.y, wa.width / 2, wa.height)
                p.x >= wa.x + wa.width - 3 -> window.setBounds(wa.x + wa.width / 2, wa.y, wa.width - wa.width / 2, wa.height)
            }
        }
    }
}

internal enum class WinGlyph { MIN, MAX, RESTORE, CLOSE }

/** Przycisk okna w stylu Windows 11: cienkie znaki, czerwone tło zamykania. */
@Composable
internal fun WindowButton(glyph: WinGlyph, tip: String, height: Dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val close = glyph == WinGlyph.CLOSE
    val fg = if (close && hovered) Color.White else P.text
    Tip(tip) {
        Box(
            Modifier.size(46.dp, height)
                .background(if (hovered) (if (close) Color(0xFFE81123) else P.raised2) else Color.Transparent)
                .hoverable(src)
                .clickable(src, null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(10.dp)) {
                val w = size.width
                val px = 1.dp.toPx()
                when (glyph) {
                    WinGlyph.MIN -> drawLine(fg, Offset(0f, w / 2), Offset(w, w / 2), px)
                    WinGlyph.MAX -> drawRect(fg, style = Stroke(px))
                    WinGlyph.RESTORE -> {
                        val o = w * 0.24f
                        drawRect(fg, Offset(0f, o), Size(w - o, w - o), style = Stroke(px))
                        drawLine(fg, Offset(o, o), Offset(o, 0f), px)
                        drawLine(fg, Offset(o, 0f), Offset(w, 0f), px)
                        drawLine(fg, Offset(w, 0f), Offset(w, w - o), px)
                        drawLine(fg, Offset(w, w - o), Offset(w - o, w - o), px)
                    }
                    WinGlyph.CLOSE -> {
                        drawLine(fg, Offset(0f, 0f), Offset(w, w), px)
                        drawLine(fg, Offset(w, 0f), Offset(0f, w), px)
                    }
                }
            }
        }
    }
}

/** Uchwyty zmiany rozmiaru okna bez ramki: krawędzie i rogi. */
@Composable
fun ResizeHandles(window: Window) {
    val e = 5.dp
    val c = 12.dp
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(e).resizer(window, EDGE_T, Cursor.N_RESIZE_CURSOR))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(e).resizer(window, EDGE_B, Cursor.S_RESIZE_CURSOR))
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(e).resizer(window, EDGE_L, Cursor.W_RESIZE_CURSOR))
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(e).resizer(window, EDGE_R, Cursor.E_RESIZE_CURSOR))
        Box(Modifier.align(Alignment.TopStart).size(c).resizer(window, EDGE_T or EDGE_L, Cursor.NW_RESIZE_CURSOR))
        Box(Modifier.align(Alignment.TopEnd).size(c).resizer(window, EDGE_T or EDGE_R, Cursor.NE_RESIZE_CURSOR))
        Box(Modifier.align(Alignment.BottomStart).size(c).resizer(window, EDGE_B or EDGE_L, Cursor.SW_RESIZE_CURSOR))
        Box(Modifier.align(Alignment.BottomEnd).size(c).resizer(window, EDGE_B or EDGE_R, Cursor.SE_RESIZE_CURSOR))
    }
}

private const val EDGE_L = 1
private const val EDGE_T = 2
private const val EDGE_R = 4
private const val EDGE_B = 8

private fun Modifier.resizer(window: Window, edges: Int, cursor: Int): Modifier =
    this.pointerHoverIcon(PointerIcon(Cursor(cursor))).pointerInput(edges) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
            val b = window.bounds
            val min = window.minimumSize
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.none { it.pressed }) break
                ev.changes.forEach { it.consume() }
                val p = MouseInfo.getPointerInfo()?.location ?: continue
                val dx = p.x - start.x
                val dy = p.y - start.y
                var x = b.x
                var y = b.y
                var w = b.width
                var h = b.height
                if (edges and EDGE_L != 0) {
                    w = max(min.width, b.width - dx)
                    x = b.x + b.width - w
                }
                if (edges and EDGE_R != 0) w = max(min.width, b.width + dx)
                if (edges and EDGE_T != 0) {
                    h = max(min.height, b.height - dy)
                    y = b.y + b.height - h
                }
                if (edges and EDGE_B != 0) h = max(min.height, b.height + dy)
                window.setBounds(x, y, w, h)
            }
        }
    }
