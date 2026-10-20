package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.blacksingularities.openpdf.pdf.FieldInfo
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.RectN
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.pdf.ShapeMark
import okio.Path

enum class Screen { HOME, VIEWER, PAGES }

enum class Tool(private val pl: String) {
    VIEW("Przeglądanie"),
    SELECT("Zaznaczanie i przesuwanie"),
    PEN("Pióro"),
    HIGHLIGHTER("Zakreślacz"),
    ERASER("Gumka"),
    TEXT("Tekst"),
    NOTE("Notatka"),
    SHAPE("Kształty"),
    MARKUP("Zaznaczanie tekstu"),
    SIGNATURE("Podpis"),
    IMAGE("Obraz"),
    ;

    val label: String get() = tr(pl)

    /** Narzędzia rysujące jednym palcem (przewijanie – dwoma). */
    val drags: Boolean get() = this == PEN || this == HIGHLIGHTER || this == ERASER || this == SHAPE || this == MARKUP
}

data class ToolStyle(val color: Int, val width: Float, val fill: Boolean = false)

enum class Sheet { OUTLINE, ANNOTATIONS, FORM, INFO, SECURITY, COMPRESS, EXPORT, MORE, SETTINGS, TEXT, SIGNATURES }

/**
 * Otwarty dokument: plik roboczy w katalogu sesji i miejsce, z którego pochodzi. [converted] –
 * dokument tekstowy (EPUB, DOCX…) złożony w strony PDF; zapis tworzy wtedy nowy plik PDF.
 */
class OpenDoc(
    val uri: String?,
    val name: String,
    val dir: Path,
    val source: Path,
    val writable: Boolean,
    val converted: Boolean = false,
)

private val DOC_EXTENSIONS = setOf(
    "pdf", "epub", "fb2", "docx", "odt", "rtf", "txt", "text", "md", "markdown", "html", "htm", "xhtml", "csv", "tsv",
    "log", "json", "xml", "jpg", "jpeg", "png", "webp", "gif", "bmp", "zip",
)

/** Nazwa pliku bez rozszerzenia dokumentu („Książka.epub” → „Książka”). */
fun baseName(name: String): String {
    var n = name
    // „ksiazka.fb2.zip” – dwa rozszerzenia.
    repeat(2) {
        val ext = n.substringAfterLast('.', "")
        if (ext.isNotEmpty() && ext.lowercase() in DOC_EXTENSIONS && n.length > ext.length + 1) n = n.dropLast(ext.length + 1)
    }
    return n
}

/** Nazwa pliku PDF dla dokumentu („Raport.docx” → „Raport.pdf”). */
fun pdfFileName(name: String): String = if (name.endsWith(".pdf", ignoreCase = true)) name else baseName(name) + ".pdf"

data class Busy(val label: String, val progress: Float? = null)

data class PasswordRequest(val uri: String?, val file: Path?, val name: String, val wrong: Boolean, val purpose: PasswordPurpose)

enum class PasswordPurpose { OPEN, INSERT }

data class JumpRequest(val page: Int, val region: RectN?, val token: Long)

data class StepRequest(val direction: Int, val token: Long)

class LiveStroke(val page: Int, val points: SnapshotStateList<PointN>, val pageWidthPt: Float)

data class TextSelection(val page: Int, val anchor: Int, val focus: Int, val rects: List<RectN>, val text: String) {
    val range: IntRange get() = minOf(anchor, focus)..maxOf(anchor, focus)
}

/** Prośba o tekst: nowe pole tekstowe / notatka albo edycja istniejącej. */
data class TextRequest(val tool: Tool, val page: Int, val at: PointN, val editId: Long?, val initial: String)

/** Podgląd notatki z pliku (adnotacja tekstowa). */
data class NoteView(val title: String, val text: String)

data class Protection(val user: String, val owner: String)

sealed interface SaveTarget {
    /** Bieżący dokument („Zapisz jako”). */
    data object Document : SaveTarget

    data class FileCopy(val file: Path, val mime: String) : SaveTarget

    data class Text(val text: String) : SaveTarget
}

sealed interface UiEvent {
    data class AskSaveLocation(val name: String, val mime: String, val target: SaveTarget) : UiEvent
    data class Share(val file: Path, val mime: String) : UiEvent
    data class Print(val file: Path, val name: String) : UiEvent
    data class OpenUri(val uri: String) : UiEvent
    data object PickImageForPlacement : UiEvent
}

/** Zmiana do cofnięcia: tylko elementy oczekujące albo cały plik (migawka) z elementami. */
sealed interface Change {
    data class Marks(val before: List<Mark>) : Change
    data class FileState(val snapshot: Path, val marks: List<Mark>, val label: String) : Change
}

data class FieldDialog(val field: FieldInfo)

/** Kształt rysowany na żywo. */
fun makeShape(id: Long, page: Int, kind: ShapeKind, a: PointN, b: PointN, style: ToolStyle, pageWidthPt: Float) =
    ShapeMark(id, page, kind, a, b, style.color, style.width, if (style.fill) (style.color and 0x00FFFFFF) or (0x40 shl 24) else null, pageWidthPt)

val MarkupKind.label: String
    get() = when (this) {
        MarkupKind.HIGHLIGHT -> tr("Zakreślenie")
        MarkupKind.UNDERLINE -> tr("Podkreślenie")
        MarkupKind.STRIKEOUT -> tr("Przekreślenie")
    }

val ShapeKind.label: String
    get() = when (this) {
        ShapeKind.RECT -> tr("Prostokąt")
        ShapeKind.ELLIPSE -> tr("Elipsa")
        ShapeKind.LINE -> tr("Linia")
        ShapeKind.ARROW -> tr("Strzałka")
    }
