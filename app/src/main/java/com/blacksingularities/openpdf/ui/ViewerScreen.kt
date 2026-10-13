@file:Suppress("DEPRECATION")

package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PanoramaFishEye
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.data.BookMode
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.ui.components.ColorDot
import com.blacksingularities.openpdf.ui.components.RoundIcon
import com.blacksingularities.openpdf.ui.theme.GlassLevel
import com.blacksingularities.openpdf.ui.theme.GlassToolbar
import com.blacksingularities.openpdf.ui.theme.GlassValueButton
import com.blacksingularities.openpdf.ui.theme.LocalHintOnTap
import com.blacksingularities.openpdf.ui.theme.LocalPickerDirection
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.ui.theme.PickerDirection
import com.blacksingularities.openpdf.ui.theme.PickerOption
import com.blacksingularities.openpdf.ui.theme.glass
import com.blacksingularities.openpdf.ui.theme.reactive
import kotlin.math.roundToInt

/** Czy pokazywać rozkładówki: ustawienie, a w trybie automatycznym – poziomy ekran lub rozłożony fold. */
fun bookActive(mode: BookMode, widthDp: Float, heightDp: Float, foldBook: Boolean): Boolean = when (mode) {
    BookMode.ALWAYS -> true
    BookMode.NEVER -> false
    BookMode.AUTO -> foldBook || (widthDp > heightDp * 1.15f && widthDp >= 560f)
}

@Composable
fun ViewerScreen(vm: PdfViewModel) {
    val scope = rememberCoroutineScope()
    val state = remember { ViewerState(scope) }
    val config = LocalConfiguration.current
    val landscape = config.screenWidthDp > config.screenHeightDp
    val book = bookActive(vm.settings.bookMode, config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat(), vm.foldBook)
    // Nowy dokument (lub jego nowa wersja) – nowy stan widoku zachowuje stronę w PdfViewer.
    BackHandler(enabled = true) {
        when {
            vm.selection != null -> vm.clearSelection()
            vm.searchActive -> vm.closeSearch()
            vm.editing -> vm.changeEditing(false)
            else -> vm.exitToHome()
        }
    }
    val pinned = vm.settings.pinToolbar
    var barSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxSize().background(Palette.Desk)) {
        // Przypięty pasek: pierwsza strona zaczyna się pod paskiem, ale płótno dokumentu się nie
        // przesuwa – chowanie i pokazywanie paska nie zmienia położenia stron.
        val topInset = if (pinned && !landscape) barSize.height.toFloat() else 0f
        PdfViewer(vm, state, book, topInset = topInset)
        val showChrome = vm.chrome || vm.editing || vm.searchActive
        // Pasek główny: u góry (pionowo – z lewej, jak w OpenShot).
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn() + if (landscape) slideInHorizontally { -it } else slideInVertically { -it },
            exit = fadeOut() + if (landscape) slideOutHorizontally { -it } else slideOutVertically { -it },
            modifier = if (landscape) Modifier.align(Alignment.CenterStart) else Modifier.align(Alignment.TopCenter),
        ) {
            Box(Modifier.onSizeChanged { barSize = it }.windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp)) {
                if (vm.searchActive && !landscape) SearchBar(vm) else MainBar(vm, book, vertical = landscape)
            }
        }
        if (vm.searchActive && landscape) {
            Box(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp).widthIn(max = 560.dp)) {
                SearchBar(vm)
            }
        }
        // Dół: numer strony albo narzędzia edycji.
        Box(
            Modifier
                .align(if (landscape && vm.editing) Alignment.CenterEnd else Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp),
        ) {
            AnimatedVisibility(visible = vm.editing, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                EditBar(vm, vertical = landscape)
            }
            AnimatedVisibility(visible = !vm.editing && showChrome && vm.selection == null, enter = fadeIn(), exit = fadeOut()) {
                PageIndicator(vm, state)
            }
        }
        // Akcje zaznaczonego tekstu.
        val sel = vm.selection
        if (sel != null && vm.tool != Tool.MARKUP) {
            Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = if (vm.editing && !landscape) 76.dp else 14.dp)) {
                SelectionBar(vm)
            }
        }
        // Zaznaczony element w trybie edycji.
        val selectedMark = vm.marks.firstOrNull { it.id == vm.selectedId }
        if (selectedMark != null && vm.editing) {
            Box(
                Modifier
                    .align(if (landscape) Alignment.BottomCenter else Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = if (landscape) 0.dp else 68.dp, bottom = 12.dp),
            ) {
                MarkActions(vm, selectedMark)
            }
        }
        if (vm.searchActive && vm.searchHits.isNotEmpty() && !vm.editing) {
            Box(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(end = 12.dp, bottom = 70.dp)) {
                SearchResultsButton(vm)
            }
        }
    }
}

