package com.blacksingularities.openpdf.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.pdf.PdfOps
import com.blacksingularities.openpdf.ui.components.ActionRow
import com.blacksingularities.openpdf.ui.components.DarkDialog
import com.blacksingularities.openpdf.ui.components.PillButton
import com.blacksingularities.openpdf.ui.theme.Palette
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
        val f = File(dir, "OpenDocument.pdf")
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
        PdfHandler.Us -> tr("OpenDocument otwiera pliki PDF domyślnie")
        PdfHandler.None -> tr("Brak domyślnej aplikacji – wybierz OpenDocument i „Zawsze”")
        is PdfHandler.Other -> tr("Teraz domyślna: {0}", h.label)
    }
}

/** Ustawienie OpenDocument jako domyślnej aplikacji do plików PDF (przez okno wyboru systemu). */
@Composable
fun DefaultAppRow() {
    val ctx = LocalContext.current
    var handler by remember { mutableStateOf(DefaultApp.current(ctx)) }
    var explain by remember { mutableStateOf<PdfHandler.Other?>(null) }
    LifecycleResumeEffect(Unit) {
        handler = DefaultApp.current(ctx)
        onPauseOrDispose { }
    }
    val h = handler
    ActionRow(
        Icons.Rounded.PictureAsPdf,
        if (h == PdfHandler.Us) tr("OpenDocument jest domyślną aplikacją PDF") else tr("Ustaw jako domyślną aplikację PDF"),
        DefaultApp.describe(h),
        enabled = h != PdfHandler.Us,
    ) {
        when (h) {
            PdfHandler.None -> DefaultApp.askSystem(ctx)
            is PdfHandler.Other -> explain = h
            PdfHandler.Us -> Unit
        }
    }
    explain?.let { other ->
        DarkDialog(
            tr("Zmiana domyślnej aplikacji"),
            onDismiss = { explain = null },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { explain = null }
                PillButton(tr("Otwórz ustawienia")) {
                    explain = null
                    DefaultApp.openSettingsOf(ctx, other.pkg)
                }
            },
        ) {
            Text(
                tr("Pliki PDF otwiera teraz „{0}”. Android nie pozwala innej aplikacji tego zmienić – w ustawieniach „{0}” wybierz „Otwieraj domyślnie” → „Wyczyść ustawienia domyślne”, wróć tutaj i dotknij ponownie: pojawi się wybór, w którym zaznacz OpenDocument i „Zawsze”.", other.label),
                color = Palette.TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
