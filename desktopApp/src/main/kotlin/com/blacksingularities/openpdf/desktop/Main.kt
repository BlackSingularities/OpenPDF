package com.blacksingularities.openpdf.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.blacksingularities.openpdf.desktop.ui.AppContent
import com.blacksingularities.openpdf.desktop.ui.AppTheme
import com.blacksingularities.openpdf.desktop.ui.ViewerHandle
import com.blacksingularities.openpdf.desktop.ui.targets
import com.blacksingularities.openpdf.desktop.ui.copyToClipboard
import com.blacksingularities.openpdf.desktop.ui.palette
import com.blacksingularities.openpdf.desktop.ui.print
import com.blacksingularities.openpdf.desktop.ui.scrollByAnimated
import com.blacksingularities.openpdf.desktop.ui.setFlow
import com.blacksingularities.openpdf.desktop.ui.setLayout
import com.blacksingularities.openpdf.desktop.ui.zoomStep
import kotlinx.coroutines.launch
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "DIRECT3D")
    val files = args.map { File(it) }.filter { it.exists() }
    // Drugie uruchomienie (np. dwuklik pliku) przekazuje pliki do działającego okna i kończy się.
    if (SingleInstance.forward(files)) exitProcess(0)
    val app = AppState()
    SingleInstance.listen { received ->
        SwingUtilities.invokeLater {
            app.scope.launch {
                if (received.isNotEmpty()) app.open(received)
                app.raise++
            }
        }
    }
    app.restoreSession()
    if (files.isNotEmpty()) app.open(files)
    app.startUpdateChecks()
    application {
        val wb = app.settings.window
        val state = rememberWindowState(
            placement = if (wb.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = if (wb.x >= 0 && wb.y >= 0) WindowPosition(wb.x.dp, wb.y.dp) else WindowPosition.PlatformDefault,
            size = DpSize(wb.w.dp, wb.h.dp),
        )
        val handles = remember { HashMap<Int, ViewerHandle>() }
        val frame = remember { arrayOfNulls<Frame>(1) }
        val title = app.active?.let { "${it.name} – OpenDocument" } ?: "OpenDocument"
        val exit = {
            saveWindow(app, state)
            app.persist()
            app.tabs.toList().forEach { it.close() }
            exitApplication()
        }
        // Zamknięcie okna: najpierw pytanie o niezapisane zmiany.
        val quit: () -> Unit = {
            if (app.tabs.none { it.unsaved }) exit()
            else {
                app.persist()
                app.requestCloseMany(app.tabs.toList()) { exit() }
            }
        }
        // Okno bez systemowej ramki: karty leżą w jednym pasku z przyciskami okna.
        Window(
            onCloseRequest = quit,
            state = state,
            title = title,
            icon = painterResource("icon.png"),
            undecorated = true,
            onPreviewKeyEvent = { e -> handleKey(app, handles, e, frame[0]) },
        ) {
            val win = window
            frame[0] = win
            app.window = win
            LaunchedEffect(Unit) {
                win.minimumSize = java.awt.Dimension(900, 560)
                WindowsChrome.install(win)
                installDrop(app, win)
            }
            LaunchedEffect(app.settings.theme, app.settings.accent) {
                val p = palette(app.settings.theme, app.settings.accent)
                win.background = java.awt.Color(p.bg.toArgb())
            }
            // Pełny ekran i prezentacja.
            val full = app.fullscreen || app.presentation
            LaunchedEffect(full) {
                if (!full) WindowsChrome.updateMaximizedBounds(win)
                state.placement = if (full) WindowPlacement.Fullscreen else if (app.settings.window.maximized) WindowPlacement.Maximized else WindowPlacement.Floating
            }
            LaunchedEffect(state.placement, state.position, state.size) {
                if (!full) saveWindow(app, state)
            }
            LaunchedEffect(app.raise) {
                if (app.raise > 0) {
                    if (win.extendedState and Frame.ICONIFIED != 0) win.extendedState = win.extendedState and Frame.ICONIFIED.inv()
                    win.toFront()
                    win.requestFocus()
                }
            }
            AppTheme(app.settings.theme, app.settings.accent) {
                AppContent(app, this, state, handles, onClose = quit)
            }
        }
    }
}

private fun saveWindow(app: AppState, state: androidx.compose.ui.window.WindowState) {
    val max = state.placement == WindowPlacement.Maximized
    val old = app.settings.window
    val pos = state.position
    app.settings.window = if (max || state.placement == WindowPlacement.Fullscreen) old.copy(maximized = max)
    else Settings.WindowBounds(
        if (pos.isSpecified) pos.x.value.toInt() else old.x,
        if (pos.isSpecified) pos.y.value.toInt() else old.y,
        state.size.width.value.toInt(), state.size.height.value.toInt(), false,
    )
}