// ------------------------------------------------------------------ pasek główny

@Composable
private fun MainBar(vm: PdfViewModel, book: Boolean, vertical: Boolean) {
    val s = vm.settings
    val items: @Composable () -> Unit = {
        RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Zamknij dokument"), onClick = { vm.exitToHome() })
        RoundIcon(Icons.Rounded.GridView, tr("Strony: układanie, obracanie, usuwanie"), onClick = { vm.screen = Screen.PAGES })
        val modes = listOf(BookMode.NEVER, BookMode.ALWAYS, BookMode.AUTO)
        RoundIcon(
            if (book) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.Description,
            when (s.bookMode) {
                BookMode.AUTO -> if (book) tr("Tryb książki (automatycznie)") else tr("Pojedyncze strony (automatycznie)")
                BookMode.ALWAYS -> tr("Tryb książki: dwie strony obok siebie")
                BookMode.NEVER -> tr("Pojedyncze strony")
            },
            active = book,
            badge = if (s.bookMode == BookMode.AUTO) "A" else null,
            options = listOf(
                PickerOption(tr("Pojedyncze strony"), Icons.Rounded.Description, caption = tr("Jedna")),
                PickerOption(tr("Tryb książki"), Icons.AutoMirrored.Rounded.MenuBook, caption = tr("Książka")),
                PickerOption(tr("Automatycznie: poziomo i na rozłożonym foldzie"), Icons.Rounded.AutoStories, caption = "Auto"),
            ),
            selected = modes.indexOf(s.bookMode),
            onPick = { i -> vm.updateSettings { it.copy(bookMode = modes[i]) } },
            onClick = { vm.updateSettings { it.copy(bookMode = if (book) BookMode.NEVER else BookMode.ALWAYS) } },
        )
        if (book) {
            GlassValueButton(
                if (s.coverAlone) "1|23" else "12|34",
                tr("strony"),
                if (s.coverAlone) tr("Okładka osobno – rozkładówki 2–3, 4–5…") else tr("Rozkładówki od pierwszej strony: 1–2, 3–4…"),
                0f,
                active = s.coverAlone,
                options = listOf(
                    PickerOption(tr("Okładka osobno"), value = "1|23", caption = tr("okładka")),
                    PickerOption(tr("Od pierwszej strony"), value = "12|34", caption = tr("parami")),
                ),
                selected = if (s.coverAlone) 0 else 1,
                onPick = { i -> vm.updateSettings { it.copy(coverAlone = i == 0) } },
                onClick = { vm.updateSettings { it.copy(coverAlone = !it.coverAlone) } },
            )
        }
        val scrolls = listOf(ScrollMode.VERTICAL, ScrollMode.PAGED)
        RoundIcon(
            if (s.scroll == ScrollMode.VERTICAL) Icons.Rounded.SwapVert else Icons.Rounded.ViewCarousel,
            if (s.scroll == ScrollMode.VERTICAL) tr("Przewijanie ciągłe w pionie") else tr("Przewracanie stron w poziomie"),
            options = listOf(
                PickerOption(tr("Przewijanie ciągłe"), Icons.Rounded.SwapVert, caption = tr("Ciągłe")),
                PickerOption(tr("Przewracanie stron"), Icons.Rounded.ViewCarousel, caption = tr("Strony")),
            ),
            selected = scrolls.indexOf(s.scroll),
            onPick = { i -> vm.updateSettings { it.copy(scroll = scrolls[i]) } },
            onClick = { vm.updateSettings { it.copy(scroll = if (it.scroll == ScrollMode.VERTICAL) ScrollMode.PAGED else ScrollMode.VERTICAL) } },
        )
        RoundIcon(Icons.Rounded.Search, tr("Szukaj w dokumencie"), active = vm.searchActive, onClick = { vm.searchActive = !vm.searchActive; if (!vm.searchActive) vm.closeSearch() })
        RoundIcon(Icons.Rounded.Toc, tr("Spis treści i zakładki"), onClick = { vm.sheet = Sheet.OUTLINE })
        RoundIcon(Icons.Rounded.Edit, if (vm.editing) tr("Zakończ edycję") else tr("Edycja: rysowanie, tekst, podpis"), active = vm.editing, onClick = { vm.changeEditing(!vm.editing) })
        RoundIcon(
            Icons.Rounded.Save,
            if (vm.dirty || vm.marks.isNotEmpty()) tr("Zapisz zmiany") else tr("Zapisz"),
            badge = if (vm.dirty || vm.marks.isNotEmpty()) "•" else null,
            onLongPress = { vm.saveAs() },
            onClick = { vm.save() },
        )
        RoundIcon(Icons.Rounded.Share, tr("Udostępnij"), onClick = { vm.share() })
        val papers = com.blacksingularities.openpdf.data.PaperTheme.entries
        RoundIcon(
            Icons.Rounded.DarkMode,
            tr("Papier: {0}", s.paper.label),
            active = s.paper == com.blacksingularities.openpdf.data.PaperTheme.DARK || s.paper == com.blacksingularities.openpdf.data.PaperTheme.NIGHT,
            options = papers.map { PickerOption(it.label, Icons.Rounded.DarkMode, caption = it.label) },
            selected = papers.indexOf(s.paper),
            onPick = { i -> vm.updateSettings { it.copy(paper = papers[i]) } },
            onClick = {
                vm.updateSettings {
                    it.copy(paper = if (it.paper == com.blacksingularities.openpdf.data.PaperTheme.NORMAL) com.blacksingularities.openpdf.data.PaperTheme.DARK else com.blacksingularities.openpdf.data.PaperTheme.NORMAL)
                }
            },
        )
    }
    val settings: @Composable () -> Unit = {
        RoundIcon(Icons.Rounded.MoreVert, tr("Menu: dokument i ustawienia"), badge = if (vm.updatePending) "1" else null, onClick = { vm.sheet = Sheet.MORE })
    }
    if (vertical) {
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f, fill = false)) {
                GlassToolbar(vertical = true, modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalHintOnTap provides false, LocalPickerDirection provides PickerDirection.RIGHT) { items() }
                }
            }
            settings()
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                GlassToolbar(
                    vertical = false,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .widthIn(min = maxWidth),
                ) { items() }
            }
            settings()
        }
    }
}

