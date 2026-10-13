package com.blacksingularities.openpdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class OpenPdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Czcionki i mapy kodowań PdfBox ładowane z zasobów biblioteki.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
