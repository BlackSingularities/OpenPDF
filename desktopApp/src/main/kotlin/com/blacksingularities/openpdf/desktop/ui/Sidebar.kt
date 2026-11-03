@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.DocTab
import com.blacksingularities.openpdf.desktop.SideTab
import com.blacksingularities.openpdf.pdf.PageText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun Sidebar(app: AppState, tab: DocTab, modifier: Modifier = Modifier) {
    val st = app.settings
    LaunchedEffect(app.focusSearch) { if (app.focusSearch > 0) st.sideTab = SideTab.SEARCH }
    Column(modifier.background(P.panel)) {
        Row(Modifier.fillMaxWidth().padding(10.dp, 10.dp, 10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Seg(
                listOf(
                    SegItem(SideTab.THUMBS, icon = Icons.Rounded.GridView, tip = L("Miniatury stron", "Page thumbnails")),
                    SegItem(SideTab.OUTLINE, icon = Icons.Rounded.Toc, tip = L("Spis treści", "Table of contents")),
                    SegItem(SideTab.SEARCH, icon = Icons.Rounded.Search, tip = L("Szukaj w dokumencie", "Search the document"), shortcut = "Ctrl+F"),
                ),
                st.sideTab,
                {
                    st.sideTab = it
                    app.saveSoon()
                },
                Modifier.fillMaxWidth(),
                fill = true,
            )
        }
        CompositionLocalProvider(
            LocalScrollbarStyle provides ScrollbarStyle(
                minimalHeight = 24.dp, thickness = 6.dp, shape = RoundedCornerShape(6.dp), hoverDurationMillis = 250,
                unhoverColor = P.text.copy(alpha = 0.14f), hoverColor = P.text.copy(alpha = 0.32f),
            ),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (st.sideTab) {
                    SideTab.THUMBS -> Thumbs(tab)
                    SideTab.OUTLINE -> Outline(tab)
                    SideTab.SEARCH -> SearchPanel(app, tab)
                }
            }
        }
    }
}

@Composable
private fun Thumbs(tab: DocTab) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (tab.page - 1).coerceAtLeast(0))
    val density = LocalDensity.current.density
    LaunchedEffect(tab.page) {
        val vis = state.layoutInfo.visibleItemsInfo
        val inView = vis.any { it.index == tab.page && it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset }
        if (!inView) state.animateScrollToItem((tab.page - 1).coerceAtLeast(0))
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tab.pageCount, key = { it }) { p ->
                val s = tab.renderer?.sizes?.getOrNull(p)
                val aspect = s?.aspect ?: 0.75f
                val wDp = 118f
                val hDp = (wDp / aspect).coerceIn(40f, 220f)
                val wPx = (wDp * density).roundToInt()
                val hPx = (hDp * density).roundToInt()
                val on = p == tab.page
                val src = remember { MutableInteractionSource() }
                val hovered by src.collectIsHoveredAsState()
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (on) P.raised2 else if (hovered) P.raised else Color.Transparent)
                        .hoverable(src).pointerHoverIcon(PointerIcon.Hand)
                        .clickable { tab.goTo(p) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val img = pageImage(tab, p, wPx, hPx, quick = true)
                    Box(
                        Modifier.size(wDp.dp, hDp.dp).shadow(2.dp).background(Color.White)
                            .border(if (on) 2.dp else 0.dp, if (on) P.sel else Color.Transparent),
                    ) {
                        if (img != null) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), dstOffset = IntOffset.Zero, filterQuality = FilterQuality.High)
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Txt((p + 1).toString(), style = Type.small, color = if (on) P.text else P.text3, weight = if (on) FontWeight.SemiBold else null)
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
    }
}

@Composable
private fun Outline(tab: DocTab) {
    val outline = tab.index.outline
    if (outline.isEmpty()) {
        Empty(L("Ten dokument nie ma spisu treści", "This document has no table of contents"))
        return
    }
    // Zwinięte gałęzie (indeksy pozycji).
    val collapsed = remember(tab.id) { mutableStateListOf<Int>() }
    val visible = remember(outline, collapsed.toList()) {
        val out = ArrayList<Int>()
        var hideBelow = Int.MAX_VALUE
        for ((i, e) in outline.withIndex()) {
            if (e.depth > hideBelow) continue
            hideBelow = Int.MAX_VALUE
            out += i
            if (i in collapsed) hideBelow = e.depth
        }
        out
    }
    // Pozycja spisu, w której jest bieżąca strona.
    val currentEntry = remember(tab.page, outline) { outline.indices.lastOrNull { (outline[it].page ?: -1) in 0..tab.page } }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, contentPadding = PaddingValues(8.dp, 0.dp, 10.dp, 16.dp)) {
            itemsIndexed(visible, key = { _, i -> i }) { _, i ->
                val e = outline[i]
                val hasChildren = outline.getOrNull(i + 1)?.let { it.depth > e.depth } == true
                val on = i == currentEntry
                val src = remember { MutableInteractionSource() }
                val hovered by src.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (on) P.raised2 else if (hovered) P.raised else Color.Transparent)
                        .hoverable(src).pointerHoverIcon(PointerIcon.Hand)
                        .clickable { e.page?.let { tab.goTo(it) } }
                        .padding(start = (4 + e.depth * 14).dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        if (hasChildren) {
                            Icon(
                                if (i in collapsed) Icons.Rounded.KeyboardArrowRight else Icons.Rounded.KeyboardArrowDown, null,
                                Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).clickable { if (i in collapsed) collapsed.remove(i) else collapsed.add(i) },
                                tint = P.text3,
                            )
                        }
                    }
                    Txt(e.title.ifBlank { "—" }, Modifier.weight(1f).padding(start = 2.dp), color = if (on) P.text else P.text2, weight = if (e.depth == 0 || on) FontWeight.Medium else null, maxLines = 2)
                    e.page?.let { Txt((it + 1).toString(), Modifier.padding(start = 6.dp), style = Type.small, color = P.text3) }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
    }
}

