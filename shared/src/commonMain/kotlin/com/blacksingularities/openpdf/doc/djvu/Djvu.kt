package com.blacksingularities.openpdf.doc.djvu

import okio.BufferedSink

/** Fragment pliku IFF: identyfikator i zakres danych w [DjvuDocument.data]. */
internal class Chunk(val id: String, val start: Int, val end: Int) {
    val size get() = end - start
}

/** Obraz strony gotowy do zapisu: piksele 0xRRGGBB od lewego górnego rogu, [dpi] po zmniejszeniu. */
class Raster(val width: Int, val height: Int, val pixels: IntArray, val dpi: Int, val gray: Boolean)

/**
 * Dokument DjVu (jedna strona „FORM:DJVU” albo pakiet wielostronicowy „FORM:DJVM”).
 * Dekodowane są warstwy JB2 (tekst), IW44 (tło i kolor pierwszego planu) oraz palety FGbz.
 */
class DjvuDocument(internal val data: ByteArray) {
    private val pages = ArrayList<Chunk>()           // FORM:DJVU kolejnych stron
    private val includes = HashMap<String, Chunk>()  // FORM:DJVI według identyfikatora z DIRM
    private val dicts = HashMap<String, Jb2Dict>()

    init {
        if (data.size < 16 || ascii(0, 4) != "AT&T" || ascii(4, 4) != "FORM") throw DjvuException("To nie jest plik DjVu")
        val top = form(4)
        when (top.second) {
            "DJVU" -> pages += top.first
            "DJVM" -> parseBundle(top.first)
            else -> throw DjvuException("Nieobsługiwany rodzaj pliku DjVu")
        }
        if (pages.isEmpty()) throw DjvuException("Dokument DjVu nie zawiera stron")
    }

    val pageCount get() = pages.size

    private fun ascii(at: Int, n: Int) = buildString { for (i in at until at + n) append((data[i].toInt() and 0xFF).toChar()) }

    private fun u32(at: Int) = ((data[at].toInt() and 0xFF) shl 24) or ((data[at + 1].toInt() and 0xFF) shl 16) or
        ((data[at + 2].toInt() and 0xFF) shl 8) or (data[at + 3].toInt() and 0xFF)

    /** Formularz zaczynający się nagłówkiem „FORM” w [at]: zawartość (bez typu) i typ. */
    private fun form(at: Int): Pair<Chunk, String> {
        val size = u32(at + 4)
        val end = at + 8 + size
        if (size < 4 || end > data.size) throw DjvuException("Uszkodzony plik DjVu")
        return Chunk("FORM", at + 12, end) to ascii(at + 8, 4)
    }

    /** Fragmenty leżące bezpośrednio w [parent]. */
    internal fun children(parent: Chunk): List<Chunk> {
        val out = ArrayList<Chunk>()
        var p = parent.start
        while (p + 8 <= parent.end) {
            val id = ascii(p, 4)
            val size = u32(p + 4)
            val start = p + 8
            val end = start + size
            if (size < 0 || end > parent.end) break
            out += if (id == "FORM") Chunk("FORM:" + ascii(start, 4), start + 4, end) else Chunk(id, start, end)
            p = end + (end and 1)
        }
        return out
    }

