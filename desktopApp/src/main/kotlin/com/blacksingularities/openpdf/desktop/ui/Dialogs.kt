package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.Dialog
import com.blacksingularities.openpdf.desktop.Flow
import com.blacksingularities.openpdf.desktop.PageLayout
import com.blacksingularities.openpdf.desktop.Paper
import com.blacksingularities.openpdf.desktop.Protection
import com.blacksingularities.openpdf.desktop.UpdateStatus
import com.blacksingularities.openpdf.desktop.humanBytes
import com.blacksingularities.openpdf.i18n.AppLanguage
import com.blacksingularities.openpdf.pdf.InfoEdit
import com.blacksingularities.openpdf.platform.formatDate
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.net.URI
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@Composable
fun Dialogs(app: AppState) {
    val close = { app.dialog = Dialog.NONE }
    when (app.dialog) {
        Dialog.NONE -> Unit
        Dialog.SETTINGS -> Modal(close, 560.dp) { SettingsDialog(app, close) }
        Dialog.SHORTCUTS -> Modal(close, 600.dp) { ShortcutsDialog(close) }
        Dialog.ABOUT -> Modal(close, 420.dp) { AboutDialog(app, close) }
        Dialog.PROPERTIES -> Modal(close, 540.dp) { PropertiesDialog(app, close) }
        Dialog.UPDATE -> Modal(close, 480.dp) { UpdateDialog(app, close) }
        Dialog.EXPORT_IMAGES -> Modal(close, 460.dp) { ExportImagesDialog(app, close) }
        Dialog.COMPRESS -> Modal(close, 460.dp) { CompressDialog(app, close) }
        Dialog.SECURITY -> Modal(close, 460.dp) { SecurityDialog(app, close) }
        Dialog.TEXT -> Modal(close, 720.dp) { TextDialog(app, close) }
        Dialog.NEW_DOC -> Modal(close, 420.dp) { NewDocDialog(app, close) }
        Dialog.UNSAVED -> Modal({ app.resolveClosing(null) }, 460.dp) { UnsavedDialog(app) }
        Dialog.INPUT -> Modal({ app.input = null; close() }, 460.dp) { InputDialog(app, close) }
    }
}

@Composable
private fun DialogTitle(text: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(text, Modifier.weight(1f), style = Type.title, weight = FontWeight.SemiBold)
        IconBtn(Icons.Rounded.Close, L("Zamknij", "Close"), onClose, shortcut = "Esc")
    }
}

@Composable
private fun SettingRow(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Txt(label)
            if (hint != null) Txt(hint, style = Type.tiny, color = P.text3, maxLines = 2)
        }
        content()
    }
}

@Composable
private fun Buttons(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

/** Podgląd motywu: miniatura okna w jego kolorach. */
@Composable
private fun ThemeCard(id: ThemeId, accent: Accent, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val p = palette(id, accent)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).border(2.dp, if (on) P.accent else P.line, shape)
            .pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(8.dp)).background(p.bg).border(1.dp, p.line, RoundedCornerShape(8.dp))) {
            Row(Modifier.fillMaxWidth().height(14.dp).background(p.panel).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(width = 18.dp, height = 6.dp).background(p.accent, RoundedCornerShape(3.dp)))
                Box(Modifier.size(width = 14.dp, height = 6.dp).background(p.raised2, RoundedCornerShape(3.dp)))
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.width(18.dp).fillMaxHeight().background(p.panel))
                Box(Modifier.weight(1f).fillMaxHeight().background(p.canvas), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 26.dp, height = 34.dp).background(Color.White))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Txt(id.label, style = Type.small, weight = if (on) FontWeight.SemiBold else null, color = if (on) P.text else P.text2)
    }
}

