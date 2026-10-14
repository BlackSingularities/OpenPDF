package com.blacksingularities.openpdf.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextMeasurer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale

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
    val out = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 90, out)
    out.toByteArray()
}.getOrNull()

actual fun decodeImageFile(path: Path): ImageBitmap? =
    runCatching { BitmapFactory.decodeFile(path.toString())?.asImageBitmap() }.getOrNull()

private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }

actual fun measurePlainText(text: String, sizePt: Float): Float {
    measurePaint.textSize = sizePt
    return measurePaint.measureText(text)
}

actual fun DrawScope.drawPlainText(measurer: TextMeasurer, text: String, x: Float, baseline: Float, color: Int, sizePx: Float) {
    drawPaint.color = color
    drawPaint.textSize = sizePx
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, baseline, drawPaint) }
}

actual fun maxImageCacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 5).coerceAtMost(256L * 1024 * 1024).toInt()

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun screenSizeDp(): ScreenDp {
    val c = LocalConfiguration.current
    return ScreenDp(c.screenWidthDp, c.screenHeightDp, c.smallestScreenWidthDp)
}
