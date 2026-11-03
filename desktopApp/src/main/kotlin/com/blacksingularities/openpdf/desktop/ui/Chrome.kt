@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatTextdirectionRToL
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HighlightAlt
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Undo
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.blacksingularities.openpdf.desktop.INK_COLORS
import com.blacksingularities.openpdf.desktop.MARKER_COLORS
import com.blacksingularities.openpdf.desktop.PageLayout
import com.blacksingularities.openpdf.desktop.Paper
import com.blacksingularities.openpdf.desktop.SideTab
import com.blacksingularities.openpdf.desktop.TabStatus
import com.blacksingularities.openpdf.desktop.Tool
import com.blacksingularities.openpdf.desktop.UpdateStatus
import com.blacksingularities.openpdf.desktop.ZoomMode
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.ShapeKind
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TitleH = 38.dp

/**
 * Pasek tytułu okna: logo, karty dokumentów, pusty obszar do przeciągania okna (dwuklik –
 * maksymalizacja), aktualizacja, menu programu oraz przyciski okna.
 */
@Composable
fun TitleBar(app: AppState, scope: FrameWindowScope, state: WindowState, onClose: () -> Unit) {
    val window = scope.window
    val maximized = state.placement == WindowPlacement.Maximized
    Row(Modifier.fillMaxWidth().height(TitleH).background(P.bg), verticalAlignment = Alignment.CenterVertically) {
        // Przed kartami: menu programu i panel boczny.
        Spacer(Modifier.width(6.dp))
        MainMenu(app)
        val doc = app.active?.takeIf { it.ready && !it.organize }
        IconBtn(
            Icons.Rounded.ViewSidebar, L("Panel boczny", "Sidebar"),
            { app.settings.sidebar = !app.settings.sidebar; app.saveSoon() },
            active = doc != null && app.settings.sidebar, enabled = doc != null, shortcut = "Ctrl+B", size = 28.dp,
        )
        Box(Modifier.fillMaxHeight().width(10.dp).windowDrag(state, window))
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            app.tabs.forEachIndexed { i, t -> TabChip(app, t, i) }
        }
        Spacer(Modifier.width(3.dp))
        IconBtn(Icons.Rounded.Add, L("Otwórz dokumenty", "Open documents"), { app.pickFiles(window) }, shortcut = "Ctrl+O", size = 28.dp)
        // Pusty obszar paska – przeciąganie okna.
        Box(Modifier.weight(1f).fillMaxHeight().windowDrag(state, window).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (app.tabs.isEmpty()) Txt("OpenDocument", color = P.text2, weight = FontWeight.Medium)
        }
        val work = app.work
        if (work != null) {
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spinner(Modifier.size(13.dp), P.accent)
                Txt(work + (app.workProgress?.let { " ${(it * 100).roundToInt()}%" } ?: ""), style = Type.small, color = P.text2)
            }
        }
        val upd = app.update
        if (upd is UpdateStatus.Available && app.settings.skippedVersion != upd.info.version) {
            Btn(L("Aktualizacja {0}", "Update {0}").replace("{0}", upd.info.version), Icons.Rounded.SystemUpdateAlt, { app.dialog = Dialog.UPDATE }, kind = BtnKind.PRIMARY, height = 26.dp)
            Spacer(Modifier.width(6.dp))
        } else if (upd is UpdateStatus.Downloading) {
            Btn("${(upd.progress * 100).roundToInt()}%", Icons.Rounded.Download, { app.dialog = Dialog.UPDATE }, height = 26.dp)
            Spacer(Modifier.width(6.dp))
        }
        ThemeQuick(app)
        Spacer(Modifier.width(4.dp))
        WindowButton(WinGlyph.MIN, L("Minimalizuj", "Minimize"), TitleH) { state.isMinimized = true }
        WindowButton(if (maximized) WinGlyph.RESTORE else WinGlyph.MAX, if (maximized) L("Przywróć", "Restore") else L("Maksymalizuj", "Maximize"), TitleH) {
            toggleMaximize(state, window)
        }
        WindowButton(WinGlyph.CLOSE, L("Zamknij", "Close"), TitleH, onClick = onClose)
    }
}

