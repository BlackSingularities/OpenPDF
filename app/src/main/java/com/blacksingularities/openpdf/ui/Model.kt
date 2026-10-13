package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import android.net.Uri
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.blacksingularities.openpdf.pdf.FieldInfo
import com.blacksingularities.openpdf.pdf.Mark
import com.blacksingularities.openpdf.pdf.MarkupKind
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.RectN
import com.blacksingularities.openpdf.pdf.ShapeKind
import com.blacksingularities.openpdf.pdf.ShapeMark
import java.io.File

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

/** Otwarty dokument: plik roboczy w katalogu sesji i miejsce, z którego pochodzi. */
class OpenDoc(
    val uri: Uri?,
    val name: String,
    val dir: File,
    val source: File,
    val writable: Boolean,
)

data class Busy(val label: String, val progress: Float? = null)

data class PasswordRequest(val uri: Uri?, val file: File?, val name: String, val wrong: Boolean, val purpose: PasswordPurpose)

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

    data class FileCopy(val file: File, val mime: String) : SaveTarget

    data class Text(val text: String) : SaveTarget
}

sealed interface UiEvent {
    data class AskSaveLocation(val name: String, val mime: String, val target: SaveTarget) : UiEvent
    data class Share(val file: File, val mime: String) : UiEvent
    data class Print(val file: File, val name: String) : UiEvent
    data class OpenUri(val uri: String) : UiEvent
    data object PickImageForPlacement : UiEvent
}

/** Zmiana do cofnięcia: tylko elementy oczekujące albo cały plik (migawka) z elementami. */
sealed interface Change {
    data class Marks(val before: List<Mark>) : Change
    data class FileState(val snapshot: File, val marks: List<Mark>, val label: String) : Change
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