@Composable
private fun SettingsDialog(app: AppState, close: () -> Unit) {
    val st = app.settings
    fun changed() = app.saveSoon()
    DialogTitle(L("Ustawienia", "Settings"), close)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionLabel(L("Wygląd", "Appearance"))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (t in ThemeId.entries) ThemeCard(t, st.accent, st.theme == t, { st.theme = t; changed() }, Modifier.weight(1f))
        }
        SettingRow(L("Kolor akcentu", "Accent colour"), st.accent.label) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in Accent.entries) {
                    val c = Color(if (palette(st.theme).dark) a.dark else a.light)
                    Tip(a.label) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape)
                                .border(2.dp, if (st.accent == a) P.text else Color.Transparent, CircleShape)
                                .padding(3.dp).clip(CircleShape).background(c).border(1.dp, P.line2, CircleShape)
                                .pointerHoverIcon(PointerIcon.Hand).clickable { st.accent = a; changed() },
                        )
                    }
                }
            }
        }
        SettingRow(L("Język", "Language")) {
            Seg(
                listOf(SegItem(AppLanguage.SYSTEM, L("System", "System")), SegItem(AppLanguage.PL, "Polski"), SegItem(AppLanguage.EN, "English")),
                st.language, { app.setLanguage(it) },
            )
        }
        SettingRow(L("Papier", "Paper"), L("Kolory stron dokumentu", "Colours of document pages")) {
            Seg(
                listOf(SegItem(Paper.NORMAL, L("Zwykły", "Normal")), SegItem(Paper.SEPIA, "Sepia"), SegItem(Paper.NIGHT, L("Nocny", "Night"))),
                st.paper, { st.paper = it; changed() },
            )
        }
        HDivider(Modifier.padding(vertical = 8.dp))
        SectionLabel(L("Czytanie", "Reading"))
        SettingRow(L("Domyślny układ stron", "Default page layout"), L("Dla nowo otwieranych dokumentów", "For newly opened documents")) {
            Seg(
                listOf(SegItem(PageLayout.SINGLE, L("Jedna", "Single")), SegItem(PageLayout.SPREAD, L("Dwie", "Two")), SegItem(PageLayout.BOOK, L("Książka", "Book"))),
                st.layout, { st.layout = it; changed() },
            )
        }
        SettingRow(L("Przewijanie", "Scrolling")) {
            Seg(
                listOf(SegItem(Flow.CONTINUOUS, L("Ciągłe", "Continuous")), SegItem(Flow.PAGED, L("Strona po stronie", "Page by page"))),
                st.flow, { st.flow = it; changed() },
            )
        }
        SettingRow(L("Od prawej do lewej", "Right to left"), L("Kolejność stron w rozkładówce (manga, języki RTL)", "Page order in spreads (manga, RTL languages)")) {
            Switch(st.rtl) { st.rtl = it; changed() }
        }
        SettingRow(L("Płynne przewijanie i animacje", "Smooth scrolling and animations")) {
            Switch(st.smoothScroll) { st.smoothScroll = it; changed() }
        }
        SettingRow(L("Pamiętaj miejsce w dokumencie", "Remember reading position"), L("Otwiera plik na stronie, na której skończono", "Opens a file on the page where you stopped")) {
            Switch(st.rememberPosition) { st.rememberPosition = it; changed() }
        }
        SettingRow(L("Przywracaj otwarte karty", "Restore open tabs"), L("Po ponownym uruchomieniu programu", "When the program starts again")) {
            Switch(st.restoreSession) { st.restoreSession = it; changed() }
        }
        HDivider(Modifier.padding(vertical = 8.dp))
        SectionLabel(L("Adnotacje", "Annotations"))
        SettingRow(L("Autor adnotacji", "Annotation author"), L("Zapisywany w dodawanych adnotacjach", "Stored in the annotations you add")) {
            Input(st.author, { st.author = it; changed() }, Modifier.width(200.dp), onFocus = { app.typing = it })
        }
        HDivider(Modifier.padding(vertical = 8.dp))
        SectionLabel(L("Program", "Program"))
        SettingRow(L("Sprawdzaj aktualizacje", "Check for updates"), L("Automatycznie, w tle", "Automatically, in the background")) {
            Switch(st.checkUpdates) { st.checkUpdates = it; changed() }
        }
        SettingRow(L("Ostatnio otwierane", "Recent files"), L("{0} pozycji na liście", "{0} items in the list").replace("{0}", st.recents.size.toString())) {
            Btn(L("Wyczyść", "Clear"), null, {
                st.recents.toList().filter { !st.isFavorite(it.path) }.forEach { app.removeRecent(it.path) }
            }, enabled = st.recents.isNotEmpty())
        }
    }
}

