@file:Suppress("DEPRECATION")

package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Toc
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.data.SavedSignature
import com.blacksingularities.openpdf.pdf.FieldKind
import com.blacksingularities.openpdf.pdf.InfoEdit
import com.blacksingularities.openpdf.pdf.ImageMark
import com.blacksingularities.openpdf.pdf.InkMark
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.MarkupMark
import com.blacksingularities.openpdf.pdf.NoteMark
import com.blacksingularities.openpdf.pdf.ShapeMark
import com.blacksingularities.openpdf.pdf.SignatureMark
import com.blacksingularities.openpdf.pdf.TextMark
import com.blacksingularities.openpdf.ui.components.ActionRow
import com.blacksingularities.openpdf.ui.components.Chips
import com.blacksingularities.openpdf.ui.components.DarkField
import com.blacksingularities.openpdf.ui.components.Group
import com.blacksingularities.openpdf.ui.components.InfoRow
import com.blacksingularities.openpdf.ui.components.Panel
import com.blacksingularities.openpdf.ui.components.PillButton
import com.blacksingularities.openpdf.ui.components.TabChip
import com.blacksingularities.openpdf.ui.theme.Palette
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.blacksingularities.openpdf.pdf.SplitMode
import com.blacksingularities.openpdf.pdf.SplitPart
import com.blacksingularities.openpdf.pdf.SplitPlan
import com.blacksingularities.openpdf.pdf.describePages
import com.blacksingularities.openpdf.pdf.parsePageRanges
import com.blacksingularities.openpdf.pdf.validCrop
import com.blacksingularities.openpdf.platform.renderPage
import com.blacksingularities.openpdf.ui.components.SwitchRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.blacksingularities.openpdf.platform.formatDate
import com.blacksingularities.openpdf.platform.formatDecimal
import com.blacksingularities.openpdf.platform.fileSystem

/** Panele boczne/pełnoekranowe przeglądarki. */
@Composable
fun ViewerSheets(vm: PdfViewModel) {
    when (vm.sheet) {
        Sheet.OUTLINE -> OutlineSheet(vm)
        Sheet.ANNOTATIONS -> AnnotationsSheet(vm)
        Sheet.FORM -> FormSheet(vm)
        Sheet.INFO -> InfoSheet(vm)
        Sheet.SECURITY -> SecuritySheet(vm)
        Sheet.COMPRESS -> CompressSheet(vm)
        Sheet.EXPORT -> ExportSheet(vm)
        Sheet.MORE -> SettingsPanel(vm, startOnDocument = true)
        Sheet.TEXT -> TextSheet(vm)
        Sheet.SIGNATURES -> SignaturesSheet(vm)
        Sheet.SETTINGS -> SettingsPanel(vm)
        Sheet.SPLIT -> SplitSheet(vm)
        Sheet.CROP -> CropSheet(vm)
        null -> Unit
    }
}

private fun close(vm: PdfViewModel) {
    vm.sheet = null
}