// ------------------------------------------------------------------ numer strony

@Composable
private fun PageIndicator(vm: PdfViewModel, state: ViewerState) {
    val n = vm.index.pageCount
    var scrubbing by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    val bookmarked = vm.bookmarks.any { it.page == vm.currentPage }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (scrubbing && n > 1) {
            var v by remember { mutableStateOf(vm.currentPage.toFloat()) }
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), GlassLevel.Regular)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Slider(
                    value = v,
                    onValueChange = {
                        v = it
                        vm.jumpTo(it.roundToInt())
                    },
                    valueRange = 0f..(n - 1).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIcon(
                if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                if (bookmarked) tr("Usuń zakładkę") else tr("Dodaj zakładkę na tej stronie"),
                size = 40.dp,
                active = bookmarked,
                onClick = { vm.toggleBookmark() },
            )
            Text(
                "${vm.currentPage + 1} / $n",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .reactive(tr("Przejdź do strony"), onLongPress = { goTo = true }) { scrubbing = !scrubbing }
                    .glass(RoundedCornerShape(50), GlassLevel.Regular)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            RoundIcon(Icons.Rounded.KeyboardArrowUp, tr("Poprzednia strona"), size = 40.dp, onClick = { vm.pageStep(-1) })
            RoundIcon(Icons.Rounded.KeyboardArrowDown, tr("Następna strona"), size = 40.dp, onClick = { vm.pageStep(1) })
        }
    }
    if (goTo) GoToPageDialog(n, vm.currentPage, onDismiss = { goTo = false }) { p ->
        goTo = false
        vm.jumpTo(p)
    }
}

// ------------------------------------------------------------------ wyszukiwanie

