package com.blacksingularities.openpdf

import com.blacksingularities.openpdf.data.AppSettings
import com.blacksingularities.openpdf.data.pageCrops
import com.blacksingularities.openpdf.ui.DocLayout
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CropTest {
    private val s = AppSettings(
        cropLeft = 0.01f, cropTop = 0.02f, cropRight = 0.03f, cropBottom = 0.04f,
        cropSpread = true,
        spreadCropLeft = 0.1f, spreadCropTop = 0.05f, spreadCropRight = 0.2f, spreadCropBottom = 0.06f,
    )
    private val single = floatArrayOf(0.01f, 0.02f, 0.03f, 0.04f)
    private val left = floatArrayOf(0.1f, 0.05f, 0f, 0.06f)
    private val right = floatArrayOf(0f, 0.05f, 0.2f, 0.06f)

    private fun crops(st: AppSettings, n: Int, book: Boolean, coverAlone: Boolean = true, rtl: Boolean = false) =
        st.pageCrops(DocLayout.group(n, book, coverAlone, rtl), n, book)

    @Test
    fun singlePagesUsePageCrop() {
        crops(s, 4, book = false).forEach { assertArrayEquals(single, it, 1e-6f) }
    }

    @Test
    fun spreadCropsOnlyOuterEdges() {
        // Okładka osobno po prawej stronie grzbietu, potem 1–2 (lewa, prawa), na końcu 3 po lewej.
        val c = crops(s, 4, book = true)
        assertArrayEquals(right, c[0], 1e-6f)
        assertArrayEquals(left, c[1], 1e-6f)
        assertArrayEquals(right, c[2], 1e-6f)
        assertArrayEquals(left, c[3], 1e-6f)
    }

    @Test
    fun spreadRtlSwapsSides() {
        val c = crops(s, 3, book = true, coverAlone = false, rtl = true)
        assertArrayEquals(right, c[0], 1e-6f)
        assertArrayEquals(left, c[1], 1e-6f)
    }

    @Test
    fun spreadModeOffUsesPageCrop() {
        crops(s.copy(cropSpread = false), 4, book = true).forEach { assertArrayEquals(single, it, 1e-6f) }
    }
}
