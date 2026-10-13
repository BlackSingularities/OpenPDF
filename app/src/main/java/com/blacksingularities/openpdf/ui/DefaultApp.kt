package com.blacksingularities.openpdf.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.pdf.PdfOps
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File

/** Kto otwiera pliki PDF: ta aplikacja, inna (nazwa i pakiet) albo nikt (system pyta za każdym razem). */
sealed interface PdfHandler {
    data object Us : PdfHandler
    data object None : PdfHandler
    data class Other(val label: String, val pkg: String) : PdfHandler
}

/**
 * Android nie pozwala aplikacji samej ustawić się jako domyślnej dla plików – może jednak
 * otworzyć okno wyboru („Zawsze”) albo ustawienia aplikacji, która jest domyślna teraz.
 */
object DefaultApp {
    private fun viewIntent(uri: Uri) = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")

    fun current(context: Context): PdfHandler {
        val pm = context.packageManager
        val info = pm.resolveActivity(viewIntent(Uri.parse("content://x/sample.pdf")), PackageManager.MATCH_DEFAULT_ONLY) ?: return PdfHandler.None
        val pkg = info.activityInfo?.packageName ?: return PdfHandler.None
        if (pkg == context.packageName) return PdfHandler.Us
        // Gdy nie ma domyślnej, system zwraca własne okno wyboru (pakiet „android” lub resolver).
        if (pkg == "android" || info.activityInfo.name.contains("Resolver", ignoreCase = true)) return PdfHandler.None
        return PdfHandler.Other(info.loadLabel(pm).toString(), pkg)
    }

    /** Otwiera mały PDF przez system – pojawia się okno wyboru z opcją „Zawsze”. */
    fun askSystem(context: Context) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "OpenPDF.pdf")
        if (!f.exists()) runCatching { PdfOps.createBlank(f, 1, PDRectangle.A4) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val intent = viewIntent(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Ustawienia aplikacji, która jest teraz domyślna – tam „Wyczyść domyślne” / „Otwieraj domyślnie”. */
    fun openSettingsOf(context: Context, pkg: String) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    fun describe(h: PdfHandler): String = when (h) {
        PdfHandler.Us -> tr("OpenPDF otwiera pliki PDF domyślnie")
        PdfHandler.None -> tr("Brak domyślnej aplikacji – wybierz OpenPDF i „Zawsze”")
        is PdfHandler.Other -> tr("Teraz domyślna: {0}", h.label)
    }
}