    private fun parseBundle(root: Chunk) {
        val items = children(root)
        val dirm = items.firstOrNull { it.id == "DIRM" } ?: throw DjvuException("Brak katalogu w pliku DjVu")
        val flags = data[dirm.start].toInt() and 0xFF
        if (flags and 0x80 == 0) {
            throw DjvuException("Wielokrotne pliki DjVu (strony w osobnych plikach) nie są obsługiwane – otwórz plik pakietu „bundled”")
        }
        val n = ((data[dirm.start + 1].toInt() and 0xFF) shl 8) or (data[dirm.start + 2].toInt() and 0xFF)
        val offsets = IntArray(n) { u32(dirm.start + 3 + it * 4) }
        val meta = Bzz.decode(data, dirm.start + 3 + n * 4, dirm.end)
        // Rozmiary (3 bajty), znaczniki (1 bajt), potem identyfikatory zakończone zerem.
        var p = n * 3
        val compFlags = IntArray(n) { meta.getOrNull(p + it)?.toInt()?.and(0xFF) ?: 0 }
        p += n
        val ids = ArrayList<String>()
        fun str(): String {
            val s = p
            while (p < meta.size && meta[p].toInt() != 0) p++
            val r = meta.copyOfRange(s, p).decodeToString()
            p++
            return r
        }
        for (i in 0 until n) {
            ids += str()
            if (compFlags[i] and 0x80 != 0) str()
            if (compFlags[i] and 0x40 != 0) str()
        }
        for (i in 0 until n) {
            val off = offsets[i]
            val at = when {
                off + 12 <= data.size && ascii(off, 4) == "FORM" -> off
                off + 16 <= data.size && ascii(off + 4, 4) == "FORM" -> off + 4
                else -> continue
            }
            val (chunk, type) = runCatching { form(at) }.getOrNull() ?: continue
            when {
                type == "DJVU" -> pages += chunk
                type == "DJVI" -> includes[ids[i]] = chunk
            }
        }
    }

    private fun dict(id: String): Jb2Dict? {
        dicts[id]?.let { return it }
        val inc = includes[id] ?: return null
        val djbz = children(inc).firstOrNull { it.id == "Djbz" } ?: return null
        return Jb2Decoder(data, djbz.start, djbz.end, null).decodeDict().also { dicts[id] = it }
    }