@Composable
private fun SearchBar(vm: PdfViewModel) {
    var text by remember { mutableStateOf(vm.searchQuery) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(50), GlassLevel.Regular)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text(tr("Szukaj w dokumencie"), color = Palette.TextFaint, fontSize = 15.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (vm.searchHits.isNotEmpty()) {
                Text("${vm.searchIndex + 1}/${vm.searchHits.size}", color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
            }
            RoundIcon(Icons.Rounded.KeyboardArrowUp, tr("Poprzednie trafienie"), size = 38.dp, enabled = vm.searchHits.isNotEmpty(), onClick = { vm.searchStep(-1) })
            RoundIcon(Icons.Rounded.KeyboardArrowDown, tr("Następne trafienie"), size = 38.dp, enabled = vm.searchHits.isNotEmpty() || text.isNotBlank(), onClick = {
                if (vm.searchHits.isEmpty() || text != vm.searchQuery) vm.search(text) else vm.searchStep(1)
            })
            RoundIcon(Icons.Rounded.Close, tr("Zamknij wyszukiwanie"), size = 38.dp, onClick = { vm.closeSearch() })
        }
        if (vm.searching) {
            LinearProgressIndicator(
                progress = { vm.searchProgress },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).height(2.dp),
            )
        }
    }
}