/** Szybka zmiana motywu: AMOLED → grafit → jasny. */
@Composable
private fun ThemeQuick(app: AppState) {
    val st = app.settings
    val next = ThemeId.entries[(st.theme.ordinal + 1) % ThemeId.entries.size]
    IconBtn(
        when (st.theme) {
            ThemeId.DARK -> Icons.Rounded.Brightness2
            ThemeId.GRAY -> Icons.Rounded.Coffee
            ThemeId.LIGHT -> Icons.Rounded.WbSunny
        },
        L("Motyw: {0} – kliknij: {1}", "Theme: {0} – click: {1}").replace("{0}", st.theme.label).replace("{1}", next.label),
        { st.theme = next; app.saveSoon() },
        size = 28.dp,
    )
}

@Composable
private fun TabChip(app: AppState, t: DocTab, index: Int) {
    val on = app.active == t
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }
    val shape = RoundedCornerShape(8.dp)
    Box {
        Tip(t.file.absolutePath) {
            Row(
                Modifier.height(28.dp).widthIn(min = 96.dp, max = 230.dp).clip(shape)
                    .background(if (on) P.raised2 else if (hovered) P.raised else Color.Transparent)
                    .border(1.dp, if (on) P.line2 else Color.Transparent, shape)
                    .hoverable(src)
                    .onPointerEvent(PointerEventType.Press) { e ->
                        when (e.button) {
                            PointerButton.Tertiary -> app.requestClose(t)
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
                    .padding(start = 9.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val fg = if (on) P.text else P.text2
                val st = t.status
                if (st is TabStatus.Loading || t.busy != null) {
                    Spinner(Modifier.size(12.dp), P.accent)
                } else {
                    Icon(iconFor(t), null, Modifier.size(14.dp), tint = if (st is TabStatus.Failed) P.err else if (on) P.accent else fg)
                }
                Txt(t.name, Modifier.weight(1f, fill = false), style = Type.small, color = fg, weight = if (on) FontWeight.SemiBold else null)
                val cs = remember { MutableInteractionSource() }
                val ch by cs.collectIsHoveredAsState()
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(if (ch) P.line2 else Color.Transparent)
                        .hoverable(cs).pointerHoverIcon(PointerIcon.Hand).clickable { app.requestClose(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (t.unsaved && !ch) {
                        // Niezapisane zmiany: kropka w miejscu krzyżyka.
                        Box(Modifier.size(8.dp).background(P.accent, CircleShape))
                    } else {
                        Icon(Icons.Rounded.Close, L("Zamknij", "Close"), Modifier.size(13.dp).alpha(if (on || hovered || ch) 1f else 0.45f), tint = fg)
                    }
                }
            }
        }
        Menu(menu, { menu = false }, alignEnd = false, width = 230.dp) {
            MenuItem(L("Zapisz", "Save"), Icons.Rounded.Save, "Ctrl+S", enabled = t.ready && t.unsaved) { menu = false; app.save(t) }
            MenuItem(L("Zapisz jako…", "Save as…"), Icons.Rounded.SaveAs, enabled = t.ready) { menu = false; app.saveAs(t) }
            MenuSeparator()
            MenuItem(L("Zamknij", "Close"), Icons.Rounded.Close, "Ctrl+W") { menu = false; app.requestClose(t) }
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

/** Menu programu (przycisk ☰ w lewym górnym rogu): plik, tworzenie dokumentów, widok, program. */
@Composable
private fun MainMenu(app: AppState) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconBtn(Icons.Rounded.Menu, L("Menu", "Menu"), { open = !open }, active = open, size = 28.dp)
        Menu(open, { open = false }, alignEnd = false, width = 290.dp) {
            val t = app.active
            val ready = t?.ready == true
            fun go(a: () -> Unit) {
                open = false
                a()
            }
            MenuHeader(L("Plik", "File"))
            MenuItem(L("Otwórz…", "Open…"), Icons.Rounded.FolderOpen, "Ctrl+O") { go { app.pickFiles() } }
            MenuItem(L("Zapisz", "Save"), Icons.Rounded.Save, "Ctrl+S", enabled = ready && t?.unsaved == true) { go { app.save() } }
            MenuItem(L("Zapisz jako…", "Save as…"), Icons.Rounded.SaveAs, "Ctrl+Shift+S", enabled = ready) { go { app.saveAs() } }
            MenuItem(L("Drukuj…", "Print…"), Icons.Rounded.Print, "Ctrl+P", enabled = ready) { go { t?.let { print(app, it) } } }
            MenuItem(L("Zamknij kartę", "Close tab"), Icons.Rounded.Close, "Ctrl+W", enabled = t != null) { go { t?.let { app.requestClose(it) } } }
            MenuSeparator()
            MenuHeader(L("Nowy dokument", "New document"))
            MenuItem(L("Pusty dokument…", "Blank document…"), Icons.Rounded.NoteAdd) { go { app.dialog = Dialog.NEW_DOC } }
            MenuItem(L("PDF z obrazów…", "PDF from images…"), Icons.Rounded.Image) { go { app.imagesToPdf() } }
            MenuItem(L("Scal pliki PDF…", "Merge PDF files…"), Icons.Rounded.MergeType) { go { app.mergePdfs() } }
            MenuSeparator()
            MenuHeader(L("Widok", "View"))
            MenuItem(L("Pełny ekran", "Full screen"), Icons.Rounded.Fullscreen, "F11", enabled = ready) { go { app.fullscreen = true } }
            MenuItem(L("Prezentacja", "Presentation"), Icons.Rounded.Slideshow, "F5", enabled = ready) { go { app.presentation = true } }
            MenuSeparator()
            MenuHeader(L("Program", "Program"))
            MenuItem(L("Ustawienia", "Settings"), Icons.Rounded.Settings, "Ctrl+,") { go { app.dialog = Dialog.SETTINGS } }
            MenuItem(L("Skróty klawiszowe", "Keyboard shortcuts"), Icons.Rounded.Keyboard, "F1") { go { app.dialog = Dialog.SHORTCUTS } }
            MenuItem(L("Sprawdź aktualizacje", "Check for updates"), Icons.Rounded.SystemUpdateAlt) { go { app.checkUpdates(manual = true) } }
            MenuItem(L("O programie", "About"), Icons.Rounded.Info) { go { app.dialog = Dialog.ABOUT } }
        }
    }
}

fun toolIcon(t: Tool): ImageVector = when (t) {
    Tool.SELECT -> Icons.Rounded.HighlightAlt
    Tool.HAND -> Icons.Rounded.PanTool
    Tool.MARKUP -> Icons.Rounded.FormatColorText
    Tool.PEN -> Icons.Rounded.Edit
    Tool.MARKER -> Icons.Rounded.BorderColor
    Tool.SHAPE -> Icons.Rounded.Category
    Tool.TEXT -> Icons.Rounded.TextFields
    Tool.NOTE -> Icons.Rounded.StickyNote2
    Tool.ERASER -> Icons.Rounded.CleaningServices
}

/**
 * Pasek dokumentu w jednym wierszu: po lewej nawigacja (strony, powiększenie), po prawej wszystkie
 * narzędzia – narzędzie i styl, cofanie, zakładka, szukanie, widok, dokument i zapis. Na wąskim oknie
 * etykiety chowają się, zostają ikony.
 */
@Composable
fun Toolbar(app: AppState, tab: DocTab) {
    val st = app.settings
    BoxWithConstraints(Modifier.fillMaxWidth().height(42.dp).background(P.panel)) {
        val wide = maxWidth > 1180.dp
        val medium = maxWidth > 940.dp
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Nawigacja.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!tab.organize) {
                    IconBtn(Icons.Rounded.ChevronLeft, L("Poprzednia strona", "Previous page"), { tab.step(-1) }, enabled = tab.rowOf(tab.page) > 0, shortcut = "PgUp")
                    PageField(app, tab)
                    IconBtn(Icons.Rounded.ChevronRight, L("Następna strona", "Next page"), { tab.step(1) }, enabled = tab.rowOf(tab.page) < tab.rowOf(tab.pageCount - 1), shortcut = "PgDn")
                    VDivider()
                    if (medium) IconBtn(Icons.Rounded.Remove, L("Pomniejsz", "Zoom out"), { zoomStep(tab, -1) }, shortcut = "Ctrl+−")
                    ZoomMenu(tab)
                    if (medium) IconBtn(Icons.Rounded.Add, L("Powiększ", "Zoom in"), { zoomStep(tab, 1) }, shortcut = "Ctrl++")
                } else {
                    Txt(L("Porządkowanie stron – zaznacz strony i wybierz działanie", "Organizing pages – select pages and choose an action"), color = P.text2, style = Type.small)
                }
            }
            // Narzędzia.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End)) {
                val busy = tab.busy
                if (busy != null) {
                    Spinner(Modifier.size(13.dp), P.accent)
                    if (medium) Txt(busy, Modifier.padding(start = 4.dp, end = 6.dp).widthIn(max = 220.dp), style = Type.small, color = P.text2)
                }
                if (!tab.organize) {
                    ToolDrop(app, wide)
                    if (app.tool.annotates && app.tool != Tool.ERASER) StyleDrop(app)
                }
                IconBtn(Icons.Rounded.Undo, L("Cofnij", "Undo"), { tab.undo() }, enabled = tab.canUndo && tab.busy == null, shortcut = "Ctrl+Z")
                IconBtn(Icons.Rounded.Redo, L("Ponów", "Redo"), { tab.redo() }, enabled = tab.canRedo && tab.busy == null, shortcut = "Ctrl+Y")
                VDivider()
                IconBtn(
                    if (tab.isBookmarked(tab.page)) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    if (tab.isBookmarked(tab.page)) L("Usuń zakładkę z tej strony", "Remove the bookmark from this page") else L("Dodaj zakładkę na tej stronie", "Bookmark this page"),
                    { tab.toggleBookmark() }, active = tab.isBookmarked(tab.page), shortcut = "Ctrl+D",
                )
                IconBtn(Icons.Rounded.Search, L("Szukaj w dokumencie", "Search the document"), {
                    st.sidebar = true
                    st.sideTab = SideTab.SEARCH
                    app.focusSearch++
                }, active = st.sidebar && st.sideTab == SideTab.SEARCH, shortcut = "Ctrl+F")
                if (!tab.organize) ViewDrop(app, tab, wide)
                DocDrop(app, tab, wide)
                Spacer(Modifier.width(2.dp))
                if (tab.unsaved) {
                    Btn(if (medium) L("Zapisz", "Save") else null, Icons.Rounded.Save, { app.save(tab) }, kind = BtnKind.PRIMARY, enabled = tab.busy == null, tip = L("Zapisz zmiany", "Save changes"), shortcut = "Ctrl+S", height = 30.dp)
                }
            }
        }
    }
}

