package com.blacksingularities.openpdf.ios

import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.platform.PlatformUi
import com.blacksingularities.openpdf.platform.nowMillis
import com.blacksingularities.openpdf.ui.PdfViewModel
import kotlinx.cinterop.useContents
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Okna systemu iOS (wybór plików i zdjęć, zapis, udostępnianie, druk) dla wspólnego interfejsu. */
class IosPlatformUi(private val vm: PdfViewModel) : PlatformUi {
    private val fs = FileSystem.SYSTEM

    /** Delegat okna wyboru – UIKit trzyma go słabo, więc referencja musi żyć tutaj. */
    private var keep: NSObject? = null

    private fun top(): UIViewController? {
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }

    private fun present(vc: UIViewController) {
        val host = top() ?: return
        vc.popoverPresentationController?.let { pop ->
            // iPad: okno jako dymek na środku ekranu.
            pop.sourceView = host.view
            val (w, h) = host.view.bounds.useContents { size.width to size.height }
            pop.sourceRect = CGRectMake(w / 2, h / 2, 1.0, 1.0)
        }
        host.presentViewController(vc, animated = true, completion = null)
    }

    // ------------------------------------------------------------------ pliki PDF

    private fun pickPdfs(multiple: Boolean, done: (List<String>) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = false)
        picker.allowsMultipleSelection = multiple
        val d = DocDelegate { urls ->
            keep = null
            done(urls.map { bookmarkOf(it) })
        }
        keep = d
        picker.delegate = d
        present(picker)
    }

    override fun openPdf() = pickPdfs(false) { it.firstOrNull()?.let { u -> vm.open(u) } }

    override fun mergePdfs() = pickPdfs(true) { if (it.isNotEmpty()) vm.mergeFiles(it) }

    override fun insertPdf(at: Int) = pickPdfs(false) { it.firstOrNull()?.let { u -> vm.insertPdf(u, at) } }

    // ------------------------------------------------------------------ zdjęcia

    private fun pickImages(limit: Int, done: (List<String>) -> Unit) {
        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter
        config.selectionLimit = limit.toLong()
        val picker = PHPickerViewController(configuration = config)
        val d = PhotoDelegate { results ->
            keep = null
            loadImages(results, done)
        }
        keep = d
        picker.delegate = d
        present(picker)
    }

    /** Kopiuje wybrane zdjęcia do katalogu tymczasowego (po kolei), wynik – w wątku głównym. */
    private fun loadImages(results: List<PHPickerResult>, done: (List<String>) -> Unit) {
        val dir = vm.platform.cacheDir / "picked"
        runCatching { fs.createDirectories(dir) }
        val out = ArrayList<String>()
        fun next(i: Int) {
            if (i >= results.size) {
                dispatch_async(dispatch_get_main_queue()) { done(out) }
                return
            }
            results[i].itemProvider.loadFileRepresentationForTypeIdentifier("public.image") { url, _ ->
                val path = url?.path
                if (path != null) {
                    runCatching {
                        val ext = url.pathExtension?.takeIf { it.isNotEmpty() } ?: "img"
                        val target = dir / "pick-${nowMillis()}-$i.$ext"
                        fs.copy(path.toPath(), target)
                        out += target.toString()
                    }
                }
                next(i + 1)
            }
        }
        next(0)
    }

    override fun createFromImages() = pickImages(0) { if (it.isNotEmpty()) vm.createFromImages(it) }

    override fun insertImages(at: Int) = pickImages(0) { if (it.isNotEmpty()) vm.insertImages(it, at) }

    override fun pickImageForPlacement() = pickImages(1) { vm.placeImage(it.firstOrNull()) }

    // ------------------------------------------------------------------ zapis, udostępnianie, druk

    override fun saveAs(name: String, mime: String) {
        // Okno eksportu przenosi pusty plik w wybrane miejsce; treść zapisuje potem model widoku.
        val dir = vm.platform.cacheDir / "export"
        val f: Path = dir / name.replace('/', '_')
        try {
            fs.deleteRecursively(dir)
            fs.createDirectories(dir)
            fs.write(f) {}
        } catch (e: Exception) {
            vm.toast(tr("Zapis nie powiódł się: {0}", e.message))
            return
        }
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(f.toString())), asCopy = false)
        val d = DocDelegate { urls ->
            keep = null
            vm.onSaveLocation(urls.firstOrNull()?.let { bookmarkOf(it) })
        }
        keep = d
        picker.delegate = d
        present(picker)
    }

    override fun share(file: Path, mime: String) {
        val vc = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(file.toString())), applicationActivities = null)
        present(vc)
    }

    override fun print(file: Path, name: String) {
        val pc = UIPrintInteractionController.sharedPrintController
        pc.printingItem = NSURL.fileURLWithPath(file.toString())
        pc.presentAnimated(true, completionHandler = null)
    }

    override fun openUri(uri: String) = vm.platform.openUrl(uri)
}

private class DocDelegate(private val done: (List<NSURL>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        done(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        done(emptyList())
    }
}

private class PhotoDelegate(private val done: (List<PHPickerResult>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        done(didFinishPicking.filterIsInstance<PHPickerResult>())
    }
}
