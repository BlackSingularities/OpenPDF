package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blacksingularities.openpdf.desktop.AppState
import com.blacksingularities.openpdf.desktop.DocTab
import com.blacksingularities.openpdf.desktop.InputRequest
import com.blacksingularities.openpdf.desktop.SideTab
import com.blacksingularities.openpdf.pdf.FieldEdit
import com.blacksingularities.openpdf.pdf.FieldKind
import com.blacksingularities.openpdf.pdf.ImageMark
import com.blacksingularities.openpdf.pdf.InkMark
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.MarkupMark
import com.blacksingularities.openpdf.pdf.NoteMark
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.pdf.ShapeMark
import com.blacksingularities.openpdf.pdf.SignatureMark
import com.blacksingularities.openpdf.pdf.TextMark

fun sideLabel(t: SideTab, tab: DocTab): String = when (t) {
    SideTab.THUMBS -> L("Miniatury stron", "Page thumbnails")
    SideTab.OUTLINE -> L("Spis treści", "Contents")
    SideTab.BOOKMARKS -> L("Zakładki", "Bookmarks") + count(tab.bookmarks.size)
    SideTab.SEARCH -> L("Szukaj", "Search") + count(tab.hits.size)
    SideTab.ANNOTATIONS -> L("Adnotacje", "Annotations") + count(tab.index.annotCount + tab.marks.size)
    SideTab.FORM -> L("Formularz", "Form") + count(tab.index.fields.size)
}

private fun count(n: Int) = if (n > 0) " ($n)" else ""

fun sideIcon(t: SideTab): ImageVector = when (t) {
    SideTab.THUMBS -> Icons.Rounded.GridView
    SideTab.OUTLINE -> Icons.Rounded.Toc
    SideTab.BOOKMARKS -> Icons.Rounded.Bookmark
    SideTab.SEARCH -> Icons.Rounded.Search
    SideTab.ANNOTATIONS -> Icons.Rounded.Layers
    SideTab.FORM -> Icons.Rounded.FactCheck
}

/** Wiersz listy panelu: tytuł, podpis, akcje widoczne po najechaniu. */
@Composable
private fun PanelRow(
    title: String,
    subtitle: String?,
    on: Boolean = false,
    dot: Color? = null,
    onClick: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (on) P.accentSoft else if (hovered) P.raised else Color.Transparent)
            .hoverable(src).pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) Box(Modifier.size(10.dp).background(dot, CircleShape))
        Column(Modifier.weight(1f)) {
            Txt(title, color = if (on) P.text else P.text, weight = if (on) FontWeight.SemiBold else null, maxLines = 2)
            if (subtitle != null) Txt(subtitle, style = Type.tiny, color = P.text3, maxLines = 2)
        }
        if (hovered) actions()
    }
}

// ------------------------------------------------------------------ zakładki

