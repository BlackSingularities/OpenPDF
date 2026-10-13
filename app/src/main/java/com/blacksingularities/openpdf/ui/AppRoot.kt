package com.blacksingularities.openpdf.ui

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.blacksingularities.openpdf.ui.theme.GlassScope
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Composable
fun AppRoot(vm: PdfViewModel) {
    val context = LocalContext.current
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { vm.onSaveLocation(it) }
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { vm.onSaveLocation(it) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { vm.placeImage(it) }
    LaunchedEffect(vm) {
        vm.uiEvents.collect { e ->
            when (e) {
                is UiEvent.AskSaveLocation -> try {
                    if (e.mime == "text/plain") saveText.launch(e.name) else savePdf.launch(e.name)
                } catch (_: ActivityNotFoundException) {
                    vm.toast("Brak aplikacji do wyboru miejsca zapisu")
                }
                is UiEvent.Share -> share(context, e.file, e.mime)
                is UiEvent.Print -> print(context, e.file, e.name)
                is UiEvent.OpenUri -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    vm.toast("Nie można otworzyć: ${e.uri}")
                }
                UiEvent.PickImageForPlacement -> try {
                    pickImage.launch("image/*")
                } catch (_: ActivityNotFoundException) {
                    vm.toast("Brak aplikacji do wyboru obrazu")
                }
            }
        }
    }
    GlassScope(haptics = vm.settings.haptics) {
        when (vm.screen) {
            Screen.HOME -> HomeScreen(vm)
            Screen.VIEWER -> if (vm.render != null) ViewerScreen(vm) else HomeScreen(vm)
            Screen.PAGES -> if (vm.render != null) PagesScreen(vm) else HomeScreen(vm)
        }
        ViewerSheets(vm)
        ViewModelDialogs(vm)
        BusyOverlay(vm)
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
            MessagePill(vm)
        }
    }
}

private fun share(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun print(context: Context, file: File, name: String) {
    val pm = context.getSystemService(PrintManager::class.java) ?: return
    pm.print(name, FilePrintAdapter(file, name), PrintAttributes.Builder().build())
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