@Composable
fun ColumnScope.DocumentMenu(vm: PdfViewModel) {
    val idx = vm.index
    run {
        Text(vm.doc?.name ?: "", color = Palette.TextDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
        Group(tr("Plik")) {
            ActionRow(Icons.Rounded.Save, tr("Zapisz"), if (vm.doc?.writable == true && vm.doc?.uri != null) tr("W miejscu oryginału") else tr("Wybierz miejsce zapisu")) { close(vm); vm.save() }
            ActionRow(Icons.Rounded.SaveAs, tr("Zapisz jako…"), tr("Nowy plik ze wszystkimi zmianami")) { close(vm); vm.saveAs() }
            ActionRow(Icons.Rounded.Share, tr("Udostępnij")) { close(vm); vm.share() }
            ActionRow(Icons.Rounded.Print, tr("Drukuj")) { close(vm); vm.print() }
        }
        Group(tr("Edycja i podpis")) {
            ActionRow(Icons.Rounded.Layers, tr("Adnotacje"), tr("W pliku: {0} · niezapisane: {1}", idx.annotCount, vm.marks.size)) { vm.sheet = Sheet.ANNOTATIONS }
            ActionRow(Icons.Rounded.Draw, tr("Podpisy"), tr("Zapisane podpisy i nowy podpis")) { vm.sheet = Sheet.SIGNATURES }
            ActionRow(
                Icons.Rounded.FactCheck,
                tr("Formularz"),
                if (idx.fields.isEmpty()) tr("Brak pól – dodaj pola tekstowe i pola wyboru") else tr("Pola do wypełnienia: {0} · dodawanie pól", idx.fields.count { it.kind != FieldKind.BUTTON && it.kind != FieldKind.SIGNATURE }),
            ) { vm.sheet = Sheet.FORM }
        }
        Group(tr("Strony")) {
            ActionRow(Icons.Rounded.GridView, tr("Porządkuj strony"), tr("Kolejność, obracanie, usuwanie, wstawianie, wyodrębnianie")) { close(vm); vm.screen = Screen.PAGES }
            ActionRow(Icons.Rounded.CallSplit, tr("Podziel dokument"), tr("Zakresy, co N stron, zakładki, rozmiar, tekst, strony na pół")) { vm.sheet = Sheet.SPLIT }
            ActionRow(Icons.Rounded.Crop, tr("Przytnij marginesy"), tr("Ręcznie albo automatycznie, dla wybranych stron")) { vm.sheet = Sheet.CROP }
        }
        Group(tr("Konwersja")) {
            ActionRow(Icons.Rounded.Description, tr("Eksport do Worda (.docx)"), tr("Tekst z akapitami i nagłówkami do dalszej edycji")) { close(vm); vm.exportWord() }
            ActionRow(Icons.Rounded.Image, tr("Eksport stron jako obrazy"), tr("PNG lub JPG do galerii")) { vm.sheet = Sheet.EXPORT }
            ActionRow(Icons.Rounded.Notes, tr("Wyodrębnij tekst"), tr("Cały tekst dokumentu do skopiowania lub zapisu")) { close(vm); vm.extractText() }
        }
        Group(tr("Narzędzia")) {
            ActionRow(Icons.Rounded.Compress, tr("Zmniejsz rozmiar"), tr("Kompresja obrazów w dokumencie")) { vm.compressResult = null; vm.sheet = Sheet.COMPRESS }
            ActionRow(Icons.Rounded.Lock, tr("Hasło i ochrona"), if (vm.protection != null) tr("Dokument będzie zapisany z hasłem") else tr("Bez hasła")) { vm.sheet = Sheet.SECURITY }
            ActionRow(Icons.Rounded.Info, tr("Właściwości"), tr("Tytuł, autor, temat, informacje o pliku")) { vm.sheet = Sheet.INFO }
        }
    }
}

// ------------------------------------------------------------------ spis treści i zakładki

@Composable
private fun OutlineSheet(vm: PdfViewModel) {
    val outline = vm.index.outline
    var tab by remember { mutableStateOf(if (outline.isEmpty()) 1 else 0) }
    Panel(tr("Nawigacja"), onClose = { close(vm) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TabChip(Icons.Rounded.Toc, tr("Spis treści"), tab == 0) { tab = 0 }
            TabChip(Icons.Rounded.Bookmark, tr("Zakładki ({0})", vm.bookmarks.size), tab == 1) { tab = 1 }
        }
        if (tab == 0) {
            if (outline.isEmpty()) {
                Text(tr("Ten dokument nie ma spisu treści."), color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            } else {
                Group(null) {
                    outline.forEach { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = e.page != null) {
                                    e.page?.let { vm.jumpTo(it) }
                                    close(vm)
                                }
                                .padding(start = (12 + e.depth * 16).dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                e.title.ifBlank { tr("(bez tytułu)") },
                                color = if (e.depth == 0) Color.White else Palette.TextDim,
                                fontSize = if (e.depth == 0) 14.sp else 13.sp,
                                fontWeight = if (e.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            e.page?.let { Text("${it + 1}", color = Palette.TextFaint, fontSize = 12.sp) }
                        }
                    }
                }
            }
        } else {
            if (vm.bookmarks.isEmpty()) {
                Text(tr("Brak zakładek. Dodasz je przyciskiem zakładki obok numeru strony."), color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            } else {
                Group(null) {
                    vm.bookmarks.forEach { b ->
                        ActionRow(
                            Icons.Rounded.Bookmark,
                            b.title,
                            trailing = {
                                Text(
                                    tr("Usuń"),
                                    color = Palette.Record,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.removeBookmark(b) }.padding(8.dp),
                                )
                            },
                        ) {
                            vm.jumpTo(b.page)
                            close(vm)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ adnotacje

private fun markLabel(m: Mark): String = when (m) {
    is InkMark -> if (m.highlighter) tr("Zakreślacz") else tr("Rysunek")
    is ShapeMark -> m.kind.label
    is TextMark -> tr("Tekst: ") + m.text.take(40)
    is NoteMark -> tr("Notatka: ") + m.text.take(40)
    is MarkupMark -> m.kind.label + ": " + m.text.take(40)
    is ImageMark -> tr("Obraz")
    is SignatureMark -> tr("Podpis")
}

@Composable
private fun AnnotationsSheet(vm: PdfViewModel) {
    var confirmAll by remember { mutableStateOf(false) }
    Panel(tr("Adnotacje"), onClose = { close(vm) }) {
        if (vm.marks.isNotEmpty()) {
            Group(tr("Niezapisane ({0})", vm.marks.size)) {
                vm.marks.sortedBy { it.page }.forEach { m ->
                    ActionRow(
                        Icons.Rounded.Edit,
                        markLabel(m),
                        tr("Strona {0}", m.page + 1),
                        trailing = {
                            Text(tr("Usuń"), color = Palette.Record, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.removePendingMark(m.id) }.padding(8.dp))
                        },
                    ) {
                        vm.jumpTo(m.page, m.bounds())
                        close(vm)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End) {
                    PillButton(tr("Zapisz w dokumencie")) { vm.commitPending() }
                }
            }
        }
        val all = vm.index.annots.flatten()
        Group(tr("W pliku ({0})", all.size)) {
            if (all.isEmpty()) {
                Text(tr("Brak adnotacji w pliku."), color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
            all.forEach { a ->
                ActionRow(
                    Icons.Rounded.Layers,
                    PdfViewModel.annotTypeLabel(a.subtype) + (if (a.contents.isNotBlank()) ": " + a.contents.take(50) else ""),
                    tr("Strona {0}", a.page + 1) + (if (a.author.isNotBlank()) " · ${a.author}" else ""),
                    trailing = {
                        Text(tr("Usuń"), color = Palette.Record, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.deleteAnnotation(a) }.padding(8.dp))
                    },
                ) {
                    vm.jumpTo(a.page, a.rect)
                    close(vm)
                }
            }
        }
        if (all.isNotEmpty()) {
            Group(null) {
                ActionRow(Icons.Rounded.DeleteSweep, if (confirmAll) tr("Dotknij ponownie, aby usunąć wszystkie") else tr("Usuń wszystkie adnotacje z pliku"), tr("Odnośniki i pola formularza zostają"), danger = true) {
                    if (confirmAll) {
                        confirmAll = false
                        vm.deleteAllAnnotations()
                    } else {
                        confirmAll = true
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ formularz

@Composable
private fun FormSheet(vm: PdfViewModel) {
    val fields = vm.index.fields.filter { it.kind != FieldKind.BUTTON }
    Panel(tr("Formularz"), onClose = { close(vm) }) {
        Group(tr("Utwórz formularz")) {
            ActionRow(Icons.Rounded.TextFields, tr("Dodaj pole tekstowe"), tr("Potem dotknij miejsca na stronie")) { vm.startPlacingField(FieldKind.TEXT) }
            ActionRow(Icons.Rounded.CheckBox, tr("Dodaj pole wyboru"), tr("Kwadrat do zaznaczenia – potem dotknij strony")) { vm.startPlacingField(FieldKind.CHECKBOX) }
        }
        Text(
            if (fields.isEmpty()) tr("Dokument nie ma jeszcze pól formularza.") else tr("Pola są podświetlone na stronach – dotknij pola w dokumencie albo na liście."),
            color = Palette.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        if (fields.isNotEmpty()) Group(tr("Pola ({0})", fields.size)) {
            fields.forEach { f ->
                val value = when (f.kind) {
                    FieldKind.CHECKBOX -> if (f.value == "true") tr("Zaznaczone") else tr("Niezaznaczone")
                    FieldKind.SIGNATURE -> tr("Pole podpisu cyfrowego")
                    else -> f.value.ifBlank { tr("(puste)") }
                }
                ActionRow(
                    Icons.Rounded.FactCheck,
                    f.label.ifBlank { f.name },
                    value + (f.widgets.firstOrNull()?.let { tr(" · strona {0}", it.page + 1) } ?: "") + (if (f.readOnly) tr(" · tylko do odczytu") else ""),
                    enabled = !f.readOnly && f.kind != FieldKind.SIGNATURE,
                ) {
                    f.widgets.firstOrNull()?.let { vm.jumpTo(it.page, it.rect) }
                    vm.openField(f)
                }
            }
        }
        if (fields.isNotEmpty()) Group(null) {
            ActionRow(Icons.Rounded.Layers, tr("Spłaszcz formularz"), tr("Wartości staną się zwykłą treścią stron – pól nie da się już edytować")) { vm.flattenForm(); close(vm) }
        }
    }
}

// ------------------------------------------------------------------ właściwości

@Composable
private fun InfoSheet(vm: PdfViewModel) {
    val info = vm.index.info
    var title by remember { mutableStateOf(info.title) }
    var author by remember { mutableStateOf(info.author) }
    var subject by remember { mutableStateOf(info.subject) }
    var keywords by remember { mutableStateOf(info.keywords) }
    Panel(tr("Właściwości"), onClose = { close(vm) }) {
        Group(tr("Opis")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkField(title, { title = it }, tr("Tytuł"))
                DarkField(author, { author = it }, tr("Autor"))
                DarkField(subject, { subject = it }, tr("Temat"))
                DarkField(keywords, { keywords = it }, tr("Słowa kluczowe"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton(tr("Zapisz właściwości"), enabled = title != info.title || author != info.author || subject != info.subject || keywords != info.keywords) {
                        vm.setInfo(InfoEdit(title, author, subject, keywords))
                    }
                }
            }
        }
        Group(tr("Plik")) {
            InfoRow(Icons.Rounded.Description, tr("Nazwa"), vm.doc?.name ?: "")
            InfoRow(Icons.Rounded.Layers, tr("Strony"), "${vm.index.pageCount}")
            InfoRow(Icons.Rounded.Info, tr("Rozmiar pliku roboczego"), PdfViewModel.formatSize(vm.doc?.source?.let { fileSystem.metadataOrNull(it)?.size } ?: 0))
            InfoRow(Icons.Rounded.Title, tr("Wersja PDF"), info.version)
            InfoRow(Icons.Rounded.Person, tr("Program"), listOf(info.creator, info.producer).filter { it.isNotBlank() }.joinToString(" · "))
            info.created?.let { InfoRow(Icons.Rounded.Info, tr("Utworzono"), formatDate(it, withTime = true)) }
            info.modified?.let { InfoRow(Icons.Rounded.Info, tr("Zmieniono"), formatDate(it, withTime = true)) }
            vm.index.geos.firstOrNull()?.let { g ->
                InfoRow(Icons.Rounded.Description, tr("Rozmiar strony 1"), "${formatDecimal(g.displayWidth / 72f * 25.4f, 0)} × ${formatDecimal(g.displayHeight / 72f * 25.4f, 0)} mm")
            }
        }
    }
}

// ------------------------------------------------------------------ hasło

@Composable
private fun SecuritySheet(vm: PdfViewModel) {
    var user by remember { mutableStateOf(vm.protection?.user ?: "") }
    var owner by remember { mutableStateOf(vm.protection?.owner?.takeIf { it != vm.protection?.user } ?: "") }
    Panel(tr("Hasło i ochrona"), onClose = { close(vm) }) {
        Text(
            tr("Hasło jest dodawane przy zapisie (AES-256). Dokument w aplikacji pozostaje otwarty, dopóki go nie zamkniesz."),
            color = Palette.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Group(if (vm.protection != null) tr("Dokument chroniony") else tr("Bez ochrony")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkField(user, { user = it }, tr("Hasło do otwarcia"), password = true)
                DarkField(owner, { owner = it }, tr("Hasło właściciela (opcjonalnie)"), password = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (vm.protection != null) {
                        PillButton(tr("Usuń hasło"), primary = false) {
                            vm.protection = null
                            vm.toast(tr("Hasło zostanie usunięte przy zapisie"))
                            close(vm)
                        }
                    }
                    PillButton(tr("Ustaw hasło"), enabled = user.length >= 1) {
                        vm.protection = Protection(user, owner.ifEmpty { user })
                        vm.toast(tr("Hasło zostanie dodane przy zapisie"))
                        close(vm)
                    }
                }
            }
        }
        Group(null) {
            ActionRow(if (vm.protection != null) Icons.Rounded.Lock else Icons.Rounded.LockOpen, tr("Zapisz teraz"), tr("Zapisz dokument z bieżącymi ustawieniami ochrony")) {
                close(vm)
                vm.save()
            }
        }
    }
}

// ------------------------------------------------------------------ kompresja

@Composable
private fun CompressSheet(vm: PdfViewModel) {
    data class Level(val name: String, val maxSide: Int, val quality: Float, val hint: String)
    val levels = listOf(
        Level(tr("Lekka"), 2400, 0.85f, tr("Obrazy do 2400 px, jakość 85% – prawie bez różnic")),
        Level(tr("Średnia"), 1600, 0.72f, tr("Obrazy do 1600 px, jakość 72% – dobre do czytania na ekranie")),
        Level(tr("Mocna"), 1100, 0.55f, tr("Obrazy do 1100 px, jakość 55% – najmniejszy plik")),
    )
    var level by remember { mutableStateOf(levels[1]) }
    Panel(tr("Zmniejsz rozmiar"), onClose = { close(vm) }) {
        Group(null) {
            InfoRow(Icons.Rounded.Info, tr("Obecny rozmiar"), PdfViewModel.formatSize(vm.doc?.source?.let { fileSystem.metadataOrNull(it)?.size } ?: 0))
            Chips(Icons.Rounded.Compress, tr("Poziom"), levels, level, { it.name }, hint = level.hint) { level = it }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                PillButton(tr("Kompresuj")) { vm.compress(level.maxSide, level.quality) }
            }
        }
        vm.compressResult?.let {
            Group(tr("Wynik")) {
                Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                Text(tr("Zapisz dokument, aby zachować mniejszą wersję. Zmianę można cofnąć."), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ eksport obrazów

@Composable
private fun ExportSheet(vm: PdfViewModel) {
    val n = vm.index.pageCount
    var range by remember { mutableStateOf("bieżąca") }
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf("$n") }
    var dpi by remember { mutableStateOf(150) }
    var png by remember { mutableStateOf(false) }
    Panel(tr("Eksport jako obrazy"), onClose = { close(vm) }) {
        Group(null) {
            Chips(Icons.Rounded.Description, tr("Strony"), listOf("bieżąca", "wszystkie", "zakres"), range, { tr(it.replaceFirstChar(Char::titlecase)) }) { range = it }
            if (range == "zakres") {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { DarkField(from, { from = it.filter(Char::isDigit) }, tr("Od"), number = true) }
                    Box(Modifier.weight(1f)) { DarkField(to, { to = it.filter(Char::isDigit) }, tr("Do"), number = true) }
                }
            }
            Chips(Icons.Rounded.Image, tr("Rozdzielczość"), listOf(96, 150, 220, 300), dpi, { "$it DPI" }) { dpi = it }
            Chips(Icons.Rounded.Image, tr("Format"), listOf(false, true), png, { if (it) "PNG" else "JPG" }) { png = it }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                PillButton(tr("Eksportuj")) {
                    val pages = when (range) {
                        "bieżąca" -> listOf(vm.currentPage)
                        "wszystkie" -> (0 until n).toList()
                        else -> {
                            val a = (from.toIntOrNull() ?: 1).coerceIn(1, n)
                            val b = (to.toIntOrNull() ?: n).coerceIn(a, n)
                            (a - 1 until b).toList()
                        }
                    }
                    close(vm)
                    vm.exportImages(pages, dpi, png)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tekst

@Composable
private fun TextSheet(vm: PdfViewModel) {
    val text = vm.extractedText ?: ""
    val clipboard = LocalClipboardManager.current
    Panel(
        tr("Tekst dokumentu"),
        onClose = { close(vm) },
        actions = {
            Text(
                tr("Kopiuj"),
                color = Palette.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable {
                    clipboard.setText(AnnotatedString(text))
                    vm.toast(tr("Skopiowano {0} znaków", text.length))
                }.padding(horizontal = 14.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                tr("Zapisz .txt"),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { vm.saveExtractedText() }.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        },
    ) {
        SelectionContainer {
            Text(text.ifBlank { tr("Brak tekstu.") }, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(6.dp))
        }
    }
}

// ------------------------------------------------------------------ podpisy

@Composable
private fun SignaturesSheet(vm: PdfViewModel) {
    var list by remember { mutableStateOf(vm.signatures()) }
    Panel(tr("Podpisy"), onClose = { close(vm) }) {
        Text(tr("Dotknij podpisu, aby wstawić go na bieżącej stronie. Potem przesuń go i dopasuj rozmiar."), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        list.forEach { sig ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable { vm.placeSignatureOnCurrent(sig) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignaturePreview(sig, Modifier.weight(1f).height(70.dp))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable {
                        vm.deleteSignature(sig.id)
                        list = vm.signatures()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.Delete, tr("Usuń podpis"), tint = Palette.Record)
                }
            }
        }
        PillButton(tr("Nowy podpis"), modifier = Modifier.fillMaxWidth()) {
            vm.sheet = null
            vm.signaturePad = true
        }
    }
}

@Composable
fun SignaturePreview(sig: SavedSignature, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val boxAspect = size.width / size.height
        val (w, h) = if (sig.aspect > boxAspect) size.width to size.width / sig.aspect else size.height * sig.aspect to size.height
        val ox = (size.width - w) / 2f
        val oy = (size.height - h) / 2f
        for (s in sig.strokes) {
            if (s.isEmpty()) continue
            val p = Path()
            p.moveTo(ox + s[0].x * w, oy + s[0].y * h)
            for (i in 1 until s.size) p.lineTo(ox + s[i].x * w, oy + s[i].y * h)
            drawPath(p, Color(0xFF0D2A6B), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ------------------------------------------------------------------ podział dokumentu

private fun splitModeLabel(m: SplitMode): String = when (m) {
    SplitMode.RANGES -> tr("Zakresy")
    SplitMode.EVERY -> tr("Co N stron")
    SplitMode.BOOKMARKS -> tr("Zakładki")
    SplitMode.SIZE -> tr("Rozmiar")
    SplitMode.TEXT -> tr("Tekst")
}

@Composable
private fun SplitSheet(vm: PdfViewModel) {
    val n = vm.index.pageCount
    val outline = vm.index.outline
    val levels = SplitPlan.outlineLevels(outline)
    var mode by remember { mutableStateOf(SplitMode.RANGES) }
    var ranges by remember { mutableStateOf(if (n > 1) "1-${(n + 1) / 2}, ${(n + 1) / 2 + 1}-$n" else "1") }
    var every by remember { mutableStateOf("1") }
    var level by remember { mutableStateOf(0) }
    var sizeMb by remember { mutableStateOf("10") }
    var text by remember { mutableStateOf("") }
    var vertical by remember { mutableStateOf(true) }
    var swap by remember { mutableStateOf(false) }
    var allPages by remember { mutableStateOf(true) }
    Panel(tr("Podziel dokument"), onClose = { close(vm) }) {
        Text(
            tr("Części zapisujesz jako osobne pliki PDF – kilka części trafia do jednego archiwum ZIP. Bieżący dokument się nie zmienia."),
            color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp),
        )
        Group(tr("Na osobne pliki")) {
            Chips(Icons.Rounded.CallSplit, tr("Sposób"), SplitMode.entries.toList(), mode, ::splitModeLabel) { mode = it }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                when (mode) {
                    SplitMode.RANGES -> DarkField(ranges, { ranges = it }, tr("Zakresy stron, np. 1-3, 4-10, 11-"))
                    SplitMode.EVERY -> DarkField(every, { v -> every = v.filter(Char::isDigit).take(4) }, tr("Stron w każdej części (1 – każda strona osobno)"), number = true)
                    SplitMode.BOOKMARKS -> when {
                        outline.isEmpty() -> Text(tr("Ten dokument nie ma spisu treści – wybierz inny sposób."), color = Palette.TextDim, fontSize = 13.sp)
                        levels > 1 -> Chips(Icons.Rounded.Toc, tr("Poziom spisu treści"), (0 until levels).toList(), level, { tr("Poziom {0}", it + 1) }) { level = it }
                        else -> Unit
                    }
                    SplitMode.SIZE -> DarkField(sizeMb, { v -> sizeMb = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) }, tr("Największy rozmiar części (MB)"), number = true)
                    SplitMode.TEXT -> DarkField(text, { text = it }, tr("Tekst, od którego zaczyna się nowa część"))
                }
            }
            val plan: List<SplitPart>? = when (mode) {
                SplitMode.RANGES -> SplitPlan.ranges(ranges, n)
                SplitMode.EVERY -> every.toIntOrNull()?.takeIf { it > 0 }?.let { SplitPlan.every(n, it) } ?: emptyList()
                SplitMode.BOOKMARKS -> SplitPlan.byOutline(outline, n, level.coerceAtMost(maxOf(0, levels - 1)))
                SplitMode.SIZE, SplitMode.TEXT -> null
            }
            val mb = sizeMb.replace(',', '.').toFloatOrNull()
            val ready = when (mode) {
                SplitMode.SIZE -> mb != null && mb > 0f
                SplitMode.TEXT -> text.isNotBlank()
                else -> !plan.isNullOrEmpty()
            }
            Text(
                when {
                    mode == SplitMode.SIZE -> tr("Rozmiar stron zostanie oszacowany – części mogą być nieco mniejsze niż limit.")
                    mode == SplitMode.TEXT -> tr("Nowa część zaczyna się na każdej stronie, na której występuje ten tekst (bez względu na wielkość liter i polskie znaki).")
                    plan.isNullOrEmpty() -> tr("Brak części – sprawdź ustawienia.")
                    else -> {
                        val p = plan.orEmpty()
                        tr("Części: {0}", p.size) + " · " + p.take(4).joinToString(" | ") { describePages(it.pages) } + (if (p.size > 4) " …" else "")
                    }
                },
                color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                PillButton(tr("Podziel i zapisz"), enabled = ready) {
                    close(vm)
                    when (mode) {
                        SplitMode.SIZE -> vm.splitBySize(mb ?: 10f)
                        SplitMode.TEXT -> vm.splitByText(text)
                        else -> vm.splitInto(plan.orEmpty())
                    }
                }
            }
        }
        Group(tr("Strony na pół")) {
            Text(
                tr("Skan rozkładówki (dwie strony na jednym arkuszu) zamienia się w dwie osobne strony. Zmienia bieżący dokument – zmianę można cofnąć."),
                color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Chips(Icons.Rounded.VerticalSplit, tr("Cięcie"), listOf(true, false), vertical, { if (it) tr("Pionowe: lewa | prawa") else tr("Poziome: góra / dół") }) { vertical = it }
            Chips(Icons.Rounded.Description, tr("Strony"), listOf(true, false), allPages, { if (it) tr("Wszystkie") else tr("Bieżąca") }) { allPages = it }
            SwitchRow(Icons.Rounded.SwapHoriz, tr("Najpierw druga połowa"), tr("Prawa (dolna) część jako pierwsza – manga, pismo od prawej"), swap) { swap = it }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                PillButton(tr("Podziel strony")) {
                    close(vm)
                    vm.splitHalves(if (allPages) (0 until n).toList() else listOf(vm.currentPage), vertical, swap)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ przycinanie

@Composable
private fun MarginSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(64.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..40f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Palette.Amber, activeTrackColor = Palette.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
        )
        Text("${value.roundToInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(44.dp).padding(start = 8.dp))
    }
}

@Composable
private fun CropSheet(vm: PdfViewModel) {
    val n = vm.index.pageCount
    var left by remember { mutableStateOf(0f) }
    var top by remember { mutableStateOf(0f) }
    var right by remember { mutableStateOf(0f) }
    var bottom by remember { mutableStateOf(0f) }
    var which by remember { mutableStateOf(0) }
    var range by remember { mutableStateOf("1-$n") }
    var detecting by remember { mutableStateOf(false) }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    val page = vm.currentPage
    LaunchedEffect(page, vm.revision) { thumb = vm.render?.renderPage(page, 360) }
    val margins = floatArrayOf(left / 100f, top / 100f, right / 100f, bottom / 100f)
    val pages = when (which) {
        0 -> listOf(page)
        1 -> (0 until n).toList()
        else -> parsePageRanges(range, n)
    }
    Panel(tr("Przytnij marginesy"), onClose = { close(vm) }) {
        Text(
            tr("Widoczny obszar stron zostaje zmniejszony – treść poza nim nie jest wyświetlana ani drukowana. Zmianę można cofnąć."),
            color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp),
        )
        thumb?.let { img ->
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.height(220.dp).aspectRatio(img.width.toFloat() / img.height.coerceAtLeast(1))) {
                    drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    val l = size.width * margins[0]
                    val t = size.height * margins[1]
                    val r = size.width * (1f - margins[2])
                    val b = size.height * (1f - margins[3])
                    val shade = Color.Black.copy(alpha = 0.55f)
                    drawRect(shade, Offset.Zero, Size(size.width, t))
                    drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                    drawRect(shade, Offset(0f, t), Size(l, b - t))
                    drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
                    drawRect(Palette.Amber, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Group(tr("Marginesy (% strony {0})", page + 1)) {
            MarginSlider(tr("Lewy"), left) { left = it }
            MarginSlider(tr("Górny"), top) { top = it }
            MarginSlider(tr("Prawy"), right) { right = it }
            MarginSlider(tr("Dolny"), bottom) { bottom = it }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                PillButton(tr("Wyzeruj"), primary = false) {
                    left = 0f; top = 0f; right = 0f; bottom = 0f
                }
                PillButton(if (detecting) tr("Wykrywanie…") else tr("Wykryj białe brzegi"), primary = false, enabled = !detecting) {
                    scope.launch {
                        detecting = true
                        val m = vm.autoMargins(page)
                        detecting = false
                        if (m == null || m.all { it <= 0.001f }) {
                            vm.toast(tr("Nie wykryto pustych marginesów na tej stronie"))
                        } else {
                            left = m[0] * 100f; top = m[1] * 100f; right = m[2] * 100f; bottom = m[3] * 100f
                        }
                    }
                }
            }
        }
        Group(tr("Strony")) {
            Chips(Icons.Rounded.Description, tr("Zastosuj do"), listOf(0, 1, 2), which, { listOf(tr("Bieżąca"), tr("Wszystkie"), tr("Zakres"))[it] }) { which = it }
            if (which == 2) {
                Box(Modifier.padding(horizontal = 12.dp)) { DarkField(range, { range = it }, tr("Strony, np. 1-3, 5, 8-")) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                PillButton(tr("Przytnij ({0})", pages.size), enabled = pages.isNotEmpty() && margins.any { it > 0f } && validCrop(margins)) {
                    close(vm)
                    vm.cropPages(pages, margins)
                }
            }
        }
    }
}
