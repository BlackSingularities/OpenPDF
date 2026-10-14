package com.blacksingularities.openpdf.data

import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.platform.KeyValueStore
import com.blacksingularities.openpdf.platform.fileSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okio.Path

/** Ostatnio otwarty dokument. */
data class RecentDoc(
    val uri: String,
    val name: String,
    val size: Long,
    val opened: Long,
    val pages: Int,
    val lastPage: Int,
    val writable: Boolean,
) {
    val key: String get() = keyOf(uri)

    companion object {
        fun keyOf(uri: String): String = uri.hashCode().toUInt().toString(16) + "_" + uri.length
    }
}

/** Zakładka użytkownika na stronie dokumentu. */
data class UserBookmark(val page: Int, val title: String)

/** Zapisany podpis: pociągnięcia w układzie 0..1 i proporcje (szerokość / wysokość). */
data class SavedSignature(val id: String, val strokes: List<List<PointN>>, val aspect: Float)

/** Lista ostatnich plików, zakładki i pozycje czytania, zapisane podpisy. */
class Library(private val prefs: KeyValueStore, filesDir: Path) {
    val thumbDir: Path = (filesDir / "thumbs").also { runCatching { fileSystem.createDirectories(it) } }
    private val signatureFile = filesDir / "signatures.json"

    private fun array(text: String?): JsonArray =
        runCatching { Json.parseToJsonElement(text ?: "[]").jsonArray }.getOrDefault(JsonArray(emptyList()))

    private fun JsonObject.str(k: String) = (get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
    private fun JsonObject.long(k: String) = (get(k) as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.int(k: String) = (get(k) as? JsonPrimitive)?.intOrNull ?: 0
    private fun JsonObject.bool(k: String) = (get(k) as? JsonPrimitive)?.booleanOrNull ?: false

    fun recents(): List<RecentDoc> = array(prefs.getString("recents")).mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        RecentDoc(
            uri = o.str("uri"),
            name = o.str("name"),
            size = o.long("size"),
            opened = o.long("opened"),
            pages = o.int("pages"),
            lastPage = o.int("lastPage"),
            writable = o.bool("writable"),
        )
    }.filter { it.uri.isNotEmpty() }

    fun touch(doc: RecentDoc) {
        val list = listOf(doc) + recents().filter { it.uri != doc.uri }
        saveRecents(list.take(60))
    }

    fun updateLastPage(uri: String, page: Int) {
        val list = recents().map { if (it.uri == uri) it.copy(lastPage = page) else it }
        saveRecents(list)
    }

    fun remove(uri: String) {
        saveRecents(recents().filter { it.uri != uri })
        runCatching { fileSystem.delete(thumbFile(uri)) }
    }

    fun clearRecents() {
        saveRecents(emptyList())
        runCatching { fileSystem.list(thumbDir).forEach { fileSystem.delete(it) } }
    }

    fun thumbFile(uri: String): Path = thumbDir / (RecentDoc.keyOf(uri) + ".png")

    private fun saveRecents(list: List<RecentDoc>) {
        val arr = buildJsonArray {
            list.forEach { r ->
                add(
                    buildJsonObject {
                        put("uri", r.uri)
                        put("name", r.name)
                        put("size", r.size)
                        put("opened", r.opened)
                        put("pages", r.pages)
                        put("lastPage", r.lastPage)
                        put("writable", r.writable)
                    },
                )
            }
        }
        prefs.edit { putString("recents", arr.toString()) }
    }

    // ------------------------------------------------------------------ zakładki

    fun bookmarks(uri: String): List<UserBookmark> =
        array(prefs.getString("bm_" + RecentDoc.keyOf(uri))).mapNotNull { e ->
            (e as? JsonObject)?.let { UserBookmark(it.int("page"), it.str("title")) }
        }.sortedBy { it.page }

    fun setBookmarks(uri: String, list: List<UserBookmark>) {
        val arr = buildJsonArray {
            list.sortedBy { it.page }.forEach { b ->
                add(
                    buildJsonObject {
                        put("page", b.page)
                        put("title", b.title)
                    },
                )
            }
        }
        prefs.edit { putString("bm_" + RecentDoc.keyOf(uri), arr.toString()) }
    }

    // ------------------------------------------------------------------ podpisy

    fun signatures(): List<SavedSignature> {
        val text = runCatching { fileSystem.read(signatureFile) { readUtf8() } }.getOrNull() ?: return emptyList()
        return array(text).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val strokesArr = o["strokes"] as? JsonArray ?: return@mapNotNull null
            val strokes = strokesArr.map { s ->
                val pts = (s as? JsonArray).orEmpty().map { it.jsonPrimitive.doubleOrNull ?: 0.0 }
                (0 until pts.size / 2).map { k -> PointN(pts[k * 2].toFloat(), pts[k * 2 + 1].toFloat()) }
            }
            val aspect = (o["aspect"] as? JsonPrimitive)?.doubleOrNull ?: 3.0
            SavedSignature(o.str("id"), strokes, aspect.toFloat())
        }
    }

    fun saveSignatures(list: List<SavedSignature>) {
        val arr = buildJsonArray {
            list.forEach { sig ->
                add(
                    buildJsonObject {
                        put("id", sig.id)
                        put("aspect", sig.aspect.toDouble())
                        put(
                            "strokes",
                            buildJsonArray {
                                sig.strokes.forEach { s ->
                                    add(
                                        buildJsonArray {
                                            s.forEach { p ->
                                                add(JsonPrimitive(p.x.toDouble()))
                                                add(JsonPrimitive(p.y.toDouble()))
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
        fileSystem.write(signatureFile) { writeUtf8(arr.toString()) }
    }
}
