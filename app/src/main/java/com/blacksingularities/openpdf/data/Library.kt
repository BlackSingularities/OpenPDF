package com.blacksingularities.openpdf.data

import android.content.Context
import com.blacksingularities.openpdf.pdf.PointN
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        fun keyOf(uri: String): String = Integer.toHexString(uri.hashCode()) + "_" + uri.length
    }
}

/** Zakładka użytkownika na stronie dokumentu. */
data class UserBookmark(val page: Int, val title: String)

/** Zapisany podpis: pociągnięcia w układzie 0..1 i proporcje (szerokość / wysokość). */
data class SavedSignature(val id: String, val strokes: List<List<PointN>>, val aspect: Float)

/** Lista ostatnich plików, zakładki i pozycje czytania, zapisane podpisy. */
class Library(private val context: Context) {
    private val prefs = context.getSharedPreferences("openpdf_library", Context.MODE_PRIVATE)
    val thumbDir: File = File(context.filesDir, "thumbs").apply { mkdirs() }
    private val signatureFile = File(context.filesDir, "signatures.json")

    fun recents(): List<RecentDoc> {
        val arr = runCatching { JSONArray(prefs.getString("recents", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            RecentDoc(
                uri = o.optString("uri"),
                name = o.optString("name"),
                size = o.optLong("size"),
                opened = o.optLong("opened"),
                pages = o.optInt("pages"),
                lastPage = o.optInt("lastPage"),
                writable = o.optBoolean("writable"),
            )
        }.filter { it.uri.isNotEmpty() }
    }

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
        File(thumbDir, RecentDoc.keyOf(uri) + ".png").delete()
    }

    fun clearRecents() {
        saveRecents(emptyList())
        thumbDir.listFiles()?.forEach { it.delete() }
    }

    fun thumbFile(uri: String) = File(thumbDir, RecentDoc.keyOf(uri) + ".png")

    private fun saveRecents(list: List<RecentDoc>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("uri", r.uri)
                    .put("name", r.name)
                    .put("size", r.size)
                    .put("opened", r.opened)
                    .put("pages", r.pages)
                    .put("lastPage", r.lastPage)
                    .put("writable", r.writable),
            )
        }
        prefs.edit().putString("recents", arr.toString()).apply()
    }

    // ------------------------------------------------------------------ zakładki

    fun bookmarks(uri: String): List<UserBookmark> {
        val arr = runCatching { JSONArray(prefs.getString("bm_" + RecentDoc.keyOf(uri), "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { UserBookmark(it.optInt("page"), it.optString("title")) }
        }.sortedBy { it.page }
    }

    fun setBookmarks(uri: String, list: List<UserBookmark>) {
        val arr = JSONArray()
        list.sortedBy { it.page }.forEach { arr.put(JSONObject().put("page", it.page).put("title", it.title)) }
        prefs.edit().putString("bm_" + RecentDoc.keyOf(uri), arr.toString()).apply()
    }

    // ------------------------------------------------------------------ podpisy

    fun signatures(): List<SavedSignature> {
        val text = runCatching { signatureFile.readText() }.getOrNull() ?: return emptyList()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val strokesArr = o.optJSONArray("strokes") ?: return@mapNotNull null
            val strokes = (0 until strokesArr.length()).map { s ->
                val pts = strokesArr.getJSONArray(s)
                (0 until pts.length() / 2).map { k -> PointN(pts.getDouble(k * 2).toFloat(), pts.getDouble(k * 2 + 1).toFloat()) }
            }
            SavedSignature(o.optString("id"), strokes, o.optDouble("aspect", 3.0).toFloat())
        }
    }

    fun saveSignatures(list: List<SavedSignature>) {
        val arr = JSONArray()
        list.forEach { sig ->
            val strokes = JSONArray()
            sig.strokes.forEach { s ->
                val pts = JSONArray()
                s.forEach { p ->
                    pts.put(p.x.toDouble())
                    pts.put(p.y.toDouble())
                }
                strokes.put(pts)
            }
            arr.put(JSONObject().put("id", sig.id).put("aspect", sig.aspect.toDouble()).put("strokes", strokes))
        }
        signatureFile.writeText(arr.toString())
    }
}
