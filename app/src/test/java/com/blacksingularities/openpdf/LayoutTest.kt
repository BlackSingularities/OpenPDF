package com.blacksingularities.openpdf

import com.blacksingularities.openpdf.pdf.PageGeo
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.ui.DocLayout
import com.blacksingularities.openpdf.ui.FitMode
import com.blacksingularities.openpdf.ui.LayoutSpec
import com.blacksingularities.openpdf.ui.ScrollMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutTest {
    @Test
    fun singlePagesEachInOwnRow() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), DocLayout.group(3, book = false, coverAlone = true, rtl = false))
    }

    @Test
    fun bookWithCoverAloneStartsWithCoverOnTheRight() {
        val g = DocLayout.group(6, book = true, coverAlone = true, rtl = false)
        assertEquals(listOf(listOf(null, 0), listOf(1, 2), listOf(3, 4), listOf(5, null)), g)
    }

    @Test
    fun bookWithoutCoverPairsFromFirstPage() {
        val g = DocLayout.group(5, book = true, coverAlone = false, rtl = false)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4, null)), g)
    }

    @Test
    fun rightToLeftReversesSpreads() {
        val g = DocLayout.group(3, book = true, coverAlone = true, rtl = true)
        assertEquals(listOf(listOf(0, null), listOf(2, 1)), g)
    }

    @Test
    fun spreadFitsPageInLandscape() {
        val spec = LayoutSpec(book = true, coverAlone = false, rtl = false, fit = FitMode.PAGE, scroll = ScrollMode.VERTICAL, margin = 0f, gap = 0f)
        val layout = DocLayout.build(listOf(0.7f, 0.7f), 2000f, 1000f, spec)
        val row = layout.rows.single()
        assertEquals(1000f, row.rect.height, 0.01f)
        assertEquals(1400f, row.rect.width, 0.01f)
        assertEquals(1000f, row.spine!!, 0.01f)
        assertEquals(300f, row.rect.left, 0.01f)
    }

    @Test
    fun coverSitsRightOfSpine() {
        val spec = LayoutSpec(book = true, coverAlone = true, rtl = false, fit = FitMode.PAGE, scroll = ScrollMode.VERTICAL, margin = 0f, gap = 0f)
        val layout = DocLayout.build(listOf(0.5f, 0.5f, 0.5f), 1000f, 1000f, spec)
        val cover = layout.slotOf(0)!!
        assertEquals(500f, cover.rect.left, 0.01f)
        assertEquals(1, layout.rows[1].slots.first().page)
    }

    @Test
    fun visibleRowsBinarySearch() {
        val spec = LayoutSpec(book = false, coverAlone = false, rtl = false, fit = FitMode.WIDTH, scroll = ScrollMode.VERTICAL, margin = 0f, gap = 10f)
        val layout = DocLayout.build(List(100) { 1f }, 100f, 300f, spec)
        val rows = layout.rowsBetween(505f, 700f)
        assertEquals(listOf(4, 5, 6), rows.map { it.index })
    }

    @Test
    fun geometryRoundTripForAllRotations() {
        for (rot in listOf(0, 90, 180, 270)) {
            val g = PageGeo(10f, 20f, 600f, 800f, rot)
            val p = PointN(0.25f, 0.6f)
            val (x, y) = g.toPdf(p)
            val back = g.fromPdf(x, y)
            assertEquals(p.x, back.x, 1e-4f)
            assertEquals(p.y, back.y, 1e-4f)
        }
    }

    @Test
    fun rotated90TopLeftMapsToPageLeftEdge() {
        // Strona obrócona o 90° w prawo: lewy górny róg ekranu to lewy dolny róg strony.
        val g = PageGeo(0f, 0f, 600f, 800f, 90)
        val (x, y) = g.toPdf(PointN(0f, 0f))
        assertEquals(0f, x, 1e-3f)
        assertEquals(0f, y, 1e-3f)
        assertNull(null)
    }

    @Test
    fun normalizingMatrixMatchesToPdf() {
        for (rot in listOf(0, 90, 180, 270)) {
            val g = PageGeo(5f, 7f, 600f, 800f, rot)
            val m = g.normalizingMatrix()
            val p = PointN(0.3f, 0.2f)
            val (px, py) = g.toPdf(p)
            val nx = m[0] * px + m[2] * py + m[4]
            val ny = m[1] * px + m[3] * py + m[5]
            assertEquals(p.x * g.displayWidth, nx, 1e-2f)
            assertEquals((1f - p.y) * g.displayHeight, ny, 1e-2f)
        }
    }
}