@Composable
private fun ShortcutsDialog(close: () -> Unit) {
    DialogTitle(L("Skróty klawiszowe", "Keyboard shortcuts"), close)
    val groups = listOf(
        L("Pliki i karty", "Files and tabs") to listOf(
            "Ctrl+O" to L("Otwórz dokumenty", "Open documents"),
            "Ctrl+S · Ctrl+Shift+S" to L("Zapisz, zapisz jako", "Save, save as"),
            "Ctrl+W" to L("Zamknij kartę", "Close tab"),
            "Ctrl+Tab · Ctrl+Shift+Tab" to L("Następna / poprzednia karta", "Next / previous tab"),
            "Ctrl+Shift+1…9" to L("Karta o numerze", "Tab by number"),
            "Ctrl+P · Ctrl+I" to L("Drukuj, właściwości", "Print, properties"),
        ),
        L("Czytanie", "Reading") to listOf(
            "PgDn · PgUp · Spacja" to L("Następna / poprzednia strona", "Next / previous page"),
            "Home · End" to L("Pierwsza / ostatnia strona", "First / last page"),
            "Ctrl+G" to L("Przejdź do strony", "Go to page"),
            "Ctrl+F · F3 · Shift+F3" to L("Szukaj, następny, poprzedni wynik", "Search, next, previous result"),
            "Ctrl+D" to L("Zakładka na bieżącej stronie", "Bookmark the current page"),
            "Ctrl+ +  ·  Ctrl+ −  ·  Ctrl+kółko" to L("Powiększanie", "Zoom"),
            "Ctrl+0 · Ctrl+1 · Ctrl+2" to L("100%, cała strona, szerokość", "100%, fit page, fit width"),
            "1 · 2 · B · C · R" to L("Jedna, dwie strony, książka, przewijanie, od prawej", "Single, two pages, book, scrolling, right to left"),
            "N · Ctrl+R" to L("Papier, obrót widoku", "Paper, rotate view"),
            "Ctrl+B" to L("Panel boczny", "Sidebar"),
            "F11 · F5 · Esc" to L("Pełny ekran, prezentacja, wyjście", "Full screen, presentation, exit"),
        ),
        L("Narzędzia i edycja", "Tools and editing") to listOf(
            "V · H" to L("Zaznaczanie tekstu, rączka", "Select text, hand"),
            "U · P · M" to L("Wyróżnianie tekstu, pióro, zakreślacz", "Text markup, pen, highlighter"),
            "S · T · K · E" to L("Kształty, pole tekstowe, notatka, gumka", "Shapes, text box, note, eraser"),
            "Ctrl+Z · Ctrl+Y" to L("Cofnij, ponów", "Undo, redo"),
            "Ctrl+C · Ctrl+A" to L("Kopiuj zaznaczenie / zaznacz stronę", "Copy selection / select page"),
            "Ctrl+Shift+O" to L("Porządkowanie stron", "Organize pages"),
            "Delete · Ctrl+← →" to L("Usuń / przesuń zaznaczone strony", "Delete / move selected pages"),
        ),
    )
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for ((title, items) in groups) {
            SectionLabel(title)
            for ((k, v) in items) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Txt(v, Modifier.weight(1f), color = P.text2)
                    Kbd(k)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AboutDialog(app: AppState, close: () -> Unit) {
    DialogTitle(L("O programie", "About"), close)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource("icon.png"), null, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(10.dp))
        Txt("OpenDocument", style = Type.title, weight = FontWeight.SemiBold)
        Txt(L("Wersja {0} · Windows", "Version {0} · Windows").replace("{0}", app.version), color = P.text3, style = Type.small)
        Spacer(Modifier.height(10.dp))
        BasicText(
            L("Czytnik i edytor dokumentów: PDF, DjVu, EPUB, FB2, MOBI, DOCX, ODT, RTF, CBZ, Markdown, HTML i obrazy. Adnotacje, formularze, porządkowanie stron, scalanie, hasła i kompresja.",
                "Document reader and editor: PDF, DjVu, EPUB, FB2, MOBI, DOCX, ODT, RTF, CBZ, Markdown, HTML and images. Annotations, forms, page organizing, merging, passwords and compression."),
            style = Type.body.copy(color = P.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        )
        Spacer(Modifier.height(14.dp))
        Txt(L("Projekt i wykonanie", "Design and development"), style = Type.tiny, color = P.text3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Btn("redzikowski.com", null, { browse("https://redzikowski.com") })
            Btn("kontakt@redzikowski.com", null, { browse("mailto:kontakt@redzikowski.com") })
        }
        Spacer(Modifier.height(8.dp))
        Btn("GitHub", null, { browse("https://github.com/BlackSingularities/OpenPDF") }, kind = BtnKind.GHOST)
    }
}

@Composable
private fun PropertiesDialog(app: AppState, close: () -> Unit) {
    val t = app.active ?: return close()
    val i = t.index.info
    DialogTitle(L("Właściwości dokumentu", "Document properties"), close)
    var title by remember(t.id, t.revision) { mutableStateOf(i.title) }
    var author by remember(t.id, t.revision) { mutableStateOf(i.author) }
    var subject by remember(t.id, t.revision) { mutableStateOf(i.subject) }
    var keywords by remember(t.id, t.revision) { mutableStateOf(i.keywords) }
    val f = t.file
    val first = t.renderer?.sizes?.firstOrNull()
    val rows = listOfNotNull(
        L("Plik", "File") to f.name,
        L("Folder", "Folder") to (f.parent ?: ""),
        L("Rozmiar", "Size") to humanBytes(f.length()),
        L("Format", "Format") to t.format.name,
        L("Stron", "Pages") to t.pageCount.toString(),
        first?.let { L("Rozmiar strony", "Page size") to "${(it.width / 72 * 25.4).roundToInt()} × ${(it.height / 72 * 25.4).roundToInt()} mm" },
        i.creator.takeIf { it.isNotBlank() }?.let { L("Program", "Creator") to it },
        i.producer.takeIf { it.isNotBlank() }?.let { L("Producent PDF", "PDF producer") to it },
        i.version.takeIf { it.isNotBlank() && t.format.name == "PDF" }?.let { L("Wersja PDF", "PDF version") to it },
        i.created?.let { L("Utworzono", "Created") to formatDate(it, true) },
        i.modified?.let { L("Zmieniono", "Modified") to formatDate(it, true) },
        t.protection?.let { L("Ochrona", "Protection") to L("Hasło przy zapisie (AES-256)", "Password on save (AES-256)") },
    )
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionLabel(L("Opis", "Description"))
        for ((label, value, set) in listOf(
            Triple(L("Tytuł", "Title"), title) { v: String -> title = v },
            Triple(L("Autor", "Author"), author) { v: String -> author = v },
            Triple(L("Temat", "Subject"), subject) { v: String -> subject = v },
            Triple(L("Słowa kluczowe", "Keywords"), keywords) { v: String -> keywords = v },
        )) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(label, Modifier.width(130.dp), color = P.text3)
                Input(value, set, Modifier.weight(1f), onFocus = { app.typing = it })
            }
        }
        HDivider(Modifier.padding(vertical = 10.dp))
        SectionLabel(L("Plik", "File"))
        for ((k, v) in rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Txt(k, Modifier.width(130.dp), color = P.text3)
                Txt(v, Modifier.weight(1f), maxLines = 3)
            }
        }
    }
    val dirty = title != i.title || author != i.author || subject != i.subject || keywords != i.keywords
    Buttons {
        Btn(L("Anuluj", "Cancel"), null, close, kind = BtnKind.GHOST)
        Btn(L("Zapisz właściwości", "Save properties"), null, {
            t.setInfo(InfoEdit(title, author, subject, keywords))
            close()
        }, kind = BtnKind.PRIMARY, enabled = dirty && t.editable)
    }
}

