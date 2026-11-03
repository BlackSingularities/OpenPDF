@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatTextdirectionRToL
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WidthNormal
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.Dialog
import com.blacksingularities.openpdf.desktop.DocTab
import com.blacksingularities.openpdf.desktop.Flow
import com.blacksingularities.openpdf.desktop.PageLayout
import com.blacksingularities.openpdf.desktop.Paper
import com.blacksingularities.openpdf.desktop.TabStatus
import com.blacksingularities.openpdf.desktop.UpdateStatus
import com.blacksingularities.openpdf.desktop.WindowsChrome
import com.blacksingularities.openpdf.desktop.ZoomMode
import kotlinx.coroutines.launch
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Window
import kotlin.math.max
import kotlin.math.roundToInt

private val TitleH = 40.dp

/**
 * Pasek tytułu okna: logo, karty dokumentów, pusty obszar do przeciągania okna (dwuklik –
 * maksymalizacja), aktualizacja, menu oraz przyciski minimalizacji, maksymalizacji i zamknięcia.
 */
@Composable
fun TitleBar(app: AppState, scope: FrameWindowScope, state: WindowState, onClose: () -> Unit) {
    val window = scope.window
    val maximized = state.placement == WindowPlacement.Maximized
    Row(Modifier.fillMaxWidth().height(TitleH).background(P.bg), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.fillMaxHeight().windowDrag(state, window).padding(start = 12.dp, end = 10.dp), contentAlignment = Alignment.Center) {
            Image(painterResource("icon.png"), null, Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)))
        }
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            app.tabs.forEachIndexed { i, t -> TabChip(app, t, i) }
        }
        Spacer(Modifier.width(4.dp))
        IconBtn(Icons.Rounded.Add, L("Otwórz dokumenty", "Open documents"), { app.pickFiles(window) }, shortcut = "Ctrl+O")
        // Pusty obszar paska – przeciąganie okna.
        Box(Modifier.weight(1f).fillMaxHeight().windowDrag(state, window).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (app.tabs.isEmpty()) Txt("OpenDocument", color = P.text2, weight = FontWeight.Medium)
        }
        val upd = app.update
        if (upd is UpdateStatus.Available && app.settings.skippedVersion != upd.info.version) {
            Btn(L("Aktualizacja {0}", "Update {0}").replace("{0}", upd.info.version), Icons.Rounded.SystemUpdateAlt, { app.dialog = Dialog.UPDATE }, kind = BtnKind.PRIMARY, height = 26.dp)
            Spacer(Modifier.width(6.dp))
        } else if (upd is UpdateStatus.Downloading) {
            Btn("${(upd.progress * 100).roundToInt()}%", Icons.Rounded.Download, { app.dialog = Dialog.UPDATE }, height = 26.dp)
            Spacer(Modifier.width(6.dp))
        }
        MainMenu(app, window)
        Spacer(Modifier.width(6.dp))
        WindowButton(WinGlyph.MIN, L("Minimalizuj", "Minimize")) { state.isMinimized = true }
        WindowButton(if (maximized) WinGlyph.RESTORE else WinGlyph.MAX, if (maximized) L("Przywróć", "Restore") else L("Maksymalizuj", "Maximize")) {
            toggleMaximize(state, window)
        }
        WindowButton(WinGlyph.CLOSE, L("Zamknij", "Close"), onClick = onClose)
    }
}

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
private fun Modifier.windowDrag(state: WindowState, window: Window): Modifier = this.pointerInput(state, window) {
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

private enum class WinGlyph { MIN, MAX, RESTORE, CLOSE }

/** Przycisk okna w stylu Windows 11: cienkie znaki, czerwone tło zamykania. */
@Composable
private fun WindowButton(glyph: WinGlyph, tip: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val close = glyph == WinGlyph.CLOSE
    val fg = if (close && hovered) Color.White else P.text
    Tip(tip) {
        Box(
            Modifier.size(46.dp, TitleH)
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

@Composable
private fun TabChip(app: AppState, t: DocTab, index: Int) {
    val on = app.active == t
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }
    Box {
        Tip(t.file.absolutePath) {
            Row(
                Modifier.height(28.dp).widthIn(min = 90.dp, max = 220.dp).clip(Pill)
                    .background(if (on) P.sel else if (hovered) P.raised2 else P.surface)
                    .border(1.dp, if (on) P.sel else P.line, Pill)
                    .hoverable(src)
                    .onPointerEvent(PointerEventType.Press) { e ->
                        when (e.button) {
                            PointerButton.Tertiary -> app.close(t)
                            PointerButton.Secondary -> menu = true
                            else -> app.active = t
                        }
                    }
                    .pointerInput(index) {
                        detectDragGestures(onDragEnd = { dragX = 0f }, onDragCancel = { dragX = 0f }) { ch, d ->
                            ch.consume()
                            dragX += d.x
                            val step = 110f
                            if (dragX > step && index < app.tabs.size - 1) {
                                app.moveTab(index, index + 1)
                                dragX = 0f
                            } else if (dragX < -step && index > 0) {
                                app.moveTab(index, index - 1)
                                dragX = 0f
                            }
                        }
                    }
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val fg = if (on) P.selText else P.text2
                val st = t.status
                if (st is TabStatus.Loading) {
                    Spinner(Modifier.size(12.dp), fg)
                } else {
                    Icon(iconFor(t), null, Modifier.size(14.dp), tint = if (st is TabStatus.Failed) P.err else fg)
                }
                Txt(t.name, Modifier.weight(1f, fill = false), style = Type.small, color = fg, weight = if (on) FontWeight.SemiBold else null)
                val cs = remember { MutableInteractionSource() }
                val ch by cs.collectIsHoveredAsState()
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(if (ch) (if (on) Color.Black.copy(alpha = 0.12f) else P.raised2) else Color.Transparent)
                        .hoverable(cs).pointerHoverIcon(PointerIcon.Hand).clickable { app.close(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, L("Zamknij", "Close"), Modifier.size(13.dp).alpha(if (on || hovered) 1f else 0.5f), tint = fg)
                }
            }
        }
        Menu(menu, { menu = false }, alignEnd = false, width = 230.dp) {
            MenuItem(L("Zamknij", "Close"), Icons.Rounded.Close, "Ctrl+W") { menu = false; app.close(t) }
            MenuItem(L("Zamknij pozostałe", "Close others"), enabled = app.tabs.size > 1) { menu = false; app.closeOthers(t) }
            MenuItem(L("Zamknij wszystkie", "Close all")) { menu = false; app.closeAll() }
            MenuSeparator()
            MenuItem(L("Pokaż w folderze", "Show in folder"), Icons.Rounded.FolderOpen) {
                menu = false
                runCatching { ProcessBuilder("explorer.exe", "/select,", t.file.absolutePath).start() }
            }
            MenuItem(L("Kopiuj ścieżkę", "Copy path"), Icons.Rounded.ContentCopy) {
                menu = false
                copyToClipboard(t.file.absolutePath)
            }
        }
    }
}

private fun iconFor(t: DocTab) = when (t.file.extension.lowercase()) {
    "pdf" -> Icons.Rounded.Description
    "epub", "fb2", "mobi", "azw", "azw3", "prc", "djvu", "djv", "cbz" -> Icons.Rounded.MenuBook
    else -> Icons.Rounded.Article
}

@Composable
fun Spinner(modifier: Modifier, color: Color) {
    androidx.compose.material3.CircularProgressIndicator(modifier, color = color, strokeWidth = 1.6.dp, trackColor = color.copy(alpha = 0.18f))
}

@Composable
private fun MainMenu(app: AppState, window: java.awt.Frame) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconBtn(Icons.Rounded.MoreHoriz, L("Menu", "Menu"), { open = true }, active = open)
        Menu(open, { open = false }) {
            val t = app.active
            MenuItem(L("Otwórz…", "Open…"), Icons.Rounded.FolderOpen, "Ctrl+O") { open = false; app.pickFiles(window) }
            MenuItem(L("Drukuj…", "Print…"), Icons.Rounded.Print, "Ctrl+P", enabled = t?.ready == true) { open = false; t?.let { print(app, it) } }
            MenuItem(L("Właściwości dokumentu", "Document properties"), Icons.Rounded.Info, "Ctrl+D", enabled = t?.ready == true) { open = false; app.dialog = Dialog.PROPERTIES }
            MenuSeparator()
            MenuItem(L("Prezentacja", "Presentation"), Icons.Rounded.Slideshow, "F5", enabled = t?.ready == true) { open = false; app.presentation = true }
            MenuItem(L("Pełny ekran", "Full screen"), Icons.Rounded.Fullscreen, "F11", enabled = t?.ready == true) { open = false; app.fullscreen = true }
            MenuSeparator()
            MenuItem(L("Ustawienia", "Settings"), Icons.Rounded.Settings, "Ctrl+,") { open = false; app.dialog = Dialog.SETTINGS }
            MenuItem(L("Skróty klawiszowe", "Keyboard shortcuts"), Icons.Rounded.Keyboard, "F1") { open = false; app.dialog = Dialog.SHORTCUTS }
            MenuItem(L("Sprawdź aktualizacje", "Check for updates"), Icons.Rounded.SystemUpdateAlt) { open = false; app.checkUpdates(manual = true) }
            MenuItem(L("O programie", "About"), Icons.Rounded.Info) { open = false; app.dialog = Dialog.ABOUT }
        }
    }
}

/** Pasek narzędzi dokumentu: panel boczny i narzędzie | strony i powiększenie | układ i papier. */
@Composable
fun Toolbar(app: AppState, tab: DocTab, handle: ViewerHandle) {
    val st = app.settings
    Row(
        Modifier.fillMaxWidth().height(42.dp).background(P.panel).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBtn(Icons.Rounded.ViewSidebar, L("Panel boczny", "Sidebar"), { st.sidebar = !st.sidebar; app.saveSoon() }, active = st.sidebar, shortcut = "Ctrl+B")
            Seg(
                listOf(
                    SegItem(Tool.SELECT, icon = Icons.Rounded.TextFields, tip = L("Zaznaczanie tekstu", "Select text"), shortcut = "V"),
                    SegItem(Tool.HAND, icon = Icons.Rounded.PanTool, tip = L("Przesuwanie (rączka)", "Pan (hand)"), shortcut = "H"),
                ),
                handle.tool, { handle.tool = it },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            IconBtn(Icons.Rounded.ChevronLeft, L("Poprzednia strona", "Previous page"), { tab.step(-1) }, enabled = tab.rowOf(tab.page) > 0, shortcut = "PgUp")
            PageField(app, tab)
            IconBtn(Icons.Rounded.ChevronRight, L("Następna strona", "Next page"), { tab.step(1) }, enabled = tab.rowOf(tab.page) < tab.rowOf(tab.pageCount - 1), shortcut = "PgDn")
            VDivider()
            IconBtn(Icons.Rounded.Remove, L("Pomniejsz", "Zoom out"), { zoomStep(tab, -1) }, shortcut = "Ctrl+−")
            ZoomMenu(tab)
            IconBtn(Icons.Rounded.Add, L("Powiększ", "Zoom in"), { zoomStep(tab, 1) }, shortcut = "Ctrl++")
            Seg(
                listOf(
                    SegItem(ZoomMode.FIT_WIDTH, icon = Icons.Rounded.WidthNormal, tip = L("Dopasuj do szerokości", "Fit width"), shortcut = "Ctrl+2"),
                    SegItem(ZoomMode.FIT_PAGE, icon = Icons.Rounded.FitScreen, tip = L("Cała strona", "Fit page"), shortcut = "Ctrl+1"),
                ),
                tab.zoomMode, { tab.zoomMode = it },
            )
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
            Seg(
                listOf(
                    SegItem(PageLayout.SINGLE, icon = Icons.Rounded.Article, tip = L("Jedna strona", "Single page"), shortcut = "1"),
                    SegItem(PageLayout.SPREAD, icon = Icons.Rounded.ViewCarousel, tip = L("Dwie strony", "Two pages"), shortcut = "2"),
                    SegItem(PageLayout.BOOK, icon = Icons.Rounded.AutoStories, tip = L("Książka (okładka osobno)", "Book (cover alone)"), shortcut = "B"),
                ),
                tab.layout, { setLayout(app, tab, it) },
            )
            IconBtn(
                if (tab.flow == Flow.CONTINUOUS) Icons.Rounded.ViewAgenda else Icons.Rounded.MenuBook,
                if (tab.flow == Flow.CONTINUOUS) L("Przewijanie ciągłe – przełącz na strona po stronie", "Continuous scrolling – switch to page by page")
                else L("Strona po stronie – przełącz na przewijanie ciągłe", "Page by page – switch to continuous scrolling"),
                { setFlow(app, tab, if (tab.flow == Flow.CONTINUOUS) Flow.PAGED else Flow.CONTINUOUS) },
                active = tab.flow == Flow.PAGED,
                shortcut = "C",
            )
            IconBtn(
                Icons.Rounded.FormatTextdirectionRToL, L("Od prawej do lewej (manga)", "Right to left (manga)"),
                { tab.rtl = !tab.rtl; st.rtl = tab.rtl; app.saveSoon() },
                active = tab.rtl, enabled = tab.layout != PageLayout.SINGLE, shortcut = "R",
            )
            IconBtn(Icons.Rounded.RotateRight, L("Obróć widok", "Rotate view"), { tab.rotation = (tab.rotation + 90) % 360 }, active = tab.rotation != 0, shortcut = "Ctrl+R")
            VDivider()
            PaperButton(app)
            IconBtn(Icons.Rounded.Fullscreen, L("Pełny ekran", "Full screen"), { app.fullscreen = !app.fullscreen }, shortcut = "F11")
        }
    }
}

fun setLayout(app: AppState, tab: DocTab, l: PageLayout) {
    val p = tab.page
    val was = tab.layout
    tab.layout = l
    app.settings.layout = l
    // Rozkładówki najlepiej widać w całości; pojedyncza strona w ciągłym przewijaniu – na szerokość.
    if (l != PageLayout.SINGLE && was == PageLayout.SINGLE && tab.zoomMode == ZoomMode.FIT_WIDTH) tab.zoomMode = ZoomMode.FIT_PAGE
    if (l == PageLayout.SINGLE && was != PageLayout.SINGLE && tab.zoomMode == ZoomMode.FIT_PAGE && tab.flow == Flow.CONTINUOUS) tab.zoomMode = ZoomMode.FIT_WIDTH
    tab.goTo(p)
    app.saveSoon()
}

fun setFlow(app: AppState, tab: DocTab, f: Flow) {
    val p = tab.page
    tab.flow = f
    app.settings.flow = f
    if (f == Flow.PAGED && tab.zoomMode == ZoomMode.FIT_WIDTH) tab.zoomMode = ZoomMode.FIT_PAGE
    if (f == Flow.CONTINUOUS && tab.zoomMode == ZoomMode.FIT_PAGE && tab.layout == PageLayout.SINGLE) tab.zoomMode = ZoomMode.FIT_WIDTH
    tab.goTo(p)
    app.saveSoon()
}

fun zoomStep(tab: DocTab, dir: Int) {
    val cur = tab.effectiveZoom
    val next = if (dir > 0) ZOOM_STEPS.firstOrNull { it > cur * 1.01f } ?: ZOOM_STEPS.last()
    else ZOOM_STEPS.lastOrNull { it < cur * 0.99f } ?: ZOOM_STEPS.first()
    tab.zoomMode = ZoomMode.CUSTOM
    tab.zoom = next
}

@Composable
private fun PageField(app: AppState, tab: DocTab) {
    var text by remember(tab.page) { mutableStateOf((tab.page + 1).toString()) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(app.focusPage) { if (app.focusPage > 0) runCatching { focus.requestFocus() } }
    Row(
        Modifier.height(28.dp).background(P.surface, Pill).border(1.dp, if (focused) P.sel else P.line2, Pill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            text, { v -> text = v.filter { it.isDigit() }.take(6) },
            Modifier.width(36.dp)
                .focusRequester(focus)
                .onFocusChanged {
                    focused = it.isFocused
                    app.typing = it.isFocused
                    if (!it.isFocused) text = (tab.page + 1).toString()
                }
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                        text.toIntOrNull()?.let { n -> tab.goTo(n - 1) }
                        true
                    } else false
                },
            singleLine = true,
            textStyle = Type.body.copy(color = P.text, textAlign = TextAlign.End, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(P.text),
        )
        Txt(" / ${tab.pageCount}", color = P.text3)
    }
}

@Composable
private fun ZoomMenu(tab: DocTab) {
    var open by remember { mutableStateOf(false) }
    Box {
        Btn("${(tab.effectiveZoom * 100).roundToInt()}%", null, { open = true }, Modifier.width(62.dp), kind = BtnKind.GHOST, tip = L("Powiększenie", "Zoom"))
        Menu(open, { open = false }, alignEnd = false, width = 200.dp) {
            MenuItem(L("Dopasuj do szerokości", "Fit width"), shortcut = "Ctrl+2", checked = tab.zoomMode == ZoomMode.FIT_WIDTH) { open = false; tab.zoomMode = ZoomMode.FIT_WIDTH }
            MenuItem(L("Cała strona", "Fit page"), shortcut = "Ctrl+1", checked = tab.zoomMode == ZoomMode.FIT_PAGE) { open = false; tab.zoomMode = ZoomMode.FIT_PAGE }
            MenuItem(L("Rozmiar rzeczywisty", "Actual size"), shortcut = "Ctrl+0", checked = tab.zoomMode == ZoomMode.CUSTOM && tab.zoom == 1f) { open = false; tab.zoomMode = ZoomMode.CUSTOM; tab.zoom = 1f }
            MenuSeparator()
            for (z in floatArrayOf(0.5f, 0.75f, 1.25f, 1.5f, 2f, 3f, 4f)) {
                MenuItem("${(z * 100).roundToInt()}%", checked = tab.zoomMode == ZoomMode.CUSTOM && tab.zoom == z) { open = false; tab.zoomMode = ZoomMode.CUSTOM; tab.zoom = z }
            }
        }
    }
}

@Composable
private fun PaperButton(app: AppState) {
    var open by remember { mutableStateOf(false) }
    val st = app.settings
    Box {
        IconBtn(
            when (st.paper) {
                Paper.NORMAL -> Icons.Rounded.WbSunny
                Paper.SEPIA -> Icons.Rounded.Coffee
                Paper.NIGHT -> Icons.Rounded.Brightness2
            },
            L("Papier", "Paper"), { open = true }, active = open || st.paper != Paper.NORMAL, shortcut = "N",
        )
        Menu(open, { open = false }, width = 190.dp) {
            MenuItem(L("Zwykły", "Normal"), Icons.Rounded.WbSunny, checked = st.paper == Paper.NORMAL) { open = false; st.paper = Paper.NORMAL; app.saveSoon() }
            MenuItem(L("Sepia", "Sepia"), Icons.Rounded.Coffee, checked = st.paper == Paper.SEPIA) { open = false; st.paper = Paper.SEPIA; app.saveSoon() }
            MenuItem(L("Nocny", "Night"), Icons.Rounded.Brightness2, checked = st.paper == Paper.NIGHT) { open = false; st.paper = Paper.NIGHT; app.saveSoon() }
        }
    }
}

fun copyToClipboard(s: String) {
    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(s), null) }
}

/** Drukowanie systemowym oknem drukowania (PDFBox). */
fun print(app: AppState, tab: DocTab) {
    val f = tab.pdf ?: return
    Thread {
        runCatching {
            com.blacksingularities.openpdf.desktop.engine.DesktopPdfEngine.load(f).use { doc ->
                val job = java.awt.print.PrinterJob.getPrinterJob()
                job.jobName = tab.name
                job.setPageable(org.apache.pdfbox.printing.PDFPageable(doc))
                if (job.printDialog()) job.print()
            }
        }.onFailure { e ->
            app.scope.launch { app.showToast(L("Drukowanie nie powiodło się", "Printing failed") + ": " + (e.message ?: ""), true) }
        }
    }.apply { isDaemon = true }.start()
}
