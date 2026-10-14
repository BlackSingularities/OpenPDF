@file:Suppress("DEPRECATION")

package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.data.RecentDoc
import com.blacksingularities.openpdf.ui.components.DarkDialog
import com.blacksingularities.openpdf.ui.components.PillButton
import com.blacksingularities.openpdf.ui.components.RoundIcon
import com.blacksingularities.openpdf.ui.theme.GlassLevel
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.ui.theme.glass
import com.blacksingularities.openpdf.ui.theme.reactive
import com.blacksingularities.openpdf.platform.LocalPlatformUi
import com.blacksingularities.openpdf.platform.decodeImageFile
import com.blacksingularities.openpdf.platform.formatDate

@Composable
fun HomeScreen(vm: PdfViewModel) {
    var blank by remember { mutableStateOf(false) }
    var removeRecent by remember { mutableStateOf<RecentDoc?>(null) }
    val ui = LocalPlatformUi.current
    val recents = vm.recents
    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("OpenPDF", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text(tr("Czytaj, adnotuj, podpisuj i układaj PDF-y"), fontSize = 13.sp, color = Palette.TextDim)
                    }
                    RoundIcon(Icons.Rounded.Settings, tr("Ustawienia"), badge = if (vm.updatePending) "1" else null, onClick = { vm.sheet = Sheet.SETTINGS })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                UpdateBanner(vm)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White)
                            .reactive(tr("Otwórz plik PDF")) { ui.openPdf() }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = Palette.Ink, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(tr("Otwórz PDF"), color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(tr("Z telefonu, karty lub chmury"), color = Palette.Ink.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeAction(Icons.Rounded.NoteAdd, tr("Nowy pusty"), Modifier.weight(1f)) { blank = true }
                        HomeAction(Icons.Rounded.PhotoLibrary, tr("Z obrazów"), Modifier.weight(1f)) { ui.createFromImages() }
                        HomeAction(Icons.Rounded.MergeType, tr("Scal pliki"), Modifier.weight(1f)) { ui.mergePdfs() }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (recents.isEmpty()) tr("Otwarte pliki pojawią się tutaj.") else tr("OSTATNIE"),
                    color = if (recents.isEmpty()) Palette.TextDim else Palette.Amber,
                    fontSize = if (recents.isEmpty()) 13.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (recents.isEmpty()) 0.sp else 1.4.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            items(recents, key = { it.uri }) { r ->
                RecentCard(vm, r, onOpen = { vm.open(r.uri) }, onLong = { removeRecent = r })
            }
        }
    }
    if (blank) BlankDocDialog(onDismiss = { blank = false }) { pages, landscape ->
        blank = false
        vm.createBlank(pages, landscape)
    }
    removeRecent?.let { r ->
        DarkDialog(
            r.name,
            onDismiss = { removeRecent = null },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { removeRecent = null }
                PillButton(tr("Usuń z listy"), danger = true) {
                    vm.forgetRecent(r.uri)
                    removeRecent = null
                }
            },
        ) {
            Text(tr("Plik pozostanie na urządzeniu – zniknie tylko z listy ostatnich."), color = Palette.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HomeAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .reactive(label, onClick = onClick)
            .glass(RoundedCornerShape(20.dp), GlassLevel.Thin)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun RecentCard(vm: PdfViewModel, r: RecentDoc, onOpen: () -> Unit, onLong: () -> Unit) {
    val thumb = remember(r.uri, vm.recentsThumbVersion) {
        decodeImageFile(vm.library.thumbFile(r.uri))
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLong)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.PanelHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(thumb, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            } else {
                Icon(Icons.Rounded.PictureAsPdf, null, tint = Palette.TextFaint, modifier = Modifier.size(40.dp))
            }
            if (r.pages > 0) {
                Text(
                    if (r.lastPage > 0) "${r.lastPage + 1}/${r.pages}" else tr("{0} str.", r.pages),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .widthIn(min = 20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Text(r.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
        Text(formatDate(r.opened, withTime = false), color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
    }
}
