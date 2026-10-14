package com.blacksingularities.openpdf.ios

import androidx.compose.ui.graphics.ImageBitmap
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.platform.DocMeta
import com.blacksingularities.openpdf.platform.KeyValueEditor
import com.blacksingularities.openpdf.platform.KeyValueStore
import com.blacksingularities.openpdf.platform.PdfEngine
import com.blacksingularities.openpdf.platform.Platform
import com.blacksingularities.openpdf.platform.encodePng
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.asSkiaBitmap
import platform.Foundation.*
import platform.UIKit.*
import platform.posix.memcpy

/** Prefiks adresu dokumentu zapisanego jako zakładka dostępu (plik spoza piaskownicy aplikacji). */
private const val BOOKMARK = "bm:"

internal fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

/** Adres pliku wskazanego przez użytkownika jako trwała zakładka (dostęp także po ponownym uruchomieniu). */
internal fun bookmarkOf(url: NSURL): String {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        val data = url.bookmarkDataWithOptions(0u, includingResourceValuesForKeys = null, relativeToURL = null, error = null)
        return if (data != null) BOOKMARK + data.base64EncodedStringWithOptions(0u) else url.absoluteString ?: ""
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

/** Plik wskazany adresem; [block] działa z dostępem do zasobu spoza piaskownicy. */
internal fun <T> withUrl(uri: String, block: (NSURL) -> T): T {
    val url: NSURL = if (uri.startsWith(BOOKMARK)) {
        val data = NSData.create(base64EncodedString = uri.removePrefix(BOOKMARK), options = 0u) ?: error(tr("Brak dostępu do pliku"))
        NSURL.URLByResolvingBookmarkData(data, options = 0u, relativeToURL = null, bookmarkDataIsStale = null, error = null) ?: error(tr("Brak dostępu do pliku"))
    } else if (uri.startsWith("/")) {
        NSURL.fileURLWithPath(uri)
    } else {
        NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri)
    }
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        return block(url)
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

class IosPlatform : Platform {
    private val fs = FileSystem.SYSTEM

    override val engine: PdfEngine = IosPdfEngine()

    private fun dir(kind: NSSearchPathDirectory): Path {
        val p = (NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String ?: NSTemporaryDirectory()).toPath()
        fs.createDirectories(p)
        return p
    }

    override val cacheDir: Path by lazy { dir(NSCachesDirectory) }
    override val filesDir: Path by lazy { dir(NSApplicationSupportDirectory) }

    override val appVersion: String
        get() {
            val info = NSBundle.mainBundle.infoDictionary
            return info?.get("OpenPDFVersion") as? String ?: info?.get("CFBundleShortVersionString") as? String ?: "1.0"
        }

    override fun store(name: String): KeyValueStore = DefaultsStore("$name.")

    override fun systemLanguage(): String =
        (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-')?.lowercase() ?: "en"

    // ------------------------------------------------------------------ pliki użytkownika

    override fun meta(uri: String): DocMeta = withUrl(uri) { url ->
        val name = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: tr("Dokument.pdf")
        val size = url.path?.let { fs.metadataOrNull(it.toPath())?.size } ?: -1L
        DocMeta(name, size)
    }

    override fun copyFromUri(uri: String, out: Path): Unit = withUrl(uri) { url ->
        // Koordynacja odczytu pobiera plik z iCloud, gdy nie ma go jeszcze na urządzeniu.
        var failure: Throwable? = null
        NSFileCoordinator(filePresenter = null).coordinateReadingItemAtURL(url, options = 0u, error = null) { readUrl ->
            failure = runCatching {
                val path = (readUrl ?: url).path ?: error(tr("Brak dostępu do pliku"))
                fs.copy(path.toPath(), out)
            }.exceptionOrNull()
        }
        failure?.let { throw it }
        if (fs.metadataOrNull(out) == null) error(tr("Brak dostępu do pliku"))
    }

    override fun checkWritable(uri: String): Boolean = withUrl(uri) { url ->
        val path = url.path ?: return@withUrl false
        NSFileManager.defaultManager.isWritableFileAtPath(path)
    }

    override fun writeToUri(file: Path, uri: String): Unit = withUrl(uri) { url ->
        var failure: Throwable? = null
        NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(url, options = 0u, error = null) { writeUrl ->
            failure = runCatching {
                val path = (writeUrl ?: url).path ?: error(tr("Brak dostępu do zapisu"))
                fs.copy(file, path.toPath())
            }.exceptionOrNull()
        }
        failure?.let { throw it }
    }

    // ------------------------------------------------------------------ inne

    override fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun saveImageToGallery(image: ImageBitmap, name: String, png: Boolean) {
        val bytes = if (png) {
            encodePng(image)
        } else {
            Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, 92)?.bytes
        } ?: error(tr("Brak dostępu do galerii"))
        val img = UIImage(data = bytes.toNSData())
        UIImageWriteToSavedPhotosAlbum(img, null, null, null)
    }

    override fun galleryDoneMessage(count: Int) = tr("Zapisano {0} obrazów w Zdjęciach", count)

    override val hasVolumeKeys = false

    override val aboutTitle = "OpenPDF – otwarty edytor PDF dla iPhone’a i iPada"
    override val aboutLicense = "Licencja MIT · Apple PDFKit"

    // ------------------------------------------------------------------ aktualizacje

    override val updateAsset = ".ipa"
    override val canSelfUpdate = false

    override fun httpGet(url: String): String {
        val u = NSURL.URLWithString(url) ?: error(url)
        val data = NSData.create(contentsOfURL = u) ?: error(tr("Brak połączenia"))
        return data.toByteArray().decodeToString()
    }

    override fun openUrl(url: String) {
        val u = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(u, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

/** Ustawienia w NSUserDefaults z prefiksem nazwy magazynu. */
private class DefaultsStore(private val prefix: String) : KeyValueStore {
    private val d = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = d.stringForKey(prefix + key)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (d.objectForKey(prefix + key) == null) default else d.boolForKey(prefix + key)

    override fun getFloat(key: String, default: Float): Float =
        if (d.objectForKey(prefix + key) == null) default else d.floatForKey(prefix + key)

    override fun edit(block: KeyValueEditor.() -> Unit) {
        object : KeyValueEditor {
            override fun putString(key: String, value: String) = d.setObject(value, forKey = prefix + key)
            override fun putBoolean(key: String, value: Boolean) = d.setBool(value, forKey = prefix + key)
            override fun putFloat(key: String, value: Float) = d.setFloat(value, forKey = prefix + key)
        }.block()
    }
}