@Composable
private fun UpdateDialog(app: AppState, close: () -> Unit) {
    DialogTitle(L("Aktualizacja", "Update"), close)
    when (val u = app.update) {
        is UpdateStatus.Available -> {
            Txt(L("Dostępna jest wersja {0} (masz {1}).", "Version {0} is available (you have {1}).").replace("{0}", u.info.version).replace("{1}", app.version))
            if (u.info.notes.isNotBlank()) {
                Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(200.dp).background(P.raised, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    BasicText(u.info.notes, Modifier.verticalScroll(rememberScrollState()), style = Type.small.copy(color = P.text2))
                }
            }
            Buttons {
                Btn(L("Pomiń tę wersję", "Skip this version"), null, { app.settings.skippedVersion = u.info.version; app.saveSoon(); close() }, kind = BtnKind.GHOST)
                Btn(L("Później", "Later"), null, close)
                Btn(L("Pobierz i zainstaluj", "Download and install"), null, { app.installUpdate(u.info) { exitProcess(0) } }, kind = BtnKind.PRIMARY)
            }
        }
        is UpdateStatus.Downloading -> {
            Txt(L("Pobieranie wersji {0}…", "Downloading version {0}…").replace("{0}", u.info.version))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(Pill).background(P.raised2)) {
                Box(Modifier.fillMaxWidth(u.progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(P.accent))
            }
            Spacer(Modifier.height(8.dp))
            Txt(L("Po pobraniu program zamknie się, a instalator zaktualizuje go i uruchomi ponownie.", "When the download finishes the program closes and the installer updates and restarts it."), style = Type.small, color = P.text3, maxLines = 3)
        }
        else -> {
            Txt(L("Masz najnowszą wersję ({0}).", "You have the latest version ({0}).").replace("{0}", app.version))
        }
    }
}

