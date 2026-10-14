package com.blacksingularities.openpdf.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.blacksingularities.openpdf.i18n.I18n
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import okio.Path
import kotlin.math.abs

/** Wątki do operacji na plikach. */
expect val ioDispatcher: CoroutineDispatcher

/** System plików urządzenia. */
expect val fileSystem: FileSystem

expect fun nowMillis(): Long

/** Domyślne ustawienia regionalne procesu (formaty dat i liczb). */
expect fun applyLocale(english: Boolean)

/** Data (opcjonalnie z godziną) w formacie bieżącego języka. */
expect fun formatDate(millis: Long, withTime: Boolean): String

/** Litera bez znaków diakrytycznych (rozkład NFD, pierwszy znak). */
expect fun stripAccent(c: Char): Char

expect fun encodePng(image: ImageBitmap): ByteArray?

expect fun decodeImageFile(path: Path): ImageBitmap?

/** Szerokość wiersza tekstu czcionką systemową w punktach. */
expect fun measurePlainText(text: String, sizePt: Float): Float

/** Wiersz tekstu czcionką systemową od linii bazowej [baseline]. */
expect fun DrawScope.drawPlainText(measurer: TextMeasurer, text: String, x: Float, baseline: Float, color: Int, sizePx: Float)

/** Pamięć na obrazy stron (w bajtach). */
expect fun maxImageCacheBytes(): Int

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Rozmiar okna w dp. */
data class ScreenDp(val width: Int, val height: Int, val smallest: Int)

@Composable
expect fun screenSizeDp(): ScreenDp

/** Liczba z [digits] miejscami po przecinku, z separatorem bieżącego języka. */
fun formatDecimal(value: Double, digits: Int): String {
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val scaled = (abs(value) * factor + 0.5).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    if (digits == 0) return "$sign$whole"
    val frac = (scaled % factor).toString().padStart(digits, '0')
    val sep = if (I18n.english) '.' else ','
    return "$sign$whole$sep$frac"
}

fun formatDecimal(value: Float, digits: Int): String = formatDecimal(value.toDouble(), digits)

/** Pamięć podręczna z usuwaniem najdawniej używanych pozycji; [sizeOf] – „waga” pozycji. */
class LruCache<K : Any, V : Any>(private val maxSize: Int, private val sizeOf: (K, V) -> Int = { _, _ -> 1 }) {
    private val map = LinkedHashMap<K, V>()
    private var size = 0

    fun get(key: K): V? {
        val v = map.remove(key) ?: return null
        map[key] = v
        return v
    }

    fun put(key: K, value: V) {
        map.remove(key)?.let { size -= sizeOf(key, it) }
        map[key] = value
        size += sizeOf(key, value)
        trim()
    }

    fun evictAll() {
        map.clear()
        size = 0
    }

    private fun trim() {
        while (size > maxSize && map.isNotEmpty()) {
            val k = map.keys.first()
            val v = map.remove(k) ?: break
            size -= sizeOf(k, v)
        }
    }
}