    /**
     * Strona [index] zmniejszona tak, by dłuższy bok miał najwyżej [maxSide] pikseli.
     * Krawędzie liter są wygładzane (pokrycie piksela liczone z pełnej rozdzielczości maski).
     */
    fun render(index: Int, maxSide: Int = 2400): Raster {
        val parts = children(pages[index])
        val info = parts.firstOrNull { it.id == "INFO" } ?: throw DjvuException("Strona DjVu bez nagłówka")
        val b = { i: Int -> if (info.start + i < info.end) data[info.start + i].toInt() and 0xFF else -1 }
        val width = (b(0) shl 8) or b(1)
        val height = (b(2) shl 8) or b(3)
        var dpi = if (info.size >= 8 && b(7) != 0xFF) (b(7) shl 8) or b(6) else 300
        if (dpi < 25 || dpi > 6000) dpi = 300
        val rotation = when (if (info.size >= 10) b(9) and 7 else 0) { 6 -> 1; 2 -> 2; 5 -> 3; else -> 0 }
        if (width <= 0 || height <= 0) throw DjvuException("Strona DjVu bez wymiarów")

        var shared: Jb2Dict? = null
        var mask: Jb2Image? = null
        var palette: IntArray? = null
        var blitColors: IntArray? = null
        val bg = Iw44Image()
        val fg = Iw44Image()
        var hasBg = false
        var hasFg = false
        for (c in parts) {
            when (c.id) {
                "INCL" -> shared = dict(data.copyOfRange(c.start, c.end).decodeToString().trim()) ?: shared
                "Djbz" -> shared = Jb2Decoder(data, c.start, c.end, null).decodeDict()
                "Sjbz" -> mask = Jb2Decoder(data, c.start, c.end, shared).decodeImage()
                "BG44" -> runCatching { bg.decodeChunk(data, c.start, c.end); hasBg = true }
                "FG44" -> runCatching { fg.decodeChunk(data, c.start, c.end); hasFg = true }
                "FGbz" -> runCatching { readPalette(c) }.getOrNull()?.let { palette = it.first; blitColors = it.second }
            }
        }
        if (mask == null && !hasBg) throw DjvuException("Strona DjVu nie zawiera obsługiwanych warstw obrazu")

        val sub = maxOf(1, (maxOf(width, height) + maxSide - 1) / maxSide)
        val ow = (width + sub - 1) / sub
        val oh = (height + sub - 1) / sub
        val out = IntArray(ow * oh)

        // Tło: obraz IW44 w zmniejszonej rozdzielczości (zwykle co trzeci piksel).
        if (hasBg && bg.width > 0) {
            val red = reduction(width, height, bg.width, bg.height)
            // Tło odtwarzane od razu w skali zbliżonej do wyjściowej (potęga dwójki).
            val s = pow2AtMost(sub / red)
            val px = bg.rgb(s)
            val bw = bg.scaledWidth(s)
            val bh = bg.scaledHeight(s)
            for (y in 0 until oh) {
                val sy = minOf(bh - 1, (y * sub + sub / 2) / (red * s))
                for (x in 0 until ow) {
                    val sx = minOf(bw - 1, (x * sub + sub / 2) / (red * s))
                    out[y * ow + x] = px[sy * bw + sx]
                }
            }
        } else {
            out.fill(0xFFFFFF)
        }

        var gray = !hasBg || isGray(out)
        val m = mask
        if (m != null) {
            val fgPx = if (hasFg && fg.width > 0) fg.rgb(1) else null
            val fgRed = if (fgPx != null) reduction(width, height, fg.width, fg.height) else 1
            val cover = ShortArray(ow * oh)
            val color = if (palette != null || fgPx != null) IntArray(ow * oh) else null
            for (k in 0 until m.blitCount) {
                val left = m.blits[k * 3]
                val bottom = m.blits[k * 3 + 1]
                val s = m.shapes.getOrNull(m.blits[k * 3 + 2]) ?: continue
                val col = palette?.let { pal -> blitColors?.getOrNull(k)?.let { pal.getOrNull(it) } ?: pal.firstOrNull() }
                for (sy in 0 until s.h) {
                    val py = bottom + sy
                    if (py < 0 || py >= height) continue
                    val oy = (height - 1 - py) / sub
                    val rowBits = sy * s.w
                    for (sx in 0 until s.w) {
                        if (s.bits[rowBits + sx].toInt() == 0) continue
                        val pxX = left + sx
                        if (pxX < 0 || pxX >= width) continue
                        val o = oy * ow + pxX / sub
                        cover[o]++
                        if (color != null) {
                            color[o] = col ?: fgPx!![minOf(fg.height - 1, (height - 1 - py) / fgRed) * fg.width + minOf(fg.width - 1, pxX / fgRed)]
                        }
                    }
                }
            }
            val area = sub * sub
            for (i in out.indices) {
                val c = cover[i].toInt()
                if (c == 0) continue
                val ink = color?.get(i) ?: 0
                out[i] = if (c >= area) ink else blend(out[i], ink, c * 256 / area)
            }
            if (color != null && gray) gray = isGray(out)
        }
        val r = Raster(ow, oh, out, maxOf(1, dpi / sub), gray)
        return if (rotation == 0) r else rotate(r, rotation)
    }

    /** Paleta kolorów pierwszego planu: kolory 0xRRGGBB i numer koloru dla każdego odbicia kształtu. */
    private fun readPalette(c: Chunk): Pair<IntArray, IntArray?> {
        var p = c.start
        val version = data[p++].toInt() and 0xFF
        val n = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
        p += 2
        val pal = IntArray(n) {
            val bl = data[p].toInt() and 0xFF
            val g = data[p + 1].toInt() and 0xFF
            val r = data[p + 2].toInt() and 0xFF
            p += 3
            (r shl 16) or (g shl 8) or bl
        }
        if (version and 0x80 == 0) return pal to null
        val count = ((data[p].toInt() and 0xFF) shl 16) or ((data[p + 1].toInt() and 0xFF) shl 8) or (data[p + 2].toInt() and 0xFF)
        p += 3
        val raw = Bzz.decode(data, p, c.end)
        val idx = IntArray(minOf(count, raw.size / 2)) { (((raw[it * 2].toInt() and 0xFF) shl 8) or (raw[it * 2 + 1].toInt() and 0xFF)).coerceIn(0, maxOf(0, n - 1)) }
        return pal to idx
    }

