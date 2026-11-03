@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.DocTab
import com.blacksingularities.openpdf.ui.baseName
import kotlin.math.roundToInt

/** Strony, na których działają polecenia: zaznaczone albo – gdy nic nie zaznaczono – bieżąca. */
fun DocTab.targets(): List<Int> = if (picked.isNotEmpty()) picked.toList().sorted() else listOf(page)

/** Widok porządkowania stron: siatka miniatur, zaznaczanie (Ctrl, Shift) i operacje na stronach. */
@Composable
fun OrganizeView(app: AppState, tab: DocTab, modifier: Modifier = Modifier) {
    Column(modifier.background(P.canvas)) {
        OrganizeBar(app, tab)
        HDivider()
        val state = rememberLazyGridState()
        var anchor by remember(tab.id) { mutableStateOf(-1) }
        CompositionLocalProvider(
            LocalScrollbarStyle provides ScrollbarStyle(24.dp, 8.dp, RoundedCornerShape(8.dp), 250, P.text.copy(alpha = 0.18f), P.text.copy(alpha = 0.38f)),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    GridCells.Adaptive(156.dp), state = state,
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(tab.pageCount, key = { "${tab.revision}-$it" }) { p ->
                        PageTile(tab, p, p in tab.picked) { ctrl, shift, double ->
                            when {
                                double -> {
                                    tab.organize = false
                                    tab.goTo(p)
                                }
                                shift && anchor >= 0 -> {
                                    val r = if (anchor <= p) anchor..p else p..anchor
                                    if (!ctrl) tab.picked.clear()
                                    r.forEach { if (it !in tab.picked) tab.picked.add(it) }
                                }
                                ctrl -> {
                                    if (p in tab.picked) tab.picked.remove(p) else tab.picked.add(p)
                                    anchor = p
                                }
                                else -> {
                                    tab.picked.clear()
                                    tab.picked.add(p)
                                    anchor = p
                                    tab.page = p
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(2.dp))
            }
        }
    }
}

@Composable
private fun OrganizeBar(app: AppState, tab: DocTab) {
    val sel = tab.picked
    val targets = tab.targets()
    val can = tab.editable
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(P.panel).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Txt(
            if (sel.isEmpty()) L("Stron: {0} · kliknij, Ctrl lub Shift, aby zaznaczyć", "Pages: {0} · click, Ctrl or Shift to select").replace("{0}", tab.pageCount.toString())
            else L("Zaznaczono {0} z {1}", "{0} of {1} selected").replace("{0}", sel.size.toString()).replace("{1}", tab.pageCount.toString()),
            Modifier.weight(1f), style = Type.small, color = P.text2,
        )
        Btn(if (sel.size == tab.pageCount) L("Odznacz", "Deselect") else L("Zaznacz wszystko", "Select all"), null, {
            if (sel.size == tab.pageCount) sel.clear() else {
                sel.clear()
                sel.addAll(0 until tab.pageCount)
            }
        }, kind = BtnKind.GHOST, shortcut = "Ctrl+A")
        VDivider()
        IconBtn(Icons.Rounded.RotateLeft, L("Obróć w lewo", "Rotate left"), { tab.rotatePages(targets, -90) }, enabled = can)
        IconBtn(Icons.Rounded.RotateRight, L("Obróć w prawo", "Rotate right"), { tab.rotatePages(targets, 90) }, enabled = can)
        IconBtn(Icons.Rounded.ChevronLeft, L("Przesuń wcześniej", "Move earlier"), { tab.shiftPages(targets, -1) }, enabled = can && targets.first() > 0, shortcut = "Ctrl+←")
        IconBtn(Icons.Rounded.ChevronRight, L("Przesuń dalej", "Move later"), { tab.shiftPages(targets, 1) }, enabled = can && targets.last() < tab.pageCount - 1, shortcut = "Ctrl+→")
        IconBtn(Icons.Rounded.ContentCopy, L("Powiel", "Duplicate"), { tab.duplicatePages(targets) }, enabled = can)
        IconBtn(Icons.Rounded.Delete, L("Usuń strony", "Delete pages"), { tab.deletePages(targets) }, enabled = can && targets.size < tab.pageCount, shortcut = "Delete", tint = P.err)
        VDivider()
        val at = (targets.last() + 1).coerceAtMost(tab.pageCount)
        DropBtn(L("Wstaw", "Insert"), Icons.Rounded.AddBox, L("Wstaw strony za zaznaczeniem", "Insert pages after the selection"), width = 260.dp, alignEnd = true, enabled = can) { close ->
            MenuItem(L("Pustą stronę", "Blank page"), Icons.Rounded.NoteAdd) { close(); tab.insertBlank(at) }
            MenuItem(L("Strony z pliku PDF…", "Pages from a PDF file…"), Icons.Rounded.Description) {
                close()
                app.chooseFiles(L("Wstaw strony z PDF", "Insert pages from PDF"), app.pdfFilter) { tab.insertPdfs(it, at) }
            }
            MenuItem(L("Obrazy jako strony…", "Images as pages…"), Icons.Rounded.Image) {
                close()
                app.chooseFiles(L("Wstaw obrazy", "Insert images"), app.imageFilter) { tab.insertImages(it, at) }
            }
        }
        Btn(L("Wyodrębnij…", "Extract…"), Icons.Rounded.CallSplit, {
            val list = targets
            val suffix = if (list.size == 1) "-${list[0] + 1}" else "-${list.first() + 1}-${list.last() + 1}"
            app.chooseSave(L("Zapisz wybrane strony", "Save selected pages"), baseName(tab.name) + suffix + ".pdf", "pdf", app.pdfFilter) { out ->
                tab.extractPages(list, out, openAfter = true)
            }
        }, kind = BtnKind.GHOST, tip = L("Zapisz zaznaczone strony jako nowy PDF", "Save the selected pages as a new PDF"))
        VDivider()
        Btn(L("Czytaj", "Read"), Icons.Rounded.MenuBook, { tab.organize = false; tab.goTo(tab.page) }, kind = BtnKind.SOFT, tip = L("Wróć do czytania", "Back to reading"), shortcut = "Esc")
    }
}

@Composable
private fun PageTile(tab: DocTab, p: Int, on: Boolean, onPick: (ctrl: Boolean, shift: Boolean, double: Boolean) -> Unit) {
    val density = LocalDensity.current.density
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var lastClick by remember { mutableStateOf(0L) }
    val s = tab.renderer?.sizes?.getOrNull(p)
    val aspect = s?.aspect ?: 0.75f
    val boxH = 190f
    val wDp = (boxH * aspect).coerceAtMost(140f)
    val hDp = (wDp / aspect).coerceAtMost(boxH)
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.clip(shape)
            .background(if (on) P.accentSoft else if (hovered) P.raised else Color.Transparent)
            .border(if (on) 2.dp else 1.dp, if (on) P.accent else if (hovered) P.line2 else Color.Transparent, shape)
            .hoverable(src).pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Press) { e ->
                if (e.button != PointerButton.Primary) return@onPointerEvent
                val now = System.currentTimeMillis()
                val double = now - lastClick < 380
                lastClick = now
                onPick(e.keyboardModifiers.isCtrlPressed, e.keyboardModifiers.isShiftPressed, double)
            }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(boxH.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val img = pageImage(tab, p, (wDp * density).roundToInt(), (hDp * density).roundToInt(), quick = true)
            Box(Modifier.size(wDp.dp, hDp.dp).shadow(3.dp).background(Color.White)) {
                if (img != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), dstOffset = IntOffset.Zero, filterQuality = FilterQuality.High)
                    }
                }
            }
            if (on) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(P.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = P.selText)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt((p + 1).toString(), style = Type.small, color = if (on) P.text else P.text2, weight = if (on || p == tab.page) FontWeight.SemiBold else null)
            if (tab.isBookmarked(p)) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).background(P.accent, CircleShape))
            }
        }
    }
}
