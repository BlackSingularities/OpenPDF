package com.blacksingularities.openpdf.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.blacksingularities.openpdf.i18n.I18n
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.FileSystem
import okio.Path
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.*
import platform.UIKit.*
import kotlin.math.min

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

actual val fileSystem: FileSystem get() = FileSystem.SYSTEM

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun applyLocale(english: Boolean) {}

actual fun formatDate(millis: Long, withTime: Boolean): String {
    val f = NSDateFormatter()
    f.dateStyle = NSDateFormatterMediumStyle
    f.timeStyle = if (withTime) NSDateFormatterShortStyle else NSDateFormatterNoStyle
    f.locale = NSLocale(localeIdentifier = if (I18n.english) "en_US" else "pl_PL")
    return f.stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0))
}

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun stripAccent(c: Char): Char = (c.toString() as NSString).decomposedStringWithCanonicalMapping.firstOrNull() ?: c

actual fun encodePng(image: ImageBitmap): ByteArray? = runCatching {
    Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
}.getOrNull()

actual fun decodeImageFile(path: Path): ImageBitmap? = runCatching {
    val bytes = FileSystem.SYSTEM.read(path) { readByteArray() }
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun measurePlainText(text: String, sizePt: Float): Float {
    val font = UIFont.systemFontOfSize(sizePt.toDouble())
    return (text as NSString).sizeWithAttributes(mapOf<Any?, Any?>(NSFontAttributeName to font)).useContents { width.toFloat() }
}

actual fun DrawScope.drawPlainText(measurer: TextMeasurer, text: String, x: Float, baseline: Float, color: Int, sizePx: Float) {
    if (text.isEmpty()) return
    val layout = measurer.measure(text, TextStyle(color = Color(color), fontSize = sizePx.toSp()), softWrap = false, maxLines = 1)
    drawText(layout, topLeft = Offset(x, baseline - layout.firstBaseline))
}

actual fun maxImageCacheBytes(): Int =
    (NSProcessInfo.processInfo.physicalMemory / 10u).coerceAtMost(256uL * 1024u * 1024u).toInt()

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS nie ma przycisku „wstecz” – nawigacja odbywa się przyciskami w interfejsie.
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenSizeDp(): ScreenDp {
    val size = LocalWindowInfo.current.containerSize
    val d = LocalDensity.current.density
    val w = (size.width / d).toInt()
    val h = (size.height / d).toInt()
    return ScreenDp(w, h, min(w, h))
}
