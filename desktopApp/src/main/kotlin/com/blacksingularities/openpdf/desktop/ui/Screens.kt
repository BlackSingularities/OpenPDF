@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import com.blacksingularities.openpdf.desktop.Tool
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.DocTab
import com.blacksingularities.openpdf.desktop.Recent
import com.blacksingularities.openpdf.desktop.TabStatus
import com.blacksingularities.openpdf.platform.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Frame
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.io.File
import javax.imageio.ImageIO

/** Cała zawartość okna. */
@Composable
fun AppContent(
    app: AppState,
    scope: FrameWindowScope,
    state: WindowState,
    handles: MutableMap<Int, ViewerHandle>,
    onClose: () -> Unit,
) {
    val window: Frame = scope.window
    val tab = app.active
    val chrome = !app.fullscreen && !app.presentation
    val floating = chrome && state.placement == WindowPlacement.Floating
    Box(Modifier.fillMaxSize().background(P.bg).then(if (floating) Modifier.border(1.dp, P.line2) else Modifier)) {
        Column(Modifier.fillMaxSize()) {
            if (chrome) {
                TitleBar(app, scope, state, onClose)
                HDivider()
            }
            if (tab == null) {
                Home(app, window, Modifier.weight(1f).fillMaxWidth())
            } else {
                val handle = handles.getOrPut(tab.id) { ViewerHandle() }
                if (chrome && tab.ready) {
                    Toolbar(app, tab)
                    HDivider()
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (chrome && tab.ready && app.settings.sidebar && !tab.organize) {
                        Sidebar(app, tab, Modifier.width(248.dp).fillMaxHeight())
                        Box(Modifier.width(1.dp).fillMaxHeight().background(P.line))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (val s = tab.status) {
                            is TabStatus.Loading -> LoadingView(s)
                            is TabStatus.NeedPassword -> PasswordView(app, tab, s.wrong)
                            is TabStatus.Failed -> FailedView(app, tab, s.message)
                            TabStatus.Ready -> {
                                if (tab.organize && chrome) {
                                    OrganizeView(app, tab, Modifier.fillMaxSize())
                                } else {
                                    Viewer(app, tab, handle, app.presentation)
                                    if (!chrome) FullscreenHud(app, tab)
                                    if (chrome && app.tool.annotates) ToolHint(app, Modifier.align(Alignment.BottomCenter))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (app.dragOver) DropOverlay()
        Dialogs(app)
        ToastHost(app, Modifier.align(Alignment.BottomCenter))
        if (floating) ResizeHandles(window)
    }
}

// ------------------------------------------------------------------ ekran startowy

@Composable
private fun Home(app: AppState, window: Frame?, modifier: Modifier) {
    val recents = app.settings.recents
    val favs = app.settings.favorites
    var filter by remember { mutableStateOf("") }
    Column(modifier.background(P.bg).padding(horizontal = 28.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 14.dp)) {
            Txt("OpenDocument", style = Type.display, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Txt(
                L("Czytaj, adnotuj i porządkuj PDF, DjVu, EPUB, FB2, MOBI, DOCX, ODT, RTF, CBZ, Markdown, HTML i obrazy – przeciągnij pliki do okna.",
                    "Read, annotate and organize PDF, DjVu, EPUB, FB2, MOBI, DOCX, ODT, RTF, CBZ, Markdown, HTML and images – drag files into the window."),
                color = P.text2, maxLines = 2,
            )
        }
        // Szybkie akcje – kafelki.
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(Icons.Rounded.FileOpen, L("Otwórz pliki", "Open files"), "Ctrl+O", Modifier.weight(1f), primary = true) { app.pickFiles(window) }
            ActionTile(Icons.Rounded.NoteAdd, L("Pusty dokument", "Blank document"), null, Modifier.weight(1f)) { app.dialog = com.blacksingularities.openpdf.desktop.Dialog.NEW_DOC }
            ActionTile(Icons.Rounded.Image, L("PDF z obrazów", "PDF from images"), null, Modifier.weight(1f)) { app.imagesToPdf() }
            ActionTile(Icons.Rounded.MergeType, L("Scal pliki PDF", "Merge PDFs"), null, Modifier.weight(1f)) { app.mergePdfs() }
        }
        if (recents.isEmpty() && favs.isEmpty()) {
            EmptyDrop(app, window, Modifier.weight(1f).fillMaxWidth().padding(bottom = 28.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(L("Biblioteka", "Library"), Modifier.weight(1f))
                Input(
                    filter, { filter = it }, Modifier.width(240.dp),
                    placeholder = L("Filtruj…", "Filter…"), leading = Icons.Rounded.Search,
                    onFocus = { app.typing = it }, onEscape = { filter = "" },
                )
            }
            val q = filter.trim()
            val favList = favs.filter { q.isEmpty() || File(it).name.contains(q, ignoreCase = true) }
                .map { p -> recents.firstOrNull { it.path.equals(p, ignoreCase = true) } ?: Recent(p, 0, 0, 0L) }
            val list = recents.filter { (q.isEmpty() || File(it.path).name.contains(q, ignoreCase = true)) && !app.settings.isFavorite(it.path) }
            val state = rememberLazyGridState()
            CompositionLocalProvider(
                LocalScrollbarStyle provides ScrollbarStyle(24.dp, 6.dp, RoundedCornerShape(6.dp), 250, P.text.copy(alpha = 0.14f), P.text.copy(alpha = 0.32f)),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyVerticalGrid(
                        GridCells.Adaptive(150.dp), state = state,
                        contentPadding = PaddingValues(bottom = 24.dp, end = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (favList.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(L("Ulubione", "Favourites")) }
                            items(favList, key = { "f:" + it.path }) { r -> RecentCard(app, r) }
                            if (list.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(L("Ostatnio czytane", "Recently read")) }
                        }
                        items(list, key = { it.path }) { r -> RecentCard(app, r) }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, shortcut: String?, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(58.dp).clip(shape)
            .background(if (primary) (if (hovered) P.selHover else P.sel) else if (hovered) P.raised2 else P.raised)
            .border(1.dp, if (primary) Color.Transparent else if (hovered) P.line2 else P.line, shape)
            .hoverable(src).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val fg = if (primary) P.selText else P.text
        Box(
            Modifier.size(32.dp).background(if (primary) P.selText.copy(alpha = 0.14f) else P.accentSoft, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (primary) fg else P.accent)
        }
        Column(Modifier.weight(1f)) {
            Txt(label, color = fg, weight = FontWeight.SemiBold)
            if (shortcut != null) Txt(shortcut, style = Type.tiny, color = fg.copy(alpha = 0.6f))
        }
    }
}

/** Podpowiedź na dole widoku, gdy wybrane jest narzędzie rysowania. */
@Composable
private fun ToolHint(app: AppState, modifier: Modifier) {
    val t = app.tool
    val hint = when (t) {
        Tool.MARKUP -> L("Zaznacz tekst myszą, aby go wyróżnić", "Select text with the mouse to mark it up")
        Tool.PEN, Tool.MARKER -> L("Rysuj, przytrzymując lewy przycisk myszy", "Draw while holding the left mouse button")
        Tool.SHAPE -> L("Przeciągnij, aby narysować kształt", "Drag to draw a shape")
        Tool.TEXT -> L("Kliknij w miejscu, gdzie ma się zacząć tekst", "Click where the text should start")
        Tool.NOTE -> L("Kliknij, aby przypiąć notatkę", "Click to pin a note")
        Tool.ERASER -> L("Kliknij element, aby go usunąć (także adnotacje w pliku)", "Click an item to delete it (file annotations too)")
        else -> return
    }
    Row(
        modifier.padding(bottom = 16.dp).shadow(10.dp, Pill).background(P.raised, Pill).border(1.dp, P.line2, Pill)
            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(26.dp).background(P.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(toolIcon(t), null, Modifier.size(15.dp), tint = toolColor(app)?.let { Color(it or 0xFF000000.toInt()) } ?: P.accent)
        }
        Txt(hint, style = Type.small, color = P.text2)
        Btn(L("Zakończ", "Done"), null, { app.tool = Tool.SELECT }, kind = BtnKind.GHOST, height = 24.dp, shortcut = "Esc")
    }
}

@Composable
private fun EmptyDrop(app: AppState, window: Frame?, modifier: Modifier) {
    val line = P.line2
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).pointerHoverIcon(PointerIcon.Hand).clickable { app.pickFiles(window) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(line, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource("icon.png"), null, Modifier.size(84.dp).shadow(14.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)))
            Spacer(Modifier.height(18.dp))
            Txt(L("Upuść tu dokumenty", "Drop documents here"), style = Type.title, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Txt(L("albo kliknij, aby wybrać pliki – można otworzyć wiele naraz", "or click to choose files – you can open many at once"), color = P.text3)
        }
    }
}

@Composable
private fun RecentCard(app: AppState, r: Recent) {
    val f = File(r.path)
    val exists = remember(r.path) { f.isFile }
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var thumb by remember(r.path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(r.path, app.thumbVersion) {
        thumb = withContext(Dispatchers.IO) {
            runCatching { DocTab.thumbFile(r.path).takeIf { it.isFile }?.let { ImageIO.read(it)?.toComposeImageBitmap() } }.getOrNull()
        }
    }
    Column(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (hovered) P.raised2 else P.raised)
            .border(1.dp, if (hovered) P.line2 else P.line, RoundedCornerShape(14.dp))
            .hoverable(src).pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = exists) { app.open(listOf(f)) }
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(8.dp)).background(P.canvas), contentAlignment = Alignment.Center) {
            val t = thumb
            if (t != null) {
                Image(t, null, Modifier.fillMaxSize().padding(8.dp).shadow(3.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
            } else {
                Txt(f.extension.uppercase().ifEmpty { "?" }, style = Type.title, color = P.text3, weight = FontWeight.Bold)
            }
            val fav = app.settings.isFavorite(r.path)
            if (hovered || fav) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                        .clickable { app.settings.toggleFavorite(r.path); app.saveSoon() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, L("Ulubione", "Favourite"), Modifier.size(14.dp), tint = if (fav) Color(0xFFFFC53D) else Color.White)
                }
            }
            if (hovered) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                        .clickable { app.removeRecent(r.path); app.settings.favorites.removeAll { it.equals(r.path, ignoreCase = true) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, L("Usuń z listy", "Remove from list"), Modifier.size(13.dp), tint = Color.White)
                }
            }
            if (r.pages > 1) {
                // Postęp czytania – cienki pasek na dole miniatury.
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(((r.page + 1f) / r.pages).coerceIn(0.02f, 1f)).fillMaxHeight().background(P.accent))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Txt(f.name, style = Type.small, weight = FontWeight.Medium, color = if (exists) P.text else P.text3, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        Txt(
            if (!exists) L("Plik niedostępny", "File unavailable")
            else (if (r.pages > 0) L("str. {0} z {1}", "p. {0} of {1}").replace("{0}", (r.page + 1).toString()).replace("{1}", r.pages.toString()) + " · " else "") + formatDate(r.opened, false),
            style = Type.tiny, color = P.text3,
        )
    }
}

// ------------------------------------------------------------------ stany karty

@Composable
private fun LoadingView(s: TabStatus.Loading) {
    Box(Modifier.fillMaxSize().background(P.canvas), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner(Modifier.size(28.dp), P.text)
            Spacer(Modifier.height(14.dp))
            Txt(s.label, color = P.text2)
            val p = s.progress
            if (p != null) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(220.dp).height(4.dp).clip(Pill).background(P.raised2)) {
                    Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).fillMaxHeight().background(P.sel))
                }
            }
        }
    }
}

@Composable
private fun PasswordView(app: AppState, tab: DocTab, wrong: Boolean) {
    var pw by remember(tab.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(tab.id) {
        delay(50)
        runCatching { focus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(P.canvas), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(360.dp).background(P.panel, RoundedCornerShape(18.dp)).border(1.dp, P.line2, RoundedCornerShape(18.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(30.dp), tint = P.text)
            Spacer(Modifier.height(10.dp))
            Txt(L("Dokument chroniony hasłem", "Password-protected document"), style = Type.title, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Txt(tab.name, color = P.text3, style = Type.small)
            Spacer(Modifier.height(16.dp))
            Input(
                pw, { pw = it }, Modifier.fillMaxWidth(), placeholder = L("Hasło", "Password"), password = true, focus = focus,
                onFocus = { app.typing = it }, onEnter = { if (pw.isNotEmpty()) tab.start(pw) },
            )
            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Txt(L("Nieprawidłowe hasło", "Wrong password"), color = P.err, style = Type.small)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(L("Zamknij", "Close"), null, { app.close(tab) })
                Btn(L("Otwórz", "Open"), null, { tab.start(pw) }, kind = BtnKind.PRIMARY, enabled = pw.isNotEmpty())
            }
        }
    }
}

@Composable
private fun FailedView(app: AppState, tab: DocTab, message: String) {
    Box(Modifier.fillMaxSize().background(P.canvas), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(34.dp), tint = P.err)
            Spacer(Modifier.height(10.dp))
            Txt(L("Nie udało się otworzyć dokumentu", "Could not open the document"), style = Type.title, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.text.BasicText(message, style = Type.body.copy(color = P.text2, textAlign = TextAlign.Center))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(L("Zamknij kartę", "Close tab"), null, { app.close(tab) })
                Btn(L("Spróbuj ponownie", "Try again"), null, { tab.start() }, kind = BtnKind.PRIMARY)
            }
        }
    }
}

@Composable
private fun DropOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).padding(18.dp)) {
        val c = Color.White
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(c.copy(alpha = 0.8f), cornerRadius = CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.UploadFile, null, Modifier.size(44.dp), tint = Color.White)
            Spacer(Modifier.height(10.dp))
            Txt(L("Upuść, aby otworzyć", "Drop to open"), style = Type.title, color = Color.White, weight = FontWeight.SemiBold)
        }
    }
}

/** Numer strony na pełnym ekranie – znika po chwili bez ruchu. */
@Composable
private fun FullscreenHud(app: AppState, tab: DocTab) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(tab.page) {
        visible = true
        delay(1600)
        visible = false
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.background(Color.Black.copy(alpha = 0.72f), Pill).padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("${tab.page + 1} / ${tab.pageCount}", color = Color.White, weight = FontWeight.Medium)
                Txt(L("Esc – wyjście", "Esc – exit"), color = Color.White.copy(alpha = 0.55f), style = Type.small)
            }
        }
    }
    if (app.presentation) return
}

@Composable
private fun ToastHost(app: AppState, modifier: Modifier) {
    val t = app.toast
    AnimatedVisibility(t != null, modifier.padding(bottom = 24.dp), enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        val last = remember { mutableStateOf(t) }
        if (t != null) last.value = t
        val v = last.value ?: return@AnimatedVisibility
        Row(
            Modifier.shadow(16.dp, Pill).background(if (v.error) P.err else P.sel, Pill).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(v.text, color = if (v.error) Color.White else P.selText, weight = FontWeight.Medium, maxLines = 3)
        }
    }
}
