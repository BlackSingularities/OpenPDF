// Rozpoznawanie formatu i zamiana dokumentów (tekst, obraz, DjVu, komiks) na PDF.

package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.blacksingularities.openpdf.doc.DocFormat
import com.blacksingularities.openpdf.doc.DocumentReader
import com.blacksingularities.openpdf.doc.djvu.DjvuDocument
import com.blacksingularities.openpdf.doc.djvu.writeBmp
import com.blacksingularities.openpdf.doc.imageMime
import com.blacksingularities.openpdf.platform.fileSystem
import com.blacksingularities.openpdf.platform.ioDispatcher
import kotlinx.coroutines.withContext
import com.blacksingularities.openpdf.platform.PdfEngine
import okio.Path
import okio.openZip

internal fun detectFormat(file: Path, name: String): DocFormat {
    val head = fileSystem.read(file) { readByteArray(minOf(4096L, fileSystem.metadataOrNull(file)?.size ?: 0L)) }
    return DocumentReader.detect(name, head, fileSystem, file)
}

/** Dokument tekstowy albo obraz → plik PDF w katalogu sesji. */
internal suspend fun convertToPdf(engine: PdfEngine, dir: Path, file: Path, format: DocFormat, name: String, progress: (Busy) -> Unit): Path {
    val out = dir / "converted.pdf"
    progress(Busy(tr("Składanie stron: {0}", name)))
    if (format == DocFormat.IMAGE) {
        withContext(ioDispatcher) {
            // Dekodery obrazów kierują się rozszerzeniem – plik dostaje właściwe.
            val head = fileSystem.read(file) { readByteArray(minOf(16L, fileSystem.metadataOrNull(file)?.size ?: 0L)) }
            val ext = imageMime(head)?.substringAfter('/') ?: "jpg"
            val img = dir / "image.$ext"
            file.copyTo(img)
            engine.createFromImages(listOf(img), out)
            img.delete()
        }
    } else if (format == DocFormat.DJVU) {
        // Strony DjVu dekodowane po kolei do obrazów i od razu osadzane w PDF.
        val djvu = withContext(ioDispatcher) { DjvuDocument(fileSystem.read(file) { readByteArray() }) }
        val n = djvu.pageCount
        progress(Busy(tr("Dekodowanie DjVu: strona {0} z {1}", 1, n), 0f))
        withContext(ioDispatcher) {
            engine.createFromImageSequence(n, out, { i ->
                runCatching {
                    val raster = djvu.render(i)
                    val img = dir / "page-$i.bmp"
                    fileSystem.write(img) { writeBmp(raster, this) }
                    img
                }.getOrNull()
            }) { p ->
                val done = (p * n).toInt().coerceIn(1, n)
                progress(Busy(tr("Dekodowanie DjVu: strona {0} z {1}", minOf(n, done + 1), n), p))
            }
        }
    } else if (format == DocFormat.COMIC) {
        // Komiks CBZ: obrazy z archiwum wypakowywane po kolei, każdy na osobną stronę.
        val zip = withContext(ioDispatcher) { fileSystem.openZip(file) }
        val pages = withContext(ioDispatcher) { DocumentReader.comicPages(zip) }
        if (pages.isEmpty()) error(tr("Archiwum nie zawiera obrazów"))
        val n = pages.size
        progress(Busy(tr("Składanie stron: {0}", name), 0f))
        withContext(ioDispatcher) {
            engine.createFromImageSequence(n, out, { i ->
                runCatching {
                    val bytes = zip.read(pages[i]) { readByteArray() }
                    val ext = imageMime(bytes)?.substringAfter('/') ?: return@runCatching null
                    val img = dir / "page-$i.$ext"
                    fileSystem.write(img) { write(bytes) }
                    img
                }.getOrNull()
            }) { p -> progress(Busy(tr("Składanie stron: {0}", name), p)) }
        }
    } else {
        val text = withContext(ioDispatcher) { DocumentReader.read(fileSystem, file, format, name) }
        if (text.blocks.isEmpty()) error(tr("Dokument jest pusty"))
        engine.createFromDocument(text, out)
    }
    file.delete()
    return out
}