/** Zakres stron w postaci „1-3, 5, 8-10” → indeksy (od zera). */
fun parseRange(s: String, count: Int): List<Int> {
    val out = sortedSetOf<Int>()
    for (part in s.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }) {
        val ab = part.split('-', '–').map { it.trim().toIntOrNull() }
        val a = ab.getOrNull(0) ?: continue
        val b = if (ab.size > 1) ab[1] ?: count else a
        for (i in minOf(a, b)..maxOf(a, b)) if (i in 1..count) out += i - 1
    }
    return out.toList()
}

@Composable
private fun ExportImagesDialog(app: AppState, close: () -> Unit) {
    val t = app.active ?: return close()
    DialogTitle(L("Eksport stron jako obrazy", "Export pages as images"), close)
    var which by remember { mutableStateOf(0) }
    var range by remember { mutableStateOf("1-${t.pageCount}") }
    var dpi by remember { mutableStateOf(150) }
    var png by remember { mutableStateOf(true) }
    val pages = when (which) {
        0 -> (0 until t.pageCount).toList()
        1 -> listOf(t.page)
        else -> parseRange(range, t.pageCount)
    }
    SettingRow(L("Strony", "Pages")) {
        Seg(listOf(SegItem(0, L("Wszystkie", "All")), SegItem(1, L("Bieżąca", "Current")), SegItem(2, L("Zakres", "Range"))), which, { which = it })
    }
    if (which == 2) {
        Input(range, { range = it }, Modifier.fillMaxWidth(), placeholder = "1-3, 5, 8-10", onFocus = { app.typing = it })
    }
    SettingRow(L("Rozdzielczość", "Resolution"), L("Wyższa – ostrzejsze, ale większe pliki", "Higher – sharper but larger files")) {
        Seg(listOf(SegItem(96, "96"), SegItem(150, "150"), SegItem(300, "300"), SegItem(600, "600")), dpi, { dpi = it })
    }
    SettingRow(L("Format", "Format")) {
        Seg(listOf(SegItem(true, "PNG"), SegItem(false, "JPG")), png, { png = it })
    }
    Buttons {
        Txt(L("Stron: {0}", "Pages: {0}").replace("{0}", pages.size.toString()), Modifier.weight(1f), color = P.text3, style = Type.small)
        Btn(L("Anuluj", "Cancel"), null, close, kind = BtnKind.GHOST)
        Btn(L("Wybierz folder i zapisz", "Choose folder and save"), null, {
            close()
            app.exportImages(t, pages, dpi, png)
        }, kind = BtnKind.PRIMARY, enabled = pages.isNotEmpty() && app.work == null)
    }
}

@Composable
private fun CompressDialog(app: AppState, close: () -> Unit) {
    val t = app.active ?: return close()
    DialogTitle(L("Zmniejsz rozmiar", "Reduce size"), close)
    var level by remember { mutableStateOf(1) }
    Txt(
        L("Duże obrazy w dokumencie zostaną zmniejszone i zapisane jako JPEG. Tekst i grafika wektorowa się nie zmieniają. Zmianę można cofnąć (Ctrl+Z).",
            "Large images in the document are downscaled and stored as JPEG. Text and vector graphics stay unchanged. You can undo it (Ctrl+Z)."),
        color = P.text2, maxLines = 4,
    )
    Spacer(Modifier.height(10.dp))
    SettingRow(L("Stopień", "Level")) {
        Seg(listOf(SegItem(0, L("Lekki", "Light")), SegItem(1, L("Średni", "Medium")), SegItem(2, L("Mocny", "Strong"))), level, { level = it })
    }
    Txt(L("Rozmiar pliku teraz: {0}", "Current file size: {0}").replace("{0}", humanBytes((t.pdf ?: t.file).length())), style = Type.small, color = P.text3)
    Buttons {
        Btn(L("Anuluj", "Cancel"), null, close, kind = BtnKind.GHOST)
        Btn(L("Zmniejsz", "Reduce"), null, {
            close()
            when (level) {
                0 -> t.compress(2400, 0.85f)
                1 -> t.compress(1600, 0.72f)
                else -> t.compress(1100, 0.55f)
            }
        }, kind = BtnKind.PRIMARY, enabled = t.editable)
    }
}