@Composable
private fun SearchResultsButton(vm: PdfViewModel) {
    var open by remember { mutableStateOf(false) }
    if (open) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .glass(RoundedCornerShape(20.dp), GlassLevel.Thick)
                .padding(6.dp)
                .height(320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            vm.searchHits.forEachIndexed { i, h ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (i == vm.searchIndex) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { vm.openHit(i) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(tr("Strona {0}", h.page + 1), color = Palette.TextDim, fontSize = 11.sp)
                    Text(h.snippet, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    Text(
        if (open) tr("Ukryj wyniki") else tr("Wyniki: {0}", vm.searchHits.size),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .reactive(tr("Lista wyników")) { open = !open }
            .glass(RoundedCornerShape(50), GlassLevel.Regular)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

// ------------------------------------------------------------------ zaznaczony tekst

@Composable
private fun SelectionBar(vm: PdfViewModel) {
    GlassToolbar(vertical = false) {
        RoundIcon(Icons.Rounded.ContentCopy, tr("Kopiuj tekst"), onClick = { vm.copySelection() })
        RoundIcon(Icons.Rounded.Highlight, tr("Zakreśl"), onClick = { vm.markupSelection(MarkupKind.HIGHLIGHT) })
        RoundIcon(Icons.Rounded.FormatUnderlined, tr("Podkreśl"), onClick = { vm.markupSelection(MarkupKind.UNDERLINE) })
        RoundIcon(Icons.Rounded.FormatStrikethrough, tr("Przekreśl"), onClick = { vm.markupSelection(MarkupKind.STRIKEOUT) })
        RoundIcon(Icons.Rounded.Search, tr("Szukaj zaznaczonego"), onClick = {
            val t = vm.selection?.text ?: ""
            vm.clearSelection()
            vm.searchActive = true
            vm.search(t)
        })
        RoundIcon(Icons.Rounded.Close, tr("Anuluj zaznaczenie"), onClick = { vm.clearSelection() })
    }
}

// ------------------------------------------------------------------ edycja

private fun toolIcon(t: Tool, vm: PdfViewModel): ImageVector = when (t) {
    Tool.VIEW -> Icons.Rounded.NearMe
    Tool.SELECT -> Icons.Rounded.NearMe
    Tool.PEN -> Icons.Rounded.Edit
    Tool.HIGHLIGHTER -> Icons.Rounded.BorderColor
    Tool.ERASER -> Icons.Rounded.CleaningServices
    Tool.TEXT -> Icons.Rounded.TextFields
    Tool.NOTE -> Icons.Rounded.EditNote
    Tool.SHAPE -> shapeIcon(vm.shapeKind)
    Tool.MARKUP -> markupIcon(vm.markupKind)
    Tool.SIGNATURE -> Icons.Rounded.Draw
    Tool.IMAGE -> Icons.Rounded.AddPhotoAlternate
}

private fun shapeIcon(k: ShapeKind) = when (k) {
    ShapeKind.RECT -> Icons.Rounded.CropSquare
    ShapeKind.ELLIPSE -> Icons.Rounded.PanoramaFishEye
    ShapeKind.LINE -> Icons.Rounded.HorizontalRule
    ShapeKind.ARROW -> Icons.Rounded.NorthEast
}

private fun markupIcon(k: MarkupKind) = when (k) {
    MarkupKind.HIGHLIGHT -> Icons.Rounded.Highlight
    MarkupKind.UNDERLINE -> Icons.Rounded.FormatUnderlined
    MarkupKind.STRIKEOUT -> Icons.Rounded.FormatStrikethrough
}

@Composable
private fun EditBar(vm: PdfViewModel, vertical: Boolean) {
    var styleOpen by remember { mutableStateOf(false) }
    val styled = vm.tool in setOf(Tool.PEN, Tool.HIGHLIGHTER, Tool.SHAPE, Tool.TEXT, Tool.NOTE, Tool.MARKUP, Tool.SIGNATURE) ||
        (vm.tool == Tool.SELECT && vm.selectedId != null)
    val styleTool = if (vm.tool == Tool.SELECT) styleToolFor(vm) else vm.tool
    val items: @Composable () -> Unit = {
        for (t in listOf(Tool.SELECT, Tool.PEN, Tool.HIGHLIGHTER, Tool.ERASER, Tool.TEXT, Tool.NOTE, Tool.SHAPE, Tool.MARKUP, Tool.SIGNATURE, Tool.IMAGE)) {
            when (t) {
                Tool.SHAPE -> {
                    val kinds = ShapeKind.entries
                    RoundIcon(
                        toolIcon(t, vm),
                        tr("Kształt: {0}", vm.shapeKind.label),
                        active = vm.tool == t,
                        options = kinds.map { PickerOption(it.label, shapeIcon(it), caption = it.label) },
                        selected = kinds.indexOf(vm.shapeKind),
                        onPick = { i -> vm.shapeKind = kinds[i]; vm.selectTool(t) },
                        onClick = { vm.selectTool(t) },
                    )
                }
                Tool.MARKUP -> {
                    val kinds = MarkupKind.entries
                    RoundIcon(
                        toolIcon(t, vm),
                        tr("Tekst: {0} (przeciągnij po tekście)", vm.markupKind.label),
                        active = vm.tool == t,
                        options = kinds.map { PickerOption(it.label, markupIcon(it), caption = it.label) },
                        selected = kinds.indexOf(vm.markupKind),
                        onPick = { i -> vm.markupKind = kinds[i]; vm.selectTool(t) },
                        onClick = { vm.selectTool(t) },
                    )
                }
                Tool.SIGNATURE -> RoundIcon(
                    toolIcon(t, vm),
                    tr("Podpis – dotknij strony, przytrzymaj: lista podpisów"),
                    active = vm.tool == t,
                    onLongPress = { vm.sheet = Sheet.SIGNATURES },
                    onClick = { vm.selectTool(t) },
                )
                else -> RoundIcon(toolIcon(t, vm), t.label, active = vm.tool == t, onClick = { vm.selectTool(t) })
            }
        }
        if (styled && styleTool != null) {
            val st = vm.style(styleTool)
            Box(
                Modifier
                    .size(44.dp)
                    .reactive(tr("Kolor i grubość")) { styleOpen = !styleOpen },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Color(st.color)),
                )
            }
        }
        RoundIcon(Icons.AutoMirrored.Rounded.Undo, tr("Cofnij"), enabled = vm.canUndo, onClick = { vm.undo() })
        RoundIcon(Icons.AutoMirrored.Rounded.Redo, tr("Ponów"), enabled = vm.canRedo, onClick = { vm.redo() })
        RoundIcon(Icons.Rounded.Check, tr("Zakończ edycję"), onClick = { vm.changeEditing(false) })
    }
    val direction = if (vertical) PickerDirection.LEFT else PickerDirection.UP
    if (vertical) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (styleOpen && styled && styleTool != null) {
                StylePanel(vm, styleTool) { styleOpen = false }
                Spacer(Modifier.width(8.dp))
            }
            GlassToolbar(vertical = true, modifier = Modifier.verticalScroll(rememberScrollState())) {
                CompositionLocalProvider(LocalPickerDirection provides direction) { items() }
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (styleOpen && styled && styleTool != null) {
                StylePanel(vm, styleTool) { styleOpen = false }
                Spacer(Modifier.height(8.dp))
            }
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                GlassToolbar(vertical = false, modifier = Modifier.widthIn(max = maxWidth).horizontalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalPickerDirection provides direction) { items() }
                }
            }
        }
    }
}

/** Narzędzie, którego styl odpowiada zaznaczonemu elementowi. */
private fun styleToolFor(vm: PdfViewModel): Tool? = when (val m = vm.marks.firstOrNull { it.id == vm.selectedId }) {
    is com.blacksingularities.openpdf.pdf.InkMark -> if (m.highlighter) Tool.HIGHLIGHTER else Tool.PEN
    is com.blacksingularities.openpdf.pdf.ShapeMark -> Tool.SHAPE
    is com.blacksingularities.openpdf.pdf.TextMark -> Tool.TEXT
    is com.blacksingularities.openpdf.pdf.NoteMark -> Tool.NOTE
    is com.blacksingularities.openpdf.pdf.MarkupMark -> Tool.MARKUP
    is com.blacksingularities.openpdf.pdf.SignatureMark -> Tool.SIGNATURE
    else -> null
}

@Composable
private fun StylePanel(vm: PdfViewModel, tool: Tool, onClose: () -> Unit) {
    val st = vm.style(tool)
    val colors = if (tool == Tool.HIGHLIGHTER || tool == Tool.MARKUP || tool == Tool.NOTE) Palette.MarkerColors else Palette.InkColors
    val widthRange = when (tool) {
        Tool.HIGHLIGHTER -> 4f..30f
        Tool.TEXT -> 6f..48f
        Tool.NOTE, Tool.MARKUP -> null
        else -> 0.4f..12f
    }
    Column(
        Modifier
            .widthIn(max = 360.dp)
            .glass(RoundedCornerShape(22.dp), GlassLevel.Thick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tool.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Close, tr("Zamknij"), tint = Palette.TextDim, modifier = Modifier.size(20.dp).clickable(onClick = onClose))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { c -> ColorDot(c, c == st.color) { vm.setStyle(tool, st.copy(color = c)) } }
        }
        if (widthRange != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (tool == Tool.TEXT) tr("Rozmiar") else tr("Grubość"), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                Slider(
                    value = st.width.coerceIn(widthRange),
                    onValueChange = { vm.setStyle(tool, st.copy(width = it)) },
                    valueRange = widthRange,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                )
                Text(
                    if (tool == Tool.TEXT) "${st.width.roundToInt()} pt" else "%.1f pt".format(st.width),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
        if (tool == Tool.SHAPE) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (st.fill) Color.White else Color.White.copy(alpha = 0.08f))
                    .clickable { vm.setStyle(tool, st.copy(fill = !st.fill)) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(tr("Wypełnienie"), color = if (st.fill) Palette.Ink else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tool == Tool.PEN || tool == Tool.HIGHLIGHTER || tool == Tool.SIGNATURE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Rysuj tylko rysikiem"), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(
                    checked = vm.settings.stylusOnly,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(stylusOnly = v) } },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Palette.Ink),
                )
            }
        }
    }
}

