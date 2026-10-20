package com.blacksingularities.openpdf

import com.blacksingularities.openpdf.doc.djvu.DjvuDocument
import com.blacksingularities.openpdf.doc.djvu.writeBmp
import okio.buffer
import okio.sink
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Dekoder DjVu na prawdziwych plikach. Próbki (np. z repozytorium djvu.js: library/assets) wskazuje
 * zmienna środowiskowa DJVU_SAMPLES; bez niej testy plikowe są pomijane.
 */
class DjvuTest {
    private val dir = System.getenv("DJVU_SAMPLES")?.let(::File)?.takeIf { it.isDirectory }

    private fun doc(name: String) = DjvuDocument(File(dir, name).readBytes())

    /** Średnia różnica kanałów względem wzorca (0–255). */
    private fun compare(name: String, page: Int, png: String): Double {
        val r = doc(name).render(page, maxSide = 100_000)
        val ref = ImageIO.read(File(dir, png))
        val out = BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_RGB)
        out.setRGB(0, 0, r.width, r.height, r.pixels, 0, r.width)
        File(dir, "out").mkdirs()
        ImageIO.write(out, "png", File(dir, "out/${png.substringBeforeLast('.')}-ours.png"))
        assertEquals("wymiary $name", ref.width to ref.height, r.width to r.height)
        var sum = 0L
        for (y in 0 until r.height) for (x in 0 until r.width) {
            val a = r.pixels[y * r.width + x]
            val b = ref.getRGB(x, y)
            sum += abs(((a shr 16) and 255) - ((b shr 16) and 255)) + abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))
        }
        val diff = sum.toDouble() / (3L * r.width * r.height)
        println("$name[$page] vs $png: średnia różnica %.2f".format(diff))
        return diff
    }

    @Test
    fun jb2() { if (dir != null) assertTrue(compare("boy_jb2.djvu", 0, "boy_jb2.png") < 1.0) }

    @Test
    fun iw44Color() { if (dir != null) assertTrue(compare("boy.djvu", 0, "boy.png") < 3.0) }

    @Test
    fun iw44Chicken() { if (dir != null) assertTrue(compare("chicken.djvu", 0, "chicken.png") < 3.0) }

    @Test
    fun compound() { if (dir != null) assertTrue(compare("happy_birthday.djvu", 0, "happy_birthday.png") < 6.0) }

    @Test
    fun bundledWithSharedDict() {
        if (dir == null) return
        val d = doc("DjVu3Spec_bundled.djvu")
        assertTrue(d.pageCount > 40)
        assertTrue(compare("DjVu3Spec_bundled.djvu", 47, "DjVu3Spec_48.png") < 3.0)
    }

    @Test
    fun palette() { if (dir != null) assertTrue(compare("navm_fgbz.djvu", 2, "navm_fgbz_3.png") < 6.0) }

    @Test
    fun scans() {
        if (dir == null) return
        assertTrue(compare("colorbook.djvu", 3, "colorbook_4.png") < 6.0)
        assertTrue(compare("deutsch.djvu", 0, "deutsch_1.png") < 6.0)
        assertTrue(compare("vega.djvu", 0, "vega_1.png") < 6.0)
        assertTrue(compare("irish.djvu", 0, "irish_1.png") < 6.0)
        assertTrue(compare("problem_page.djvu", 0, "problem_page.png") < 6.0)
    }

    @Test
    fun everyPageDecodesAndBmpIsReadable() {
        if (dir == null) return
        for (f in dir.listFiles { f -> f.name.endsWith(".djvu") }!!) {
            val d = DjvuDocument(f.readBytes())
            val t = System.currentTimeMillis()
            for (i in 0 until d.pageCount) {
                val r = d.render(i)
                assertTrue(r.width > 0 && r.height > 0)
                if (i == 0) {
                    val bmp = File(dir, "out/${f.nameWithoutExtension}.bmp")
                    bmp.parentFile.mkdirs()
                    bmp.sink().buffer().use { writeBmp(r, it) }
                    val back = ImageIO.read(bmp)
                    assertEquals(r.width to r.height, back.width to back.height)
                }
            }
            println("${f.name}: ${d.pageCount} str., ${System.currentTimeMillis() - t} ms")
        }
    }

}
