@file:Suppress("DEPRECATION")

package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.pagesWord
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.platform.LocalPlatformUi
import com.blacksingularities.openpdf.platform.PlatformBackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.ui.components.DarkDialog
import com.blacksingularities.openpdf.ui.components.DarkField
import com.blacksingularities.openpdf.ui.components.PillButton
import com.blacksingularities.openpdf.ui.components.RoundIcon
import com.blacksingularities.openpdf.ui.theme.GlassToolbar
import com.blacksingularities.openpdf.ui.theme.LocalPickerDirection
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.ui.theme.PickerDirection
import kotlinx.coroutines.flow.distinctUntilChanged

/** Siatka stron: zaznaczanie, obracanie, usuwanie, kolejność, wstawianie, wyodrębnianie. */
@Composable
fun PagesScreen(vm: PdfViewModel) {
    val n = vm.index.pageCount
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var moveDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    // Po zmianie dokumentu (nowa wersja) – zaznaczenie poza zakresem jest usuwane.
    LaunchedEffect(n) { selected = selected.filter { it < n }.toSet() }
    PlatformBackHandler {
        if (selected.isNotEmpty()) selected = emptySet() else vm.screen = Screen.VIEWER
    }
    LaunchedEffect(grid, vm.revision) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { visible ->
                val first = visible.minOrNull() ?: 0
                val last = visible.maxOrNull() ?: 0
                vm.images.requestThumbs(visible + ((last + 1)..(last + 12)).filter { it < n } + ((first - 6) until first).filter { it >= 0 })
            }
    }
    val insertAt = (selected.maxOrNull()?.plus(1)) ?: n
    val ui = LocalPlatformUi.current
    Box(Modifier.fillMaxSize().background(Palette.Desk)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wróć do dokumentu"), onClick = { vm.screen = Screen.VIEWER })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (selected.isEmpty()) tr("Strony") else tr("Zaznaczono: {0}", selected.size), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (selected.isEmpty()) tr("{0} stron · przytrzymaj, aby zaznaczyć", n) else tr("Dotknij stron, aby zmienić zaznaczenie"),
                        fontSize = 12.sp,
                        color = Palette.TextDim,
                    )
                }
                RoundIcon(Icons.AutoMirrored.Rounded.Undo, tr("Cofnij"), enabled = vm.canUndo, onClick = { vm.undo() })
                Spacer(Modifier.width(6.dp))
                RoundIcon(Icons.AutoMirrored.Rounded.Redo, tr("Ponów"), enabled = vm.canRedo, onClick = { vm.redo() })
                Spacer(Modifier.width(6.dp))
                if (selected.size == n) {
                    RoundIcon(Icons.Rounded.Deselect, tr("Odznacz wszystkie"), onClick = { selected = emptySet() })
                } else {
                    RoundIcon(Icons.Rounded.SelectAll, tr("Zaznacz wszystkie"), onClick = { selected = (0 until n).toSet() })
                }
            }
            vm.images.thumbVersion.intValue
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                state = grid,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items((0 until n).toList(), key = { "${vm.revision}-$it" }) { page ->
                    val sel = page in selected
                    val size = vm.render?.sizes?.getOrNull(page)
                    val aspect = size?.aspect ?: 0.707f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 0.72f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .border(if (sel) 3.dp else 0.dp, if (sel) Color(0xFF2196F3) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (selected.isNotEmpty()) {
                                                selected = if (sel) selected - page else selected + page
                                            } else {
                                                vm.screen = Screen.VIEWER
                                                vm.jumpTo(page)
                                            }
                                        },
                                        onLongClick = { selected = if (sel) selected - page else selected + page },
                                    ),
                            ) {
                                vm.images.thumbVersion.intValue
                                val bmp = vm.images.thumb(page)
                                if (bmp != null) {
                                    Image(bmp, tr("Strona {0}", page + 1), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                }
                                if (sel) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2196F3)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Text(
                            "${page + 1}",
                            fontSize = 12.sp,
                            color = if (sel) Color.White else Palette.TextDim,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp),
        ) {
            GlassToolbar(vertical = false, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                androidx.compose.runtime.CompositionLocalProvider(LocalPickerDirection provides PickerDirection.UP) {
                    val any = selected.isNotEmpty()
                    RoundIcon(Icons.Rounded.RotateLeft, tr("Obróć w lewo"), enabled = any, onClick = { vm.rotatePages(selected, -90) })
                    RoundIcon(Icons.Rounded.RotateRight, tr("Obróć w prawo"), enabled = any, onClick = { vm.rotatePages(selected, 90) })
                    RoundIcon(Icons.Rounded.KeyboardArrowLeft, tr("Przesuń wcześniej"), enabled = any && (selected.minOrNull() ?: 0) > 0, onClick = {
                        val sorted = selected.sorted()
                        val target = sorted.first() - 1
                        vm.movePages(sorted, target)
                        selected = sorted.indices.map { target + it }.toSet()
                    })
                    RoundIcon(Icons.Rounded.KeyboardArrowRight, tr("Przesuń później"), enabled = any && (selected.maxOrNull() ?: n) < n - 1, onClick = {
                        val sorted = selected.sorted()
                        val target = sorted.first() + 1
                        vm.movePages(sorted, target)
                        selected = sorted.indices.map { target + it }.toSet()
                    })
                    RoundIcon(Icons.Rounded.DriveFileMove, tr("Przenieś na pozycję…"), enabled = any, onClick = { moveDialog = true })
                    RoundIcon(Icons.Rounded.ContentCopy, tr("Powiel"), enabled = any, onClick = {
                        vm.duplicatePages(selected)
                        selected = emptySet()
                    })
                    RoundIcon(Icons.Rounded.CallSplit, tr("Wyodrębnij do nowego pliku"), enabled = any, onClick = { vm.extractPages(selected) })
                    RoundIcon(Icons.Rounded.Delete, tr("Usuń"), enabled = any && selected.size < n, onClick = { confirmDelete = true })
                    RoundIcon(Icons.Rounded.PostAdd, if (any) tr("Wstaw pustą stronę za zaznaczonymi") else tr("Dodaj pustą stronę na końcu"), onClick = {
                        vm.insertBlank(insertAt)
                        selected = emptySet()
                    })
                    RoundIcon(Icons.Rounded.MergeType, if (any) tr("Wstaw PDF za zaznaczonymi") else tr("Dołącz PDF na końcu"), onClick = { ui.insertPdf(insertAt) })
                    RoundIcon(Icons.Rounded.AddPhotoAlternate, if (any) tr("Wstaw obrazy za zaznaczonymi") else tr("Dołącz obrazy na końcu"), onClick = { ui.insertImages(insertAt) })
                    if (any) RoundIcon(Icons.Rounded.Close, tr("Odznacz"), onClick = { selected = emptySet() })
                }
            }
        }
    }
    if (confirmDelete) {
        DarkDialog(
            tr("Usunąć {0}?", pagesWord(selected.size)),
            onDismiss = { confirmDelete = false },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { confirmDelete = false }
                PillButton(tr("Usuń"), danger = true) {
                    confirmDelete = false
                    vm.deletePages(selected)
                    selected = emptySet()
                }
            },
        ) {
            Text(tr("Usunięcie można cofnąć przyciskiem Cofnij."), color = Palette.TextDim, fontSize = 13.sp)
        }
    }
    if (moveDialog) {
        var pos by remember { mutableStateOf("1") }
        val sorted = selected.sorted()
        val max = n - sorted.size + 1
        DarkDialog(
            tr("Przenieś {0}", pagesWord(sorted.size)),
            onDismiss = { moveDialog = false },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { moveDialog = false }
                PillButton(tr("Przenieś"), enabled = (pos.toIntOrNull() ?: 0) in 1..max) {
                    moveDialog = false
                    val target = pos.toInt() - 1
                    vm.movePages(sorted, target)
                    selected = sorted.indices.map { target + it }.toSet()
                }
            },
        ) {
            DarkField(pos, { pos = it.filter(Char::isDigit).take(5) }, tr("Nowa pozycja pierwszej strony (1–{0})", max), number = true)
        }
    }
}