@Composable
fun BookmarksPanel(app: AppState, tab: DocTab) {
    val list = tab.bookmarks
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
            Btn(
                if (tab.isBookmarked(tab.page)) L("Usuń zakładkę ze strony {0}", "Remove bookmark from page {0}").replace("{0}", (tab.page + 1).toString())
                else L("Dodaj zakładkę: strona {0}", "Bookmark page {0}").replace("{0}", (tab.page + 1).toString()),
                Icons.Rounded.BookmarkAdd, { tab.toggleBookmark() }, Modifier.fillMaxWidth(), shortcut = "Ctrl+D",
            )
        }
        if (list.isEmpty()) {
            Empty(L("Brak zakładek. Dodaj je przyciskiem powyżej albo skrótem Ctrl+D.", "No bookmarks yet. Add one with the button above or Ctrl+D."))
            return
        }
        LazyColumn(contentPadding = PaddingValues(8.dp, 4.dp, 8.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(list, key = { _, b -> "${b.page}-${b.title}" }) { _, b ->
                PanelRow(b.title, L("Strona {0}", "Page {0}").replace("{0}", (b.page + 1).toString()), on = b.page == tab.page, dot = P.accent, onClick = { tab.goTo(b.page) }) {
                    IconBtn(Icons.Rounded.DriveFileRenameOutline, L("Zmień nazwę", "Rename"), {
                        app.ask(InputRequest(L("Nazwa zakładki", "Bookmark name"), initial = b.title, confirm = L("Zapisz", "Save")) { tab.renameBookmark(b, it) })
                    }, size = 24.dp)
                    IconBtn(Icons.Rounded.Close, L("Usuń", "Remove"), { tab.removeBookmark(b) }, size = 24.dp)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ adnotacje

fun markLabel(m: Mark): String = when (m) {
    is InkMark -> if (m.highlighter) L("Zakreślacz", "Highlighter") else L("Rysunek", "Drawing")
    is ShapeMark -> when (m.kind) {
        ShapeKind.RECT -> L("Prostokąt", "Rectangle")
        ShapeKind.ELLIPSE -> L("Elipsa", "Ellipse")
        ShapeKind.LINE -> L("Linia", "Line")
        ShapeKind.ARROW -> L("Strzałka", "Arrow")
    }
    is TextMark -> L("Tekst: ", "Text: ") + m.text.take(40)
    is NoteMark -> L("Notatka: ", "Note: ") + m.text.take(40)
    is MarkupMark -> when (m.kind) {
        MarkupKind.HIGHLIGHT -> L("Zakreślenie", "Highlight")
        MarkupKind.UNDERLINE -> L("Podkreślenie", "Underline")
        MarkupKind.STRIKEOUT -> L("Przekreślenie", "Strike-out")
    } + ": " + m.text.take(40)
    is ImageMark -> L("Obraz", "Image")
    is SignatureMark -> L("Podpis", "Signature")
}

fun annotLabel(subtype: String): String = when (subtype) {
    "Highlight" -> L("Zakreślenie", "Highlight")
    "Underline" -> L("Podkreślenie", "Underline")
    "StrikeOut" -> L("Przekreślenie", "Strike-out")
    "Squiggly" -> L("Falista linia", "Squiggly")
    "Ink" -> L("Rysunek", "Drawing")
    "FreeText" -> L("Tekst", "Text")
    "Text" -> L("Notatka", "Note")
    "Square" -> L("Prostokąt", "Rectangle")
    "Circle" -> L("Elipsa", "Ellipse")
    "Line" -> L("Linia", "Line")
    "Polygon", "PolyLine" -> L("Wielokąt", "Polygon")
    "Stamp" -> L("Pieczątka", "Stamp")
    "FileAttachment" -> L("Załącznik", "Attachment")
    else -> subtype
}

@Composable
fun AnnotationsPanel(app: AppState, tab: DocTab) {
    val pending = tab.marks
    val inFile = tab.index.annots.flatten()
    var confirmAll by remember(tab.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp, 2.dp, 8.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            SectionLabel(L("Niezapisane ({0})", "Unsaved ({0})").replace("{0}", pending.size.toString()), Modifier.padding(start = 4.dp))
        }
        if (pending.isEmpty()) {
            item {
                Txt(
                    L("Narysuj coś narzędziem z paska (pióro, zakreślacz, tekst…). Elementy trafią do pliku przy zapisie.",
                        "Draw with a tool from the toolbar (pen, highlighter, text…). Items are written to the file when you save."),
                    Modifier.padding(4.dp), style = Type.small, color = P.text3, maxLines = 4,
                )
            }
        } else {
            itemsIndexed(pending, key = { _, m -> "m${m.id}" }) { _, m ->
                PanelRow(markLabel(m), L("Strona {0}", "Page {0}").replace("{0}", (m.page + 1).toString()), dot = Color(m.color or 0xFF000000.toInt()), onClick = { tab.goTo(m.page, m.bounds().top) }) {
                    IconBtn(Icons.Rounded.Close, L("Usuń", "Remove"), { tab.removeMark(m.id) }, size = 24.dp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn(L("Zapisz w dokumencie", "Write to document"), Icons.Rounded.Save, { tab.commitMarks() }, Modifier.weight(1f), kind = BtnKind.PRIMARY, enabled = tab.editable)
                    Btn(L("Odrzuć", "Discard"), null, { tab.clearMarks() }, kind = BtnKind.GHOST)
                }
            }
        }
        item {
            SectionLabel(L("W pliku ({0})", "In the file ({0})").replace("{0}", inFile.size.toString()), Modifier.padding(start = 4.dp, top = 10.dp))
        }
        if (inFile.isEmpty()) {
            item { Txt(L("Brak adnotacji w pliku.", "No annotations in the file."), Modifier.padding(4.dp), style = Type.small, color = P.text3) }
        } else {
            itemsIndexed(inFile, key = { _, a -> "a${a.page}-${a.index}" }) { _, a ->
                val sub = buildString {
                    append(L("Strona {0}", "Page {0}").replace("{0}", (a.page + 1).toString()))
                    if (a.author.isNotBlank()) append(" · ").append(a.author)
                    if (a.contents.isNotBlank()) append(" · ").append(a.contents.take(60).replace('\n', ' '))
                }
                PanelRow(annotLabel(a.subtype), sub, dot = a.color?.let { Color(it) }, onClick = { tab.goTo(a.page, a.rect.top) }) {
                    IconBtn(Icons.Rounded.Close, L("Usuń z pliku", "Delete from file"), { tab.deleteAnnotation(a) }, enabled = tab.editable, size = 24.dp)
                }
            }
            item {
                Btn(
                    if (confirmAll) L("Kliknij ponownie, aby usunąć wszystkie", "Click again to delete all") else L("Usuń wszystkie adnotacje z pliku", "Delete all annotations from the file"),
                    Icons.Rounded.DeleteSweep,
                    {
                        if (confirmAll) {
                            confirmAll = false
                            tab.deleteAllAnnotations()
                        } else {
                            confirmAll = true
                        }
                    },
                    Modifier.fillMaxWidth().padding(top = 8.dp), kind = BtnKind.DANGER, enabled = tab.editable,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ formularz

@Composable
fun FormPanel(app: AppState, tab: DocTab) {
    val fields = tab.index.fields.filter { it.kind != FieldKind.BUTTON }
    if (fields.isEmpty()) {
        Empty(L("Ten dokument nie ma pól formularza.", "This document has no form fields."))
        return
    }
    // Wartości zmienione w panelu (nazwa pola → wartość), do zastosowania jednym przyciskiem.
    val edits = remember(tab.id, tab.revision) { mutableStateMapOf<String, String>() }
    val state = rememberLazyListState()
    LaunchedEffect(app.focusField, tab.revision) {
        val i = fields.indexOfFirst { it.name == app.focusField }
        if (i >= 0) state.animateScrollToItem(i)
    }
    Column(Modifier.fillMaxSize()) {
        Txt(
            L("Pola są podświetlone na stronach – kliknij pole w dokumencie, aby przejść do niego.", "Fields are highlighted on the pages – click one in the document to jump to it."),
            Modifier.padding(horizontal = 12.dp, vertical = 2.dp), style = Type.tiny, color = P.text3, maxLines = 3,
        )
        LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(10.dp, 6.dp, 10.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(fields, key = { _, f -> f.name }) { _, f ->
                val value = edits[f.name] ?: f.value
                val focused = f.name == app.focusField
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (focused) P.accentSoft else P.surface)
                        .clickable { app.focusField = f.name; f.widgets.firstOrNull()?.let { w -> tab.goTo(w.page, w.rect.top) } }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Txt(f.label.ifBlank { f.name }, style = Type.small, weight = FontWeight.SemiBold, maxLines = 2)
                    val enabled = !f.readOnly && tab.editable
                    when (f.kind) {
                        FieldKind.TEXT -> Input(value, { edits[f.name] = it }, Modifier.fillMaxWidth(), placeholder = L("(puste)", "(empty)"), onFocus = { app.typing = it; if (it) app.focusField = f.name })
                        FieldKind.CHECKBOX -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(value == "true") { if (enabled) edits[f.name] = if (it) "true" else "false" }
                            Txt(if (value == "true") L("Zaznaczone", "Checked") else L("Niezaznaczone", "Unchecked"), style = Type.small, color = P.text2)
                        }
                        FieldKind.RADIO, FieldKind.CHOICE -> DropBtn(value.ifBlank { L("(wybierz)", "(choose)") }, null, null, width = 240.dp, enabled = enabled) { close ->
                            f.options.forEachIndexed { i, o ->
                                val export = f.exportOptions.getOrNull(i) ?: o
                                MenuItem(o, checked = if (value == export || value == o) true else null) {
                                    close()
                                    edits[f.name] = export
                                }
                            }
                        }
                        FieldKind.SIGNATURE -> Txt(L("Pole podpisu cyfrowego", "Digital signature field"), style = Type.small, color = P.text3)
                        FieldKind.BUTTON -> Unit
                    }
                    if (f.readOnly) Txt(L("Tylko do odczytu", "Read only"), style = Type.tiny, color = P.text3)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val changed = edits.filter { (k, v) -> fields.firstOrNull { it.name == k }?.value != v }
            Btn(
                if (changed.isEmpty()) L("Brak zmian w polach", "No field changes") else L("Zastosuj zmiany ({0})", "Apply changes ({0})").replace("{0}", changed.size.toString()),
                Icons.Rounded.Save, { tab.fillFields(changed.map { FieldEdit(it.key, it.value) }) }, Modifier.fillMaxWidth(),
                kind = BtnKind.PRIMARY, enabled = changed.isNotEmpty() && tab.editable,
            )
            Btn(L("Spłaszcz formularz", "Flatten the form"), null, { tab.flattenForm() }, Modifier.fillMaxWidth(), kind = BtnKind.GHOST, enabled = tab.editable,
                tip = L("Wartości staną się zwykłą treścią stron – pól nie da się już edytować", "Values become ordinary page content – the fields can no longer be edited"))
        }
    }
}