/** Przeciąganie plików z Eksploratora do okna. */
private fun installDrop(app: AppState, win: java.awt.Window) {
    val target = object : DropTargetAdapter() {
        override fun dragEnter(e: DropTargetDragEvent) {
            if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                e.acceptDrag(DnDConstants.ACTION_COPY)
                app.dragOver = true
            } else e.rejectDrag()
        }

        override fun dragExit(e: DropTargetEvent) {
            app.dragOver = false
        }

        override fun drop(e: DropTargetDropEvent) {
            app.dragOver = false
            e.acceptDrop(DnDConstants.ACTION_COPY)
            val files = runCatching {
                @Suppress("UNCHECKED_CAST")
                e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            }.getOrDefault(emptyList())
            e.dropComplete(true)
            if (files.isNotEmpty()) app.scope.launch { app.open(files) }
        }
    }
    (win as? javax.swing.JFrame)?.contentPane?.let { DropTarget(it, DnDConstants.ACTION_COPY, target, true) }
    DropTarget(win, DnDConstants.ACTION_COPY, target, true)
}

/** Skróty klawiszowe całego okna. */
private fun handleKey(app: AppState, handles: Map<Int, ViewerHandle>, e: KeyEvent, window: Frame?): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ctrl = e.isCtrlPressed
    val shift = e.isShiftPressed
    val tab = app.active
    val ready = tab?.ready == true
    val handle = tab?.let { handles[it.id] }
    // Okna dialogowe: tylko Esc.
    if (app.dialog != Dialog.NONE) {
        if (e.key == Key.Escape) {
            when (app.dialog) {
                Dialog.UNSAVED -> app.resolveClosing(null)
                Dialog.INPUT -> {
                    app.input = null
                    app.dialog = Dialog.NONE
                }
                else -> app.dialog = Dialog.NONE
            }
            return true
        }
        return false
    }
    if (ctrl) {
        when (e.key) {
            Key.O -> {
                if (shift) {
                    if (ready && tab != null) tab.organize = !tab.organize
                } else {
                    app.pickFiles(window)
                }
                return true
            }
            Key.W, Key.F4 -> { tab?.let { app.requestClose(it) }; return true }
            Key.Tab -> { app.cycleTab(if (shift) -1 else 1); return true }
            Key.Comma -> { app.dialog = Dialog.SETTINGS; return true }
        }
        val digit = digitOf(e.key)
        if (digit != null && shift) {
            app.tabs.getOrNull(digit - 1)?.let { app.active = it }
            return true
        }
        if (!ready || tab == null) return false
        if (app.typing && e.key in setOf(Key.A, Key.C, Key.Z, Key.Y, Key.DirectionLeft, Key.DirectionRight)) return false
        when (e.key) {
            Key.S -> if (shift) app.saveAs(tab) else app.save(tab)
            Key.Z -> if (shift) tab.redo() else tab.undo()
            Key.Y -> tab.redo()
            Key.Equals, Key.Plus, Key.NumPadAdd -> zoomStep(tab, 1)
            Key.Minus, Key.NumPadSubtract -> zoomStep(tab, -1)
            Key.Zero, Key.NumPad0 -> { tab.zoomMode = ZoomMode.CUSTOM; tab.zoom = 1f }
            Key.One, Key.NumPad1 -> tab.zoomMode = ZoomMode.FIT_PAGE
            Key.Two, Key.NumPad2 -> tab.zoomMode = ZoomMode.FIT_WIDTH
            Key.F -> { tab.organize = false; app.settings.sidebar = true; app.settings.sideTab = SideTab.SEARCH; app.focusSearch++ }
            Key.G -> { tab.organize = false; app.focusPage++ }
            Key.B -> { app.settings.sidebar = !app.settings.sidebar; app.saveSoon() }
            Key.D -> tab.toggleBookmark()
            Key.P -> print(app, tab)
            Key.I -> app.dialog = Dialog.PROPERTIES
            Key.R -> tab.rotation = (tab.rotation + if (shift) 270 else 90) % 360
            Key.MoveHome -> tab.goTo(0)
            Key.MoveEnd -> tab.goTo(tab.pageCount - 1)
            Key.DirectionLeft -> if (tab.organize) tab.shiftPages(tab.targets(), -1) else return false
            Key.DirectionRight -> if (tab.organize) tab.shiftPages(tab.targets(), 1) else return false
            Key.C -> {
                val sel = tab.selection ?: return false
                val t = tab.pageTexts[sel.page] ?: return false
                copyToClipboard(t.substring(sel.range))
                app.showToast(com.blacksingularities.openpdf.desktop.ui.L("Skopiowano tekst", "Text copied"))
            }
            Key.A -> {
                if (tab.organize) {
                    tab.picked.clear()
                    tab.picked.addAll(0 until tab.pageCount)
                    return true
                }
                val p = tab.page
                tab.scope.launch {
                    val t = tab.pageText(p) ?: return@launch
                    if (t.length > 0) tab.selection = TextSelection(p, 0, t.length - 1)
                }
            }
            else -> return false
        }
        return true
    }
    when (e.key) {
        Key.F1 -> { app.dialog = Dialog.SHORTCUTS; return true }
        Key.F11 -> { if (ready) app.fullscreen = !app.fullscreen; return true }
        Key.F5 -> { if (ready) app.presentation = true; return true }
        Key.Escape -> {
            if (app.presentation || app.fullscreen) {
                app.presentation = false
                app.fullscreen = false
                return true
            }
            if (tab?.organize == true) {
                tab.organize = false
                return true
            }
            if (tab?.selection != null) {
                tab.selection = null
                return true
            }
            if (app.tool != Tool.SELECT) {
                app.tool = Tool.SELECT
                return true
            }
            return false
        }
    }
    if (!ready || tab == null) return false
    if (e.key == Key.F3) {
        tab.selectHit(tab.hitIndex + if (shift) -1 else 1)
        return true
    }
    if (app.typing) return false
    if (tab.organize) {
        when (e.key) {
            Key.Delete -> tab.deletePages(tab.targets())
            Key.DirectionRight, Key.DirectionDown -> tab.page = (tab.page + 1).coerceAtMost(tab.pageCount - 1).also { tab.picked.clear(); tab.picked.add(it) }
            Key.DirectionLeft, Key.DirectionUp -> tab.page = (tab.page - 1).coerceAtLeast(0).also { tab.picked.clear(); tab.picked.add(it) }
            else -> return false
        }
        return true
    }
    val paged = tab.flow == Flow.PAGED || app.presentation
    val vh = handle?.viewportH ?: 600f
    val smooth = app.settings.smoothScroll
    fun scroll(px: Float) = tab.scope.launch { tab.scrollByAnimated(px, smooth) }
    when (e.key) {
        Key.PageDown -> if (paged) tab.step(1) else scroll(vh * 0.9f)
        Key.PageUp -> if (paged) tab.step(-1) else scroll(-vh * 0.9f)
        Key.Spacebar -> if (paged) tab.step(if (shift) -1 else 1) else scroll(if (shift) -vh * 0.9f else vh * 0.9f)
        Key.DirectionDown -> if (paged) tab.step(1) else scroll(80f)
        Key.DirectionUp -> if (paged) tab.step(-1) else scroll(-80f)
        Key.DirectionRight -> {
            val d = if (tab.rtl && tab.layout != PageLayout.SINGLE) -1 else 1
            if (paged || tab.hscroll.maxValue == 0) tab.step(d) else tab.scope.launch { tab.hscroll.animateScrollTo(tab.hscroll.value + 120) }
        }
        Key.DirectionLeft -> {
            val d = if (tab.rtl && tab.layout != PageLayout.SINGLE) 1 else -1
            if (paged || tab.hscroll.maxValue == 0) tab.step(d) else tab.scope.launch { tab.hscroll.animateScrollTo(tab.hscroll.value - 120) }
        }
        Key.MoveHome -> tab.goTo(0)
        Key.MoveEnd -> tab.goTo(tab.pageCount - 1)
        Key.One -> setLayout(app, tab, PageLayout.SINGLE)
        Key.Two -> setLayout(app, tab, PageLayout.SPREAD)
        Key.B -> setLayout(app, tab, if (tab.layout == PageLayout.BOOK) PageLayout.SINGLE else PageLayout.BOOK)
        Key.C -> setFlow(app, tab, if (tab.flow == Flow.CONTINUOUS) Flow.PAGED else Flow.CONTINUOUS)
        Key.R -> if (tab.layout != PageLayout.SINGLE) {
            tab.rtl = !tab.rtl
            app.settings.rtl = tab.rtl
            app.saveSoon()
        } else return false
        Key.N -> {
            app.settings.paper = Paper.entries[(app.settings.paper.ordinal + 1) % Paper.entries.size]
            app.saveSoon()
        }
        Key.Delete -> {
            // Usuwa ostatni niezapisany element.
            val last = tab.marks.lastOrNull() ?: return false
            tab.removeMark(last.id)
        }
        else -> {
            val t = Tool.entries.firstOrNull { keyOf(it) == e.key } ?: return false
            app.tool = t
        }
    }
    return true
}

private fun keyOf(t: Tool): Key = when (t) {
    Tool.SELECT -> Key.V
    Tool.HAND -> Key.H
    Tool.MARKUP -> Key.U
    Tool.PEN -> Key.P
    Tool.MARKER -> Key.M
    Tool.SHAPE -> Key.S
    Tool.TEXT -> Key.T
    Tool.NOTE -> Key.K
    Tool.ERASER -> Key.E
}

private fun digitOf(k: Key): Int? = when (k) {
    Key.One -> 1
    Key.Two -> 2
    Key.Three -> 3
    Key.Four -> 4
    Key.Five -> 5
    Key.Six -> 6
    Key.Seven -> 7
    Key.Eight -> 8
    Key.Nine -> 9
    else -> null
}
