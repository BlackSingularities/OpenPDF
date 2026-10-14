package com.blacksingularities.openpdf

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.pdf.AndroidPdfEngine
import com.blacksingularities.openpdf.platform.DocMeta
import com.blacksingularities.openpdf.platform.KeyValueEditor
import com.blacksingularities.openpdf.platform.KeyValueStore
import com.blacksingularities.openpdf.platform.PdfEngine
import com.blacksingularities.openpdf.platform.Platform
import com.blacksingularities.openpdf.ui.DefaultAppRow
import com.blacksingularities.openpdf.update.ReleaseInfo
import com.blacksingularities.openpdf.update.UpdateManager
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

/** Usługi Androida dla wspólnego kodu. */
class AndroidPlatform(private val app: Application) : Platform {
    private val resolver get() = app.contentResolver

    override val engine: PdfEngine = AndroidPdfEngine()
    override val cacheDir: Path get() = app.cacheDir.toOkioPath()
    override val filesDir: Path get() = app.filesDir.toOkioPath()
    override val appVersion: String get() = BuildConfig.VERSION_NAME

    override fun store(name: String): KeyValueStore = PrefsStore(app.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun systemLanguage(): String = app.resources.configuration.locales[0].language

    // ------------------------------------------------------------------ pliki użytkownika

    override fun meta(uri: String): DocMeta {
        val u = Uri.parse(uri)
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val fallback = u.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: tr("Dokument.pdf")
        return DocMeta(name ?: fallback, size)
    }

    override fun copyFromUri(uri: String, out: Path) {
        val input = resolver.openInputStream(Uri.parse(uri)) ?: error(tr("Brak dostępu do pliku"))
        input.use { i -> out.toFile().outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
    }

    override fun checkWritable(uri: String): Boolean {
        val u = Uri.parse(uri)
        if (u.scheme == "file") return u.path?.let { File(it).canWrite() } == true
        val persisted = runCatching {
            resolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            true
        }.getOrDefault(false)
        if (!persisted) runCatching { resolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (persisted) return true
        return runCatching { resolver.openFileDescriptor(u, "rw")?.use { } != null }.getOrDefault(false)
    }

    private fun openOut(uri: Uri) = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
        ?: resolver.openOutputStream(uri, "w") ?: error(tr("Brak dostępu do zapisu"))

    override fun writeToUri(file: Path, uri: String) {
        openOut(Uri.parse(uri)).use { o -> file.toFile().inputStream().use { it.copyTo(o, 256 * 1024) } }
    }

    override fun releaseUri(uri: String) {
        resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // ------------------------------------------------------------------ inne

    override fun copyToClipboard(text: String) {
        val cm = app.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("OpenPDF", text))
    }

    override fun saveImageToGallery(image: ImageBitmap, name: String, png: Boolean) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenPDF")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error(tr("Brak dostępu do galerii"))
        val bmp = image.asAndroidBitmap()
        resolver.openOutputStream(uri)?.use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
    }

    override fun galleryDoneMessage(count: Int) = tr("Zapisano {0} obrazów w Obrazy/OpenPDF", count)

    override val aboutTitle = "OpenPDF – otwarty edytor PDF dla Androida"
    override val aboutLicense = "Licencja MIT · PdfBox-Android (Apache 2.0)"

    override val settingsExtras: (@Composable () -> Unit) = { DefaultAppRow() }

    // ------------------------------------------------------------------ aktualizacje

    override val updateAsset = ".apk"
    override val canSelfUpdate = true

    override fun httpGet(url: String): String = UpdateManager.httpGet(url)

    override suspend fun downloadUpdate(info: ReleaseInfo, onProgress: (Float) -> Unit): Path =
        UpdateManager.download(app, info, onProgress).toOkioPath()

    override fun canInstallUpdates() = UpdateManager.canInstall(app)

    override fun openInstallPermissionSettings() = UpdateManager.openInstallPermissionSettings(app)

    override suspend fun installUpdate(file: Path) {
        UpdateManager.install(app, file.toFile())
    }

    override fun openUrl(url: String) {
        runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private class PrefsStore(private val prefs: android.content.SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getFloat(key: String, default: Float) = prefs.getFloat(key, default)

    override fun edit(block: KeyValueEditor.() -> Unit) {
        val e = prefs.edit()
        object : KeyValueEditor {
            override fun putString(key: String, value: String) {
                e.putString(key, value)
            }

            override fun putBoolean(key: String, value: Boolean) {
                e.putBoolean(key, value)
            }

            override fun putFloat(key: String, value: Float) {
                e.putFloat(key, value)
            }
        }.block()
        e.apply()
    }
}
