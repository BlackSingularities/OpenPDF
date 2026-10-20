package com.blacksingularities.openpdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.platform.LocalPlatformUi
import com.blacksingularities.openpdf.platform.PlatformUi
import com.blacksingularities.openpdf.ui.PdfViewModel
import okio.Path
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Okna systemu Androida (wybór plików, zapis, udostępnianie, druk) dla wspólnego interfejsu. */
@Composable
fun ProvideAndroidPlatformUi(vm: PdfViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val ui = remember(vm) { AndroidPlatformUi(context, vm) }
    ui.openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.open(uri.toString())
    }
    ui.imagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.createFromImages(uris.map { it.toString() })
    }
    ui.mergeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.mergeFiles(uris.map { it.toString() })
    }
    ui.insertPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.insertPdf(uri.toString(), ui.insertAt)
    }
    ui.insertImagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.insertImages(uris.map { it.toString() }, ui.insertAt)
    }
    ui.placeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { vm.placeImage(it?.toString()) }
    ui.savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { vm.onSaveLocation(it?.toString()) }
    ui.saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { vm.onSaveLocation(it?.toString()) }
    CompositionLocalProvider(LocalPlatformUi provides ui, content = content)
}

private class AndroidPlatformUi(private val context: Context, private val vm: PdfViewModel) : PlatformUi {
    lateinit var openLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>
    lateinit var imagesLauncher: ManagedActivityResultLauncher<String, List<Uri>>
    lateinit var mergeLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>
    lateinit var insertPdfPicker: ManagedActivityResultLauncher<Array<String>, Uri?>
    lateinit var insertImagesPicker: ManagedActivityResultLauncher<String, List<Uri>>
    lateinit var placeLauncher: ManagedActivityResultLauncher<String, Uri?>
    lateinit var savePdf: ManagedActivityResultLauncher<String, Uri?>
    lateinit var saveText: ManagedActivityResultLauncher<String, Uri?>

    /** Pozycja wstawiania stron z pliku lub obrazów (ostatnie wywołanie). */
    var insertAt = 0

    private inline fun launch(what: String, block: () -> Unit) {
        try {
            block()
        } catch (_: ActivityNotFoundException) {
            vm.toast(what)
        }
    }

    /** PDF, książki (EPUB, FB2), dokumenty (DOCX, ODT, RTF), tekst, HTML, Markdown i obrazy. */
    override fun openPdf() = launch(tr("Brak aplikacji do wyboru pliku")) { openLauncher.launch(DOCUMENT_TYPES) }

    override fun createFromImages() = launch(tr("Brak aplikacji do wyboru obrazu")) { imagesLauncher.launch("image/*") }

    override fun mergePdfs() = launch(tr("Brak aplikacji do wyboru pliku")) { mergeLauncher.launch(arrayOf("application/pdf")) }

    override fun insertPdf(at: Int) {
        insertAt = at
        launch(tr("Brak aplikacji do wyboru pliku")) { insertPdfPicker.launch(arrayOf("application/pdf")) }
    }

    override fun insertImages(at: Int) {
        insertAt = at
        launch(tr("Brak aplikacji do wyboru obrazu")) { insertImagesPicker.launch("image/*") }
    }

    override fun pickImageForPlacement() = launch(tr("Brak aplikacji do wyboru obrazu")) { placeLauncher.launch("image/*") }

    override fun saveAs(name: String, mime: String) = launch(tr("Brak aplikacji do wyboru miejsca zapisu")) {
        if (mime == "text/plain") saveText.launch(name) else savePdf.launch(name)
    }

    override fun share(file: Path, mime: String) {
        val f = file.toFile()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun print(file: Path, name: String) {
        val pm = context.getSystemService(PrintManager::class.java) ?: return
        pm.print(name, FilePrintAdapter(file.toFile(), name), PrintAttributes.Builder().build())
    }

    override fun openUri(uri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            vm.toast(tr("Nie można otworzyć: {0}", uri))
        }
    }
}

/** Drukowanie gotowego pliku PDF – usługa druku dostaje plik bez przetwarzania. */
private class FilePrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            FileInputStream(file).use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

private val DOCUMENT_TYPES = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "image/vnd.djvu",
    "image/x-djvu",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.oasis.opendocument.text",
    "application/x-fictionbook+xml",
    "application/x-fictionbook",
    "application/rtf",
    "text/*",
    "application/xhtml+xml",
    "application/json",
    "application/xml",
    "application/zip",
    "image/*",
    // Część menedżerów plików nie zna typów EPUB/FB2/MD i podaje je jako „dowolny plik”.
    "application/octet-stream",
)
