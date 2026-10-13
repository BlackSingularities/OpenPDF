package com.blacksingularities.openpdf.pdf

/** Istniejąca w pliku adnotacja (do listy, usuwania, podglądu treści notatek). */
data class AnnotInfo(
    val page: Int,
    /** Pozycja na liście /Annots strony – do usuwania. */
    val index: Int,
    val subtype: String,
    val rect: RectN,
    val contents: String,
    val author: String,
    val color: Int?,
)

/** Odnośnik: do strony w dokumencie albo adres URI. */
data class LinkInfo(val rect: RectN, val targetPage: Int?, val uri: String?)

data class OutlineEntry(val title: String, val page: Int?, val depth: Int)

enum class FieldKind { TEXT, CHECKBOX, RADIO, CHOICE, SIGNATURE, BUTTON }

data class FieldWidget(val page: Int, val rect: RectN)

data class FieldInfo(
    val name: String,
    val label: String,
    val kind: FieldKind,
    val value: String,
    val options: List<String>,
    val exportOptions: List<String>,
    val readOnly: Boolean,
    val multiline: Boolean,
    val widgets: List<FieldWidget>,
)

data class DocInfo(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val version: String = "",
    val created: Long? = null,
    val modified: Long? = null,
)

/** Wszystko, co interfejs wie o dokumencie poza samym obrazem stron. */
data class DocIndex(
    val geos: List<PageGeo>,
    val annots: List<List<AnnotInfo>>,
    val links: List<List<LinkInfo>>,
    val outline: List<OutlineEntry>,
    val fields: List<FieldInfo>,
    val info: DocInfo,
) {
    val pageCount get() = geos.size
    val annotCount get() = annots.sumOf { it.size }

    companion object {
        val Empty = DocIndex(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), DocInfo())
    }
}

/** Metadane do edycji we właściwościach. */
data class InfoEdit(val title: String, val author: String, val subject: String, val keywords: String)

/** Zmiana wartości pola formularza. */
data class FieldEdit(val name: String, val value: String)
