package com.blacksingularities.openpdf.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.blacksingularities.openpdf.update.ReleaseInfo
import okio.Path

/**
 * Usługi systemu, z których korzysta wspólny kod. Adres dokumentu ([String] „uri”) jest
 * nieprzezroczysty: na Androidzie to content:// albo file://, na iOS – zakładka dostępu do pliku.
 */
interface Platform {
    val engine: PdfEngine
    val cacheDir: Path
    val filesDir: Path
    val appVersion: String

    /** Trwałe ustawienia o nazwie [name] (na Androidzie – SharedPreferences o tej nazwie). */
    fun store(name: String): KeyValueStore

    /** Kod języka systemu, np. „pl”. */
    fun systemLanguage(): String

    // ------------------------------------------------------------------ pliki użytkownika

    fun meta(uri: String): DocMeta

    /** Kopiuje zawartość dokumentu do pliku [out]. */
    fun copyFromUri(uri: String, out: Path)

    fun checkWritable(uri: String): Boolean

    /** Zapisuje plik [file] pod adresem [uri] (zastępując zawartość). */
    fun writeToUri(file: Path, uri: String)

    fun releaseUri(uri: String) {}

    // ------------------------------------------------------------------ inne

    fun copyToClipboard(text: String)

    /** Zapis obrazu strony do galerii; [name] – nazwa pliku z rozszerzeniem. */
    fun saveImageToGallery(image: ImageBitmap, name: String, png: Boolean)

    /** Komunikat po zapisaniu [count] obrazów w galerii. */
    fun galleryDoneMessage(count: Int): String

    /** Czy działają klawisze głośności (przewracanie stron). */
    val hasVolumeKeys: Boolean get() = true

    /** Teksty „O aplikacji” (polskie klucze tłumaczeń). */
    val aboutTitle: String
    val aboutLicense: String

    /** Dodatkowe wiersze w ustawieniach (np. wybór domyślnej aplikacji na Androidzie). */
    val settingsExtras: (@Composable () -> Unit)? get() = null

    // ------------------------------------------------------------------ aktualizacje

    /** Końcówka nazwy pliku w wydaniu na GitHubie (.apk / .ipa). */
    val updateAsset: String

    /** Czy aplikacja sama pobiera i instaluje nową wersję (inaczej – otwiera stronę wydania). */
    val canSelfUpdate: Boolean

    /** Pobranie tekstu z sieci (zapytanie blokujące – wywoływane w tle). */
    fun httpGet(url: String): String

    suspend fun downloadUpdate(info: ReleaseInfo, onProgress: (Float) -> Unit): Path =
        error("Aktualizacja niedostępna")

    fun canInstallUpdates(): Boolean = false

    fun openInstallPermissionSettings() {}

    suspend fun installUpdate(file: Path) {}

    fun openUrl(url: String)
}

data class DocMeta(val name: String, val size: Long)

/** Proste trwałe ustawienia klucz–wartość. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getFloat(key: String, default: Float): Float

    /** Zmiany zapisywane razem. */
    fun edit(block: KeyValueEditor.() -> Unit)
}

interface KeyValueEditor {
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun putFloat(key: String, value: Float)
}

/**
 * Okna systemu wywoływane z interfejsu: wybór plików, miejsce zapisu, udostępnianie, druk.
 * Wynik trafia prosto do modelu widoku.
 */
interface PlatformUi {
    fun openPdf()
    fun createFromImages()
    fun mergePdfs()
    fun insertPdf(at: Int)
    fun insertImages(at: Int)
    fun pickImageForPlacement()
    fun saveAs(name: String, mime: String)
    fun share(file: Path, mime: String)
    fun print(file: Path, name: String)
    fun openUri(uri: String)
}

val LocalPlatformUi = staticCompositionLocalOf<PlatformUi> { error("Brak PlatformUi") }
