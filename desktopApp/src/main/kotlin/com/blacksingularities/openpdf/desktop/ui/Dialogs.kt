package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.Dialog
import com.blacksingularities.openpdf.desktop.Flow
import com.blacksingularities.openpdf.desktop.PageLayout
import com.blacksingularities.openpdf.desktop.Paper
import com.blacksingularities.openpdf.desktop.UpdateStatus
import com.blacksingularities.openpdf.i18n.AppLanguage
import com.blacksingularities.openpdf.platform.formatDate
import java.awt.Desktop
import java.net.URI
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@Composable
fun Dialogs(app: AppState) {
    val close = { app.dialog = Dialog.NONE }
    when (app.dialog) {
        Dialog.NONE -> Unit
        Dialog.SETTINGS -> Modal(close, 520.dp) { SettingsDialog(app, close) }
        Dialog.SHORTCUTS -> Modal(close, 560.dp) { ShortcutsDialog(close) }
        Dialog.ABOUT -> Modal(close, 420.dp) { AboutDialog(app, close) }
        Dialog.PROPERTIES -> Modal(close, 520.dp) { PropertiesDialog(app, close) }
        Dialog.UPDATE -> Modal(close, 480.dp) { UpdateDialog(app, close) }
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
private fun SettingsDialog(app: AppState, close: () -> Unit) {
    val st = app.settings
    fun changed() = app.saveSoon()
    DialogTitle(L("Ustawienia", "Settings"), close)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionLabel(L("Wygląd", "Appearance"))
        SettingRow(L("Język", "Language")) {
            Seg(
                listOf(SegItem(AppLanguage.SYSTEM, L("System", "System")), SegItem(AppLanguage.PL, "Polski"), SegItem(AppLanguage.EN, "English")),
                st.language, { app.setLanguage(it) },
            )
        }
        SettingRow(L("Motyw", "Theme")) {
            Seg(
                listOf(SegItem(ThemeId.DARK, L("Ciemny", "Dark")), SegItem(ThemeId.GRAY, L("Szary", "Gray")), SegItem(ThemeId.LIGHT, L("Jasny", "Light"))),
                st.theme, { st.theme = it; changed() },
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
        SectionLabel(L("Program", "Program"))
        SettingRow(L("Sprawdzaj aktualizacje", "Check for updates"), L("Automatycznie, w tle", "Automatically, in the background")) {
            Switch(st.checkUpdates) { st.checkUpdates = it; changed() }
        }
        SettingRow(L("Ostatnio otwierane", "Recent files"), L("{0} pozycji na liście", "{0} items in the list").replace("{0}", st.recents.size.toString())) {
            Btn(L("Wyczyść", "Clear"), null, {
                st.recents.toList().forEach { app.removeRecent(it.path) }
            }, enabled = st.recents.isNotEmpty())
        }
    }
}

@Composable
private fun ShortcutsDialog(close: () -> Unit) {
    DialogTitle(L("Skróty klawiszowe", "Keyboard shortcuts"), close)
    val items = listOf(
        "Ctrl+O" to L("Otwórz dokumenty", "Open documents"),
        "Ctrl+W" to L("Zamknij kartę", "Close tab"),
        "Ctrl+Tab · Ctrl+Shift+Tab" to L("Następna / poprzednia karta", "Next / previous tab"),
        "Ctrl+Shift+1…9" to L("Karta o numerze", "Tab by number"),
        "PgDn · PgUp · Spacja" to L("Następna / poprzednia strona", "Next / previous page"),
        "Home · End" to L("Pierwsza / ostatnia strona", "First / last page"),
        "← → ↑ ↓" to L("Przewijanie, przewracanie stron", "Scroll, turn pages"),
        "Ctrl+G" to L("Przejdź do strony", "Go to page"),
        "Ctrl+F · F3 · Shift+F3" to L("Szukaj, następny, poprzedni wynik", "Search, next, previous result"),
        "Ctrl+ +  ·  Ctrl+ −" to L("Powiększ / pomniejsz", "Zoom in / out"),
        "Ctrl+kółko" to L("Powiększanie myszą", "Zoom with mouse"),
        "Ctrl+0 · Ctrl+1 · Ctrl+2" to L("100%, cała strona, szerokość", "100%, fit page, fit width"),
        "1 · 2 · B" to L("Jedna strona, dwie strony, książka", "Single, two pages, book"),
        "C" to L("Ciągłe / strona po stronie", "Continuous / page by page"),
        "R" to L("Od prawej do lewej", "Right to left"),
        "N" to L("Papier: zwykły, sepia, nocny", "Paper: normal, sepia, night"),
        "Ctrl+R" to L("Obróć widok", "Rotate view"),
        "V · H" to L("Zaznaczanie tekstu / rączka", "Select text / hand"),
        "Ctrl+C · Ctrl+A" to L("Kopiuj zaznaczenie / zaznacz stronę", "Copy selection / select page"),
        "Ctrl+B" to L("Panel boczny", "Sidebar"),
        "F11 · F5 · Esc" to L("Pełny ekran, prezentacja, wyjście", "Full screen, presentation, exit"),
        "Ctrl+P · Ctrl+D" to L("Drukuj, właściwości", "Print, properties"),
    )
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for ((k, v) in items) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(v, Modifier.weight(1f), color = P.text2)
                Kbd(k)
            }
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
            L("Przeglądarka dokumentów: PDF, DjVu, EPUB, FB2, MOBI, DOCX, ODT, RTF, CBZ, Markdown, HTML i obrazy.",
                "Document viewer: PDF, DjVu, EPUB, FB2, MOBI, DOCX, ODT, RTF, CBZ, Markdown, HTML and images."),
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
    val f = t.file
    val sizes = t.renderer?.sizes
    val first = sizes?.firstOrNull()
    val rows = listOfNotNull(
        L("Plik", "File") to f.name,
        L("Folder", "Folder") to (f.parent ?: ""),
        L("Rozmiar", "Size") to humanSize(f.length()),
        L("Format", "Format") to t.format.name,
        L("Stron", "Pages") to t.pageCount.toString(),
        first?.let { L("Rozmiar strony", "Page size") to "${(it.width / 72 * 25.4).roundToInt()} × ${(it.height / 72 * 25.4).roundToInt()} mm" },
        i.title.takeIf { it.isNotBlank() }?.let { L("Tytuł", "Title") to it },
        i.author.takeIf { it.isNotBlank() }?.let { L("Autor", "Author") to it },
        i.subject.takeIf { it.isNotBlank() }?.let { L("Temat", "Subject") to it },
        i.keywords.takeIf { it.isNotBlank() }?.let { L("Słowa kluczowe", "Keywords") to it },
        i.creator.takeIf { it.isNotBlank() }?.let { L("Program", "Creator") to it },
        i.producer.takeIf { it.isNotBlank() }?.let { L("Producent PDF", "PDF producer") to it },
        i.version.takeIf { it.isNotBlank() && t.format.name == "PDF" }?.let { L("Wersja PDF", "PDF version") to it },
        i.created?.let { L("Utworzono", "Created") to formatDate(it, true) },
        i.modified?.let { L("Zmieniono", "Modified") to formatDate(it, true) },
    )
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for ((k, v) in rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Txt(k, Modifier.width(140.dp), color = P.text3)
                Txt(v, Modifier.weight(1f), maxLines = 3)
            }
        }
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
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Btn(L("Pomiń tę wersję", "Skip this version"), null, { app.settings.skippedVersion = u.info.version; app.saveSoon(); close() }, kind = BtnKind.GHOST)
                Btn(L("Później", "Later"), null, close)
                Btn(L("Pobierz i zainstaluj", "Download and install"), null, { app.installUpdate(u.info) { exitProcess(0) } }, kind = BtnKind.PRIMARY)
            }
        }
        is UpdateStatus.Downloading -> {
            Txt(L("Pobieranie wersji {0}…", "Downloading version {0}…").replace("{0}", u.info.version))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(Pill).background(P.raised2)) {
                Box(Modifier.fillMaxWidth(u.progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(P.sel))
            }
            Spacer(Modifier.height(8.dp))
            Txt(L("Po pobraniu program zamknie się, a instalator zaktualizuje go i uruchomi ponownie.", "When the download finishes the program closes and the installer updates and restarts it."), style = Type.small, color = P.text3, maxLines = 3)
        }
        else -> {
            Txt(L("Masz najnowszą wersję ({0}).", "You have the latest version ({0}).").replace("{0}", app.version))
        }
    }
}

fun humanSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.1f MB".format(b / (1024.0 * 1024))
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

fun browse(url: String) {
    runCatching { if (url.startsWith("mailto:")) Desktop.getDesktop().mail(URI(url)) else Desktop.getDesktop().browse(URI(url)) }
}