/** Wybór narzędzia: przeglądanie i adnotacje w jednym rozwijanym menu. */
@Composable
private fun ToolDrop(app: AppState, wide: Boolean) {
    val cur = app.tool
    DropBtn(if (wide) cur.label else null, toolIcon(cur), L("Narzędzie", "Tool"), width = 250.dp, active = cur.annotates, iconTint = if (cur.annotates) toolColor(app)?.let { Color(it) } else null) { close ->
        MenuHeader(L("Przeglądanie", "Browsing"))
        for (t in listOf(Tool.SELECT, Tool.HAND)) {
            MenuItem(t.label, toolIcon(t), t.key, checked = if (cur == t) true else null) { close(); app.tool = t }
        }
        MenuSeparator()
        MenuHeader(L("Adnotacje", "Annotations"))
        for (t in listOf(Tool.MARKUP, Tool.PEN, Tool.MARKER, Tool.SHAPE, Tool.TEXT, Tool.NOTE, Tool.ERASER)) {
            MenuItem(t.label, toolIcon(t), t.key, checked = if (cur == t) true else null) { close(); app.tool = t }
        }
    }
}

/** Kolor bieżącego narzędzia rysowania (do kropki na przycisku). */
fun toolColor(app: AppState): Int? = with(app.styles) {
    when (app.tool) {
        Tool.MARKUP -> markupColor
        Tool.PEN -> penColor
        Tool.MARKER -> markerColor
        Tool.SHAPE -> shapeColor
        Tool.TEXT -> textColor
        Tool.NOTE -> noteColor
        else -> null
    }
}

