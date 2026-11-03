package com.blacksingularities.openpdf.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextMeasurer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import kotlin.math.min

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual val fileSystem: FileSystem get() = FileSystem.SYSTEM

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun applyLocale(english: Boolean) {
    Locale.setDefault(if (english) Locale.ENGLISH else Locale.forLanguageTag("pl-PL"))
}

actual fun formatDate(millis: Long, withTime: Boolean): String {
    val f = if (withTime) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.MEDIUM)
    return f.format(Date(millis))
}

actual fun stripAccent(c: Char): Char = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c

actual fun encodePng(image: ImageBitmap): ByteArray? = runCatching {
    Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
}.getOrNull()

actual fun decodeImageFile(path: Path): ImageBitmap? =
    runCatching { Image.makeFromEncoded(path.toFile().readBytes()).toComposeImageBitmap() }.getOrNull()

private val typeface by lazy { FontMgr.default.matchFamilyStyle("Segoe UI", FontStyle.NORMAL) }

actual fun measurePlainText(text: String, sizePt: Float): Float = Font(typeface, sizePt).measureTextWidth(text)

actual fun DrawScope.drawPlainText(measurer: TextMeasurer, text: String, x: Float, baseline: Float, color: Int, sizePx: Float) {
    val paint = Paint().apply { this.color = color; isAntiAlias = true }
    drawIntoCanvas { it.nativeCanvas.drawString(text, x, baseline, Font(typeface, sizePx), paint) }
}

actual fun maxImageCacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 4).coerceAtMost(512L * 1024 * 1024).toInt()

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun screenSizeDp(): ScreenDp {
    val size = LocalWindowInfo.current.containerSize
    val d = LocalDensity.current.density
    val w = (size.width / d).toInt()
    val h = (size.height / d).toInt()
    return ScreenDp(w, h, min(w, h))
}
