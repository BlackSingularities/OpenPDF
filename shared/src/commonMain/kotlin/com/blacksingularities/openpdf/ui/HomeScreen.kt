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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import com.blacksingularities.openpdf.resources.Res
import com.blacksingularities.openpdf.resources.openpdf_brand
import com.blacksingularities.openpdf.data.RecentDoc
import com.blacksingularities.openpdf.ui.components.DarkDialog
import com.blacksingularities.openpdf.ui.components.DarkField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.blacksingularities.openpdf.ui.components.PillButton
import com.blacksingularities.openpdf.ui.components.RoundIcon
import com.blacksingularities.openpdf.ui.theme.GlassLevel
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.ui.theme.glass
import com.blacksingularities.openpdf.ui.theme.reactive
import com.blacksingularities.openpdf.platform.LocalPlatformUi
import com.blacksingularities.openpdf.platform.decodeImageFile
import com.blacksingularities.openpdf.platform.formatDate
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeScreen(vm: PdfViewModel) {
    var blank by remember { mutableStateOf(false) }
    var removeRecent by remember { mutableStateOf<RecentDoc?>(null) }
    val ui = LocalPlatformUi.current
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.RECENT) }
    var favOnly by remember { mutableStateOf(false) }
    val favorites = vm.favorites
    val recents = remember(vm.recents, favorites, query, sort, favOnly) {
        val q = query.trim()
        vm.recents
            .filter { (!favOnly || it.uri in favorites) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
            .let { list ->
                when (sort) {
                    LibrarySort.RECENT -> list
                    LibrarySort.NAME -> list.sortedBy { it.name.lowercase() }
                    LibrarySort.PROGRESS -> list.sortedByDescending { if (it.pages > 0) (it.lastPage + 1f) / it.pages else 0f }
                }
            }
            // Ulubione zawsze na początku.
            .sortedByDescending { it.uri in favorites }
    }
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
                    Image(
                        painter = painterResource(Res.drawable.openpdf_brand),
                        contentDescription = null,
                        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OpenDocument", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text(tr("Czytaj PDF, EPUB, DOCX i inne dokumenty"), fontSize = 13.sp, color = Palette.TextDim)
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
                            .reactive(tr("Otwórz dokument: PDF, EPUB, DOCX, ODT, FB2, TXT…")) { ui.openPdf() }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = Palette.Ink, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(tr("Otwórz dokument"), color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
            if (vm.recents.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(tr("Otwarte pliki pojawią się tutaj."), color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("BIBLIOTEKA"), color = Palette.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 4.dp))
                        if (vm.recents.size > 6) DarkField(query, { query = it }, tr("Szukaj po nazwie"))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LibraryChip(tr("Ulubione"), favOnly) { favOnly = !favOnly }
                            LibrarySort.entries.forEach { o -> LibraryChip(o.label(), sort == o) { sort = o } }
                        }
                        if (recents.isEmpty()) Text(tr("Brak pasujących plików."), color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            items(recents, key = { it.uri }) { r ->
                RecentCard(vm, r, r.uri in favorites, onOpen = { vm.open(r.uri) }, onLong = { removeRecent = r })
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
                PillButton(if (r.uri in favorites) tr("Usuń z ulubionych") else tr("Do ulubionych"), primary = false) {
                    vm.toggleFavorite(r.uri)
                    removeRecent = null
                }
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
private fun RecentCard(vm: PdfViewModel, r: RecentDoc, favorite: Boolean, onOpen: () -> Unit, onLong: () -> Unit) {
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
            Icon(
                if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                if (favorite) tr("Usuń z ulubionych") else tr("Do ulubionych"),
                tint = if (favorite) Palette.Amber else Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { vm.toggleFavorite(r.uri) }
                    .padding(5.dp)
                    .size(18.dp),
            )
            r.name.substringAfterLast('.', "").takeIf { it.length in 2..5 }?.let { ext ->
                Text(
                    ext.uppercase(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
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

private enum class LibrarySort {
    RECENT, NAME, PROGRESS;

    fun label(): String = when (this) {
        RECENT -> tr("Ostatnio otwarte")
        NAME -> tr("Nazwa")
        PROGRESS -> tr("Postęp czytania")
    }
}

@Composable
private fun LibraryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Palette.Ink else Color.White,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Amber else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