/** Styl narzędzia: kolor, grubość, rodzaj kształtu lub wyróżnienia. */
@Composable
private fun StyleDrop(app: AppState) {
    val s = app.styles
    val color = toolColor(app) ?: return
    Box {
        DropBtn(null, Icons.Rounded.Circle, L("Kolor i styl", "Colour and style"), width = 260.dp, iconTint = Color(color)) { _ ->
            when (app.tool) {
                Tool.MARKUP -> {
                    MenuHeader(L("Rodzaj", "Kind"))
                    ChipRow(
                        listOf(MarkupKind.HIGHLIGHT to L("Zakreśl", "Highlight"), MarkupKind.UNDERLINE to L("Podkreśl", "Underline"), MarkupKind.STRIKEOUT to L("Przekreśl", "Strike")),
                        s.markupKind, { s.markupKind = it },
                    )
                    MenuHeader(L("Kolor", "Colour"))
                    ColorRow(if (s.markupKind == MarkupKind.HIGHLIGHT) MARKER_COLORS else INK_COLORS, s.markupColor, { s.markupColor = it })
                }
                Tool.PEN -> {
                    MenuHeader(L("Kolor", "Colour"))
                    ColorRow(INK_COLORS, s.penColor, { s.penColor = it })
                    MenuHeader(L("Grubość", "Width"))
                    ChipRow(listOf(1f to "1", 2f to "2", 3f to "3", 5f to "5", 8f to "8"), s.penWidth, { s.penWidth = it })
                }
                Tool.MARKER -> {
                    MenuHeader(L("Kolor", "Colour"))
                    ColorRow(MARKER_COLORS, s.markerColor, { s.markerColor = it })
                    MenuHeader(L("Grubość", "Width"))
                    ChipRow(listOf(8f to "8", 12f to "12", 16f to "16", 22f to "22"), s.markerWidth, { s.markerWidth = it })
                }
                Tool.SHAPE -> {
                    MenuHeader(L("Kształt", "Shape"))
                    ChipRow(
                        listOf(ShapeKind.RECT to L("Prostokąt", "Rectangle"), ShapeKind.ELLIPSE to L("Elipsa", "Ellipse"), ShapeKind.LINE to L("Linia", "Line"), ShapeKind.ARROW to L("Strzałka", "Arrow")),
                        s.shapeKind, { s.shapeKind = it },
                    )
                    MenuHeader(L("Kolor", "Colour"))
                    ColorRow(INK_COLORS, s.shapeColor, { s.shapeColor = it })
                    MenuHeader(L("Grubość", "Width"))
                    ChipRow(listOf(1f to "1", 2f to "2", 3f to "3", 5f to "5"), s.shapeWidth, { s.shapeWidth = it })
                    ChipRow(listOf(false to L("Bez wypełnienia", "No fill"), true to L("Wypełnienie", "Fill")), s.shapeFill, { s.shapeFill = it })
                }
                Tool.TEXT -> {
                    MenuHeader(L("Kolor tekstu", "Text colour"))
                    ColorRow(INK_COLORS, s.textColor, { s.textColor = it })
                    MenuHeader(L("Rozmiar", "Size"))
                    ChipRow(listOf(9f to "9", 12f to "12", 16f to "16", 24f to "24", 36f to "36"), s.textSize, { s.textSize = it })
                    ChipRow(listOf(false to L("Przezroczyste tło", "Transparent"), true to L("Białe tło", "White box")), s.textBackground, { s.textBackground = it })
                }
                Tool.NOTE -> {
                    MenuHeader(L("Kolor notatki", "Note colour"))
                    ColorRow(MARKER_COLORS + listOf(0xFFFFD54F.toInt()), s.noteColor, { s.noteColor = it })
                }
                else -> Unit
            }
        }
    }
}

