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
import java.text.DateFormat
import java.util.Date

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
        Group(tr("Treść")) {
            ActionRow(Icons.Rounded.FactCheck, tr("Formularz"), if (idx.fields.isEmpty()) tr("Dokument nie ma pól formularza") else tr("Pola do wypełnienia: {0}", idx.fields.count { it.kind != FieldKind.BUTTON && it.kind != FieldKind.SIGNATURE }), enabled = idx.fields.isNotEmpty()) { vm.sheet = Sheet.FORM }
            ActionRow(Icons.Rounded.Layers, tr("Adnotacje"), tr("W pliku: {0} · niezapisane: {1}", idx.annotCount, vm.marks.size)) { vm.sheet = Sheet.ANNOTATIONS }
            ActionRow(Icons.Rounded.Notes, tr("Wyodrębnij tekst"), tr("Cały tekst dokumentu do skopiowania lub zapisu")) { close(vm); vm.extractText() }
            ActionRow(Icons.Rounded.Draw, tr("Podpisy"), tr("Zapisane podpisy i nowy podpis")) { vm.sheet = Sheet.SIGNATURES }
        }
        Group(tr("Narzędzia")) {
            ActionRow(Icons.Rounded.Image, tr("Eksport stron jako obrazy"), tr("PNG lub JPG do galerii")) { vm.sheet = Sheet.EXPORT }
            ActionRow(Icons.Rounded.Compress, tr("Zmniejsz rozmiar"), tr("Kompresja obrazów w dokumencie")) { vm.compressResult = null; vm.sheet = Sheet.COMPRESS }
            ActionRow(Icons.Rounded.MergeType, tr("Wstaw strony z innego PDF"), tr("Na końcu dokumentu – kolejność zmienisz w widoku stron")) { close(vm); vm.screen = Screen.PAGES }
            ActionRow(Icons.Rounded.CallSplit, tr("Wyodrębnij lub podziel strony"), tr("W widoku stron zaznacz strony i wybierz „Wyodrębnij”")) { close(vm); vm.screen = Screen.PAGES }
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
        Text(tr("Pola są podświetlone na stronach – dotknij pola w dokumencie albo na liście."), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Group(tr("Pola ({0})", fields.size)) {
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
        Group(null) {
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
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
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
            InfoRow(Icons.Rounded.Info, tr("Rozmiar pliku roboczego"), PdfViewModel.formatSize(vm.doc?.source?.length() ?: 0))
            InfoRow(Icons.Rounded.Title, tr("Wersja PDF"), info.version)
            InfoRow(Icons.Rounded.Person, tr("Program"), listOf(info.creator, info.producer).filter { it.isNotBlank() }.joinToString(" · "))
            info.created?.let { InfoRow(Icons.Rounded.Info, tr("Utworzono"), fmt.format(Date(it))) }
            info.modified?.let { InfoRow(Icons.Rounded.Info, tr("Zmieniono"), fmt.format(Date(it))) }
            vm.index.geos.firstOrNull()?.let { g ->
                InfoRow(Icons.Rounded.Description, tr("Rozmiar strony 1"), "%.0f × %.0f mm".format(g.displayWidth / 72f * 25.4f, g.displayHeight / 72f * 25.4f))
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
            InfoRow(Icons.Rounded.Info, tr("Obecny rozmiar"), PdfViewModel.formatSize(vm.doc?.source?.length() ?: 0))
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