    private fun pow2AtMost(v: Int): Int {
        var p = 1
        while (p * 2 <= v && p < 32) p *= 2
        return p
    }

    private fun reduction(w: Int, h: Int, rw: Int, rh: Int): Int {
        for (red in 1..12) if ((w + red - 1) / red == rw && (h + red - 1) / red == rh) return red
        return maxOf(1, (w + rw - 1) / rw)
    }

    private fun isGray(px: IntArray): Boolean {
        var i = 0
        while (i < px.size) {
            val v = px[i]
            val r = (v shr 16) and 0xFF
            val g = (v shr 8) and 0xFF
            val bl = v and 0xFF
            if (kotlin.math.abs(r - g) > 6 || kotlin.math.abs(g - bl) > 6) return false
            i += 7
        }
        return true
    }

    private fun blend(a: Int, b: Int, t: Int): Int {
        val u = 256 - t
        val r = (((a shr 16) and 0xFF) * u + ((b shr 16) and 0xFF) * t) shr 8
        val g = (((a shr 8) and 0xFF) * u + ((b shr 8) and 0xFF) * t) shr 8
        val bl = ((a and 0xFF) * u + (b and 0xFF) * t) shr 8
        return (r shl 16) or (g shl 8) or bl
    }

    /** Obrót o [quarter] ćwierćobrotów przeciwnie do ruchu wskazówek zegara. */
    private fun rotate(r: Raster, quarter: Int): Raster {
        val w = r.width
        val h = r.height
        return when (quarter) {
            2 -> Raster(w, h, IntArray(w * h) { r.pixels[w * h - 1 - it] }, r.dpi, r.gray)
            1 -> Raster(h, w, IntArray(w * h) { i -> val x = i % h; val y = i / h; r.pixels[x * w + (w - 1 - y)] }, r.dpi, r.gray)
            else -> Raster(h, w, IntArray(w * h) { i -> val x = i % h; val y = i / h; r.pixels[(h - 1 - x) * w + y] }, r.dpi, r.gray)
        }
    }
}

/** Zapis obrazu w formacie BMP (8 bitów w skali szarości albo 24 bity) – czytają go dekodery Androida i iOS. */
fun writeBmp(r: Raster, sink: BufferedSink) {
    val bpp = if (r.gray) 8 else 24
    val rowBytes = ((r.width * bpp / 8) + 3) and 3.inv()
    val paletteSize = if (r.gray) 256 * 4 else 0
    val offset = 14 + 40 + paletteSize
    val size = offset + rowBytes * r.height
    fun le16(v: Int) { sink.writeByte(v and 0xFF); sink.writeByte((v shr 8) and 0xFF) }
    fun le32(v: Int) { le16(v and 0xFFFF); le16((v ushr 16) and 0xFFFF) }
    sink.writeByte('B'.code); sink.writeByte('M'.code)
    le32(size); le32(0); le32(offset)
    le32(40); le32(r.width); le32(r.height); le16(1); le16(bpp); le32(0); le32(rowBytes * r.height)
    val ppm = (r.dpi * 39.37).toInt()
    le32(ppm); le32(ppm); le32(if (r.gray) 256 else 0); le32(0)
    if (r.gray) for (i in 0 until 256) { sink.writeByte(i); sink.writeByte(i); sink.writeByte(i); sink.writeByte(0) }
    val row = ByteArray(rowBytes)
    for (y in r.height - 1 downTo 0) {
        val base = y * r.width
        if (r.gray) {
            for (x in 0 until r.width) {
                val v = r.pixels[base + x]
                row[x] = ((((v shr 16) and 0xFF) * 77 + ((v shr 8) and 0xFF) * 150 + (v and 0xFF) * 29) shr 8).toByte()
            }
        } else {
            for (x in 0 until r.width) {
                val v = r.pixels[base + x]
                row[x * 3] = v.toByte()
                row[x * 3 + 1] = (v shr 8).toByte()
                row[x * 3 + 2] = (v shr 16).toByte()
            }
        }
        sink.write(row)
    }
}