/** Widok: układ stron, przewijanie, kierunek, obrót i papier – jedno rozwijane menu. */
@Composable
private fun ViewDrop(app: AppState, tab: DocTab, wide: Boolean) {
    val st = app.settings
    val icon = when (tab.layout) {
        PageLayout.SINGLE -> if (tab.flow == Flow.CONTINUOUS) Icons.Rounded.ViewAgenda else Icons.Rounded.Article
        PageLayout.SPREAD -> Icons.Rounded.ViewCarousel
        PageLayout.BOOK -> Icons.Rounded.AutoStories
    }
    val label = when (tab.layout) {
        PageLayout.SINGLE -> L("Jedna strona", "Single page")
        PageLayout.SPREAD -> L("Dwie strony", "Two pages")
        PageLayout.BOOK -> L("Książka", "Book")
    }
    DropBtn(if (wide) label else null, icon, L("Widok", "View"), width = 270.dp, alignEnd = true) { close ->
        MenuHeader(L("Układ stron", "Page layout"))
        MenuItem(L("Jedna strona", "Single page"), Icons.Rounded.Article, "1", checked = if (tab.layout == PageLayout.SINGLE) true else null) { close(); setLayout(app, tab, PageLayout.SINGLE) }
        MenuItem(L("Dwie strony", "Two pages"), Icons.Rounded.ViewCarousel, "2", checked = if (tab.layout == PageLayout.SPREAD) true else null) { close(); setLayout(app, tab, PageLayout.SPREAD) }
        MenuItem(L("Książka (okładka osobno)", "Book (cover alone)"), Icons.Rounded.AutoStories, "B", checked = if (tab.layout == PageLayout.BOOK) true else null) { close(); setLayout(app, tab, PageLayout.BOOK) }
        MenuSeparator()
        MenuHeader(L("Przewijanie", "Scrolling"))
        MenuItem(L("Ciągłe", "Continuous"), Icons.Rounded.ViewAgenda, "C", checked = if (tab.flow == Flow.CONTINUOUS) true else null) { close(); setFlow(app, tab, Flow.CONTINUOUS) }
        MenuItem(L("Strona po stronie", "Page by page"), Icons.Rounded.MenuBook, "C", checked = if (tab.flow == Flow.PAGED) true else null) { close(); setFlow(app, tab, Flow.PAGED) }
        MenuItem(L("Od prawej do lewej (manga)", "Right to left (manga)"), Icons.Rounded.FormatTextdirectionRToL, "R", checked = if (tab.rtl) true else null, enabled = tab.layout != PageLayout.SINGLE) {
            close(); tab.rtl = !tab.rtl; st.rtl = tab.rtl; app.saveSoon()
        }
        MenuSeparator()
        MenuHeader(L("Powiększenie i obrót", "Zoom and rotation"))
        MenuItem(L("Dopasuj do szerokości", "Fit width"), Icons.Rounded.WidthNormal, "Ctrl+2", checked = if (tab.zoomMode == ZoomMode.FIT_WIDTH) true else null) { close(); tab.zoomMode = ZoomMode.FIT_WIDTH }
        MenuItem(L("Cała strona", "Fit page"), Icons.Rounded.FitScreen, "Ctrl+1", checked = if (tab.zoomMode == ZoomMode.FIT_PAGE) true else null) { close(); tab.zoomMode = ZoomMode.FIT_PAGE }
        MenuItem(L("Obróć widok w prawo", "Rotate view right"), Icons.Rounded.RotateRight, "Ctrl+R") { close(); tab.rotation = (tab.rotation + 90) % 360 }
        MenuItem(L("Obróć widok w lewo", "Rotate view left"), Icons.Rounded.RotateLeft, "Ctrl+Shift+R") { close(); tab.rotation = (tab.rotation + 270) % 360 }
        MenuSeparator()
        MenuHeader(L("Papier", "Paper"))
        MenuItem(L("Zwykły", "Normal"), Icons.Rounded.WbSunny, checked = if (st.paper == Paper.NORMAL) true else null) { close(); st.paper = Paper.NORMAL; app.saveSoon() }
        MenuItem(L("Sepia", "Sepia"), Icons.Rounded.Coffee, checked = if (st.paper == Paper.SEPIA) true else null) { close(); st.paper = Paper.SEPIA; app.saveSoon() }
        MenuItem(L("Nocny", "Night"), Icons.Rounded.Brightness2, "N", checked = if (st.paper == Paper.NIGHT) true else null) { close(); st.paper = Paper.NIGHT; app.saveSoon() }
        MenuSeparator()
        MenuItem(L("Pełny ekran", "Full screen"), Icons.Rounded.Fullscreen, "F11") { close(); app.fullscreen = true }
        MenuItem(L("Prezentacja", "Presentation"), Icons.Rounded.Slideshow, "F5") { close(); app.presentation = true }
    }
}