@Composable
private fun SearchPanel(app: AppState, tab: DocTab) {
    val focus = remember { FocusRequester() }
    var q by remember(tab.id) { mutableStateOf(tab.query) }
    LaunchedEffect(app.focusSearch, tab.id) {
        delay(30)
        runCatching { focus.requestFocus() }
    }
    // Wyszukiwanie w trakcie pisania (z opóźnieniem).
    LaunchedEffect(q) {
        tab.query = q
        if (q.trim() == tab.searchedFor) return@LaunchedEffect
        delay(350)
        if (q.trim().length >= 2 || q.isBlank()) tab.search(q)
    }
    Column(Modifier.fillMaxSize()) {
        Input(
            q, { q = it },
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            placeholder = L("Szukaj…", "Search…"),
            leading = Icons.Rounded.Search,
            focus = focus,
            onFocus = { app.typing = it },
            onEnter = { shift ->
                if (q.trim() != tab.searchedFor) tab.search(q) else tab.selectHit(tab.hitIndex + if (shift) -1 else 1)
            },
            onEscape = {
                q = ""
                tab.clearSearch()
            },
        ) {
            if (q.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close, null,
                    Modifier.size(15.dp).clip(RoundedCornerShape(50)).pointerHoverIcon(PointerIcon.Hand).clickable {
                        q = ""
                        tab.clearSearch()
                    },
                    tint = P.text3,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(14.dp, 8.dp, 10.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                tab.searchedFor.isEmpty() -> L("Wpisz co najmniej 2 znaki", "Type at least 2 characters")
                tab.searching && tab.hits.isEmpty() -> L("Szukanie… {0}%", "Searching… {0}%").replace("{0}", (tab.searchProgress * 100).roundToInt().toString())
                tab.hits.isEmpty() -> L("Brak wyników", "No results")
                else -> "${tab.hitIndex + 1} / ${tab.hits.size}" + if (tab.searching) " …" else ""
            }
            Txt(label, Modifier.weight(1f), style = Type.small, color = P.text3)
            IconBtn(Icons.Rounded.KeyboardArrowUp, L("Poprzedni", "Previous"), { tab.selectHit(tab.hitIndex - 1) }, enabled = tab.hits.isNotEmpty(), shortcut = "Shift+F3", size = 24.dp)
            IconBtn(Icons.Rounded.KeyboardArrowDown, L("Następny", "Next"), { tab.selectHit(tab.hitIndex + 1) }, enabled = tab.hits.isNotEmpty(), shortcut = "F3", size = 24.dp)
        }
        if (tab.searching) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(2.dp).background(P.line)) {
                Box(Modifier.fillMaxWidth(tab.searchProgress.coerceIn(0.02f, 1f)).fillMaxHeight().background(P.sel))
            }
        }
        val state = rememberLazyListState()
        LaunchedEffect(tab.hitIndex) {
            if (tab.hitIndex >= 0) {
                val vis = state.layoutInfo.visibleItemsInfo
                if (vis.none { it.index == tab.hitIndex && it.offset >= 0 }) state.animateScrollToItem((tab.hitIndex - 2).coerceAtLeast(0))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = state, contentPadding = PaddingValues(8.dp, 4.dp, 10.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(tab.hits) { i, h ->
                    val on = i == tab.hitIndex
                    val src = remember { MutableInteractionSource() }
                    val hovered by src.collectIsHoveredAsState()
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (on) P.raised2 else if (hovered) P.raised else Color.Transparent)
                            .hoverable(src).pointerHoverIcon(PointerIcon.Hand)
                            .clickable { tab.selectHit(i) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Txt(L("Strona {0}", "Page {0}").replace("{0}", (h.page + 1).toString()), style = Type.tiny, color = P.text3, weight = FontWeight.SemiBold)
                        androidx.compose.foundation.text.BasicText(
                            highlight(h.snippet, tab.searchedFor, P.text, P.text2),
                            style = Type.small,
                            maxLines = 3,
                        )
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
        }
    }
}

/** Fragment tekstu z pogrubionym trafieniem (bez względu na wielkość liter i polskie znaki). */
private fun highlight(snippet: String, query: String, strong: Color, normal: Color): AnnotatedString {
    val folded = PageText.fold(snippet)
    val q = PageText.fold(query)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = normal)) {
            var i = 0
            while (i < snippet.length) {
                val j = if (q.isEmpty()) -1 else folded.indexOf(q, i)
                if (j < 0) {
                    append(snippet.substring(i))
                    break
                }
                append(snippet.substring(i, j))
                withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Bold)) { append(snippet.substring(j, j + q.length)) }
                i = j + q.length
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Txt(text, Modifier.padding(top = 24.dp), color = P.text3, maxLines = 3)
    }
}