@Composable
private fun SecurityDialog(app: AppState, close: () -> Unit) {
    val t = app.active ?: return close()
    DialogTitle(L("Hasło i ochrona", "Password and protection"), close)
    var user by remember { mutableStateOf(t.protection?.user ?: "") }
    var owner by remember { mutableStateOf(t.protection?.owner?.takeIf { it != t.protection?.user } ?: "") }
    Txt(
        L("Hasło jest dodawane przy zapisie (szyfrowanie AES-256). Dokument w programie pozostaje otwarty.",
            "The password is applied on save (AES-256 encryption). The document stays open in the program."),
        color = P.text2, maxLines = 3,
    )
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Input(user, { user = it }, Modifier.fillMaxWidth(), placeholder = L("Hasło do otwarcia", "Password to open"), password = true, onFocus = { app.typing = it })
        Input(owner, { owner = it }, Modifier.fillMaxWidth(), placeholder = L("Hasło właściciela (opcjonalnie)", "Owner password (optional)"), password = true, onFocus = { app.typing = it })
    }
    Buttons {
        if (t.protection != null) Btn(L("Usuń hasło", "Remove password"), null, { t.changeProtection(null); close() }, kind = BtnKind.DANGER)
        Spacer(Modifier.weight(1f))
        Btn(L("Anuluj", "Cancel"), null, close, kind = BtnKind.GHOST)
        Btn(L("Ustaw hasło", "Set password"), null, {
            t.changeProtection(Protection(user, owner.ifEmpty { user }))
            close()
            app.showToast(L("Hasło zostanie dodane przy zapisie", "The password will be applied when you save"))
        }, kind = BtnKind.PRIMARY, enabled = user.isNotEmpty())
    }
}

@Composable
private fun TextDialog(app: AppState, close: () -> Unit) {
    val text = app.extracted ?: return close()
    val name = app.active?.name ?: "tekst"
    DialogTitle(L("Tekst dokumentu", "Document text"), close)
    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp).background(P.surface, RoundedCornerShape(10.dp)).border(1.dp, P.line, RoundedCornerShape(10.dp)).padding(12.dp)) {
        if (text.isBlank()) {
            Txt(L("Dokument nie zawiera tekstu (może to skan – brak warstwy tekstowej).", "The document has no text (it may be a scan without a text layer)."), color = P.text3, maxLines = 3)
        } else {
            androidx.compose.foundation.text.selection.SelectionContainer {
                BasicText(text, Modifier.verticalScroll(rememberScrollState()), style = Type.body.copy(color = P.text))
            }
        }
    }
    Buttons {
        Txt(L("Znaków: {0}", "Characters: {0}").replace("{0}", text.length.toString()), Modifier.weight(1f), color = P.text3, style = Type.small)
        Btn(L("Kopiuj wszystko", "Copy all"), Icons.Rounded.ContentCopy, { copyToClipboard(text); app.showToast(L("Skopiowano tekst", "Text copied")) }, enabled = text.isNotBlank())
        Btn(L("Zapisz jako .txt", "Save as .txt"), Icons.Rounded.Save, { app.saveText(text, name) }, kind = BtnKind.PRIMARY, enabled = text.isNotBlank())
    }
}