/** Dokument: panele treści i narzędzia zmieniające plik. */
@Composable
private fun DocDrop(app: AppState, tab: DocTab, wide: Boolean) {
    val st = app.settings
    val idx = tab.index
    DropBtn(if (wide) L("Dokument", "Document") else null, Icons.Rounded.Layers, L("Dokument i narzędzia", "Document and tools"), width = 300.dp, alignEnd = true) { close ->
        fun panel(t: SideTab) {
            close()
            st.sidebar = true
            st.sideTab = t
            app.saveSoon()
        }
        MenuHeader(L("Treść", "Content"))
        MenuItem(L("Adnotacje ({0})", "Annotations ({0})").replace("{0}", (idx.annotCount + tab.marks.size).toString()), Icons.Rounded.Layers) { panel(SideTab.ANNOTATIONS) }
        MenuItem(
            if (idx.fields.isEmpty()) L("Formularz (brak pól)", "Form (no fields)") else L("Formularz ({0} pól)", "Form ({0} fields)").replace("{0}", idx.fields.size.toString()),
            Icons.Rounded.FactCheck, enabled = idx.fields.isNotEmpty(),
        ) { panel(SideTab.FORM) }
        MenuItem(L("Zakładki ({0})", "Bookmarks ({0})").replace("{0}", tab.bookmarks.size.toString()), Icons.Rounded.Bookmark) { panel(SideTab.BOOKMARKS) }
        MenuItem(L("Wyodrębnij tekst…", "Extract text…"), Icons.Rounded.Notes) { close(); app.extractText(tab) }
        MenuSeparator()
        MenuHeader(L("Plik", "File"))
        MenuItem(L("Porządkuj strony", "Organize pages"), Icons.Rounded.GridView, "Ctrl+Shift+O") { close(); tab.organize = true }
        MenuItem(L("Eksport stron jako obrazy…", "Export pages as images…"), Icons.Rounded.Image) { close(); app.dialog = Dialog.EXPORT_IMAGES }
        MenuItem(L("Zmniejsz rozmiar…", "Reduce size…"), Icons.Rounded.Compress) { close(); app.dialog = Dialog.COMPRESS }
        MenuItem(
            if (tab.protection != null) L("Hasło i ochrona (włączone)", "Password and protection (on)") else L("Hasło i ochrona…", "Password and protection…"),
            Icons.Rounded.Lock,
        ) { close(); app.dialog = Dialog.SECURITY }
        MenuItem(L("Właściwości…", "Properties…"), Icons.Rounded.Info, "Ctrl+I") { close(); app.dialog = Dialog.PROPERTIES }
        MenuItem(L("Drukuj…", "Print…"), Icons.Rounded.Print, "Ctrl+P") { close(); print(app, tab) }
        MenuSeparator()
        MenuItem(L("Zapisz jako…", "Save as…"), Icons.Rounded.SaveAs, "Ctrl+Shift+S") { close(); app.saveAs(tab) }
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
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(28.dp).background(P.surface, shape).border(1.dp, if (focused) P.accent else P.line, shape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            text, { v -> text = v.filter { it.isDigit() }.take(6) },
            Modifier.width(34.dp)
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
            textStyle = Type.body.copy(color = P.text, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(P.accent),
        )
        Txt(" / ${tab.pageCount}", color = P.text3)
    }
}

@Composable
private fun ZoomMenu(tab: DocTab) {
    DropBtn("${(tab.effectiveZoom * 100).roundToInt()}%", null, L("Powiększenie", "Zoom"), Modifier.widthIn(min = 68.dp), width = 210.dp) { close ->
        MenuItem(L("Dopasuj do szerokości", "Fit width"), Icons.Rounded.WidthNormal, "Ctrl+2", checked = if (tab.zoomMode == ZoomMode.FIT_WIDTH) true else null) { close(); tab.zoomMode = ZoomMode.FIT_WIDTH }
        MenuItem(L("Cała strona", "Fit page"), Icons.Rounded.FitScreen, "Ctrl+1", checked = if (tab.zoomMode == ZoomMode.FIT_PAGE) true else null) { close(); tab.zoomMode = ZoomMode.FIT_PAGE }
        MenuItem(L("Rozmiar rzeczywisty", "Actual size"), shortcut = "Ctrl+0", checked = if (tab.zoomMode == ZoomMode.CUSTOM && tab.zoom == 1f) true else null) { close(); tab.zoomMode = ZoomMode.CUSTOM; tab.zoom = 1f }
        MenuSeparator()
        for (z in floatArrayOf(0.5f, 0.75f, 1.25f, 1.5f, 2f, 3f, 4f)) {
            MenuItem("${(z * 100).roundToInt()}%", checked = if (tab.zoomMode == ZoomMode.CUSTOM && tab.zoom == z) true else null) { close(); tab.zoomMode = ZoomMode.CUSTOM; tab.zoom = z }
        }
    }
}

fun copyToClipboard(s: String) {
    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(s), null) }
}

/** Drukowanie systemowym oknem drukowania (PDFBox) – razem z niezapisanymi adnotacjami. */
fun print(app: AppState, tab: DocTab) {
    if (!tab.ready) return
    tab.scope.launch {
        val f = runCatching { tab.currentFile() }.getOrNull() ?: return@launch
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
}