@Composable
private fun MarkActions(vm: PdfViewModel, m: com.blacksingularities.openpdf.pdf.Mark) {
    GlassToolbar(vertical = false) {
        if (m is com.blacksingularities.openpdf.pdf.TextMark || m is com.blacksingularities.openpdf.pdf.NoteMark) {
            RoundIcon(Icons.Rounded.Edit, tr("Edytuj treść"), onClick = { vm.editSelectedText() })
        }
        RoundIcon(Icons.Rounded.ContentCopy, tr("Powiel"), onClick = { vm.duplicateSelected() })
        RoundIcon(Icons.Rounded.Delete, tr("Usuń"), onClick = { vm.deleteSelected() })
        RoundIcon(Icons.Rounded.Close, tr("Odznacz"), onClick = { vm.selectedId = null })
    }
}

// ------------------------------------------------------------------ nakładka zajętości i komunikaty

@Composable
fun BusyOverlay(vm: PdfViewModel) {
    val b = vm.busy ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .glass(RoundedCornerShape(24.dp), GlassLevel.Thick)
                .padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (b.progress != null) {
                CircularProgressIndicator(progress = { b.progress }, color = Color.White, trackColor = Color.White.copy(alpha = 0.15f))
            } else {
                CircularProgressIndicator(color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Text(b.label, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
fun MessagePill(vm: PdfViewModel, modifier: Modifier = Modifier) {
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            kotlinx.coroutines.delay(2600)
            if (vm.message == msg) vm.message = null
        }
    }
    AnimatedVisibility(visible = msg != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            msg ?: "",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .glass(RoundedCornerShape(50), GlassLevel.Regular)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}