@Composable
private fun NewDocDialog(app: AppState, close: () -> Unit) {
    DialogTitle(L("Pusty dokument", "Blank document"), close)
    var pages by remember { mutableStateOf("1") }
    var landscape by remember { mutableStateOf(false) }
    var letter by remember { mutableStateOf(false) }
    SettingRow(L("Format", "Format")) {
        Seg(listOf(SegItem(false, "A4"), SegItem(true, "Letter")), letter, { letter = it })
    }
    SettingRow(L("Orientacja", "Orientation")) {
        Seg(listOf(SegItem(false, L("Pionowo", "Portrait")), SegItem(true, L("Poziomo", "Landscape"))), landscape, { landscape = it })
    }
    SettingRow(L("Liczba stron", "Number of pages")) {
        Input(pages, { v -> pages = v.filter { it.isDigit() }.take(3) }, Modifier.width(80.dp), onFocus = { app.typing = it })
    }
    val n = pages.toIntOrNull()?.coerceIn(1, 500)
    Buttons {
        Btn(L("Anuluj", "Cancel"), null, close, kind = BtnKind.GHOST)
        Btn(L("Utwórz…", "Create…"), null, {
            close()
            app.newBlank(n ?: 1, landscape, letter)
        }, kind = BtnKind.PRIMARY, enabled = n != null)
    }
}

@Composable
private fun UnsavedDialog(app: AppState) {
    val list = app.closing
    DialogTitle(L("Niezapisane zmiany", "Unsaved changes")) { app.resolveClosing(null) }
    Txt(
        if (list.size == 1) L("Dokument „{0}” ma niezapisane zmiany. Zapisać je przed zamknięciem?", "„{0}” has unsaved changes. Save them before closing?").replace("{0}", list[0].name)
        else L("Niezapisane zmiany w dokumentach: {0}. Zapisać je przed zamknięciem?", "Unsaved changes in {0} documents. Save them before closing?").replace("{0}", list.size.toString()),
        color = P.text2, maxLines = 4,
    )
    if (list.size > 1) {
        Spacer(Modifier.height(6.dp))
        for (t in list.take(8)) Txt("• " + t.name, style = Type.small, color = P.text3)
    }
    Buttons {
        Btn(L("Nie zapisuj", "Don't save"), null, { app.resolveClosing(false) }, kind = BtnKind.DANGER)
        Spacer(Modifier.weight(1f))
        Btn(L("Anuluj", "Cancel"), null, { app.resolveClosing(null) }, kind = BtnKind.GHOST)
        Btn(L("Zapisz", "Save"), Icons.Rounded.Save, { app.resolveClosing(true) }, kind = BtnKind.PRIMARY)
    }
}

/** Okno z polem tekstowym (pole tekstowe, notatka, nazwa zakładki). Ctrl+Enter zatwierdza wieloliniowe. */
@Composable
private fun InputDialog(app: AppState, close: () -> Unit) {
    val req = app.input ?: return close()
    var value by remember(req) { mutableStateOf(req.initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(req) {
        delay(40)
        runCatching { focus.requestFocus() }
    }
    fun done() {
        app.input = null
        close()
        req.onDone(value)
    }
    fun cancel() {
        app.input = null
        close()
    }
    DialogTitle(req.title, ::cancel)
    if (req.multiline) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 260.dp)
                .background(P.surface, RoundedCornerShape(10.dp)).border(1.dp, P.accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(10.dp),
        ) {
            if (value.isEmpty()) Txt(req.hint, color = P.text3)
            BasicTextField(
                value, { value = it },
                Modifier.fillMaxWidth().focusRequester(focus)
                    .onFocusChanged { app.typing = it.isFocused }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.isCtrlPressed && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                            done()
                            true
                        } else false
                    },
                textStyle = Type.body.copy(color = P.text),
                cursorBrush = SolidColor(P.accent),
            )
        }
        Txt(L("Ctrl+Enter – zatwierdź", "Ctrl+Enter – confirm"), Modifier.padding(top = 6.dp), style = Type.tiny, color = P.text3)
    } else {
        Input(value, { value = it }, Modifier.fillMaxWidth(), placeholder = req.hint, focus = focus, onFocus = { app.typing = it }, onEnter = { done() }, onEscape = ::cancel)
    }
    Buttons {
        Btn(L("Anuluj", "Cancel"), null, ::cancel, kind = BtnKind.GHOST)
        Btn(req.confirm.ifBlank { L("OK", "OK") }, null, ::done, kind = BtnKind.PRIMARY)
    }
}

fun humanSize(b: Long): String = humanBytes(b)

fun browse(url: String) {
    runCatching { if (url.startsWith("mailto:")) Desktop.getDesktop().mail(URI(url)) else Desktop.getDesktop().browse(URI(url)) }
}
