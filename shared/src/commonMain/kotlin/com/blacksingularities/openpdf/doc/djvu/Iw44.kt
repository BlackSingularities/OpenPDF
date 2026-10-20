package com.blacksingularities.openpdf.doc.djvu

/**
 * Współczynniki falkowe jednej składowej (Y, Cb albo Cr) obrazu IW44: bloki 32×32 podzielone na
 * 64 „kubełki” po 16 współczynników w kolejności zygzakowej.
 */
private class IwMap(val iw: Int, val ih: Int) {
    val bw = (iw + 31) and 31.inv()
    val bh = (ih + 31) and 31.inv()
    val nb = (bw / 32) * (bh / 32)
    val coeff = arrayOfNulls<ShortArray>(nb * 64)

    fun bucket(block: Int, b: Int): ShortArray? = coeff[block * 64 + b]
    fun alloc(block: Int, b: Int): ShortArray = coeff[block * 64 + b] ?: ShortArray(16).also { coeff[block * 64 + b] = it }

    /**
     * Odtworzenie składowej w skali 1:[s] ([s] – potęga dwójki ≤ 32): wartości −128…127, wiersze od dołu,
     * wymiary `ceil(iw/s) × ceil(ih/s)`. Transformata na poziomach ≥ [s] dotyka tylko punktów o współrzędnych
     * podzielnych przez [s], więc wystarczy zwarta tablica tych punktów – bez pełnej rozdzielczości w pamięci.
     */
    fun image(s: Int): ByteArray {
        val cw = bw / s
        val ch = bh / s
        val data = ShortArray(cw * ch)
        var block = 0
        for (i in 0 until bh step 32) {
            for (j in 0 until bw step 32) {
                for (b in 0 until 64) {
                    val d = coeff[block * 64 + b] ?: continue
                    for (k in 0 until 16) {
                        val z = ZIGZAG[b * 16 + k]
                        val zy = z shr 5
                        val zx = z and 31
                        if ((zy or zx) and (s - 1) != 0) continue
                        data[((i + zy) / s) * cw + (j + zx) / s] = d[k]
                    }
                }
                block++
            }
        }
        val ow = (iw - 1) / s + 1
        val oh = (ih - 1) / s + 1
        backward(data, ow, oh, cw, 32 / s, 1)
        val out = ByteArray(ow * oh)
        for (i in 0 until oh) {
            val row = i * cw
            for (j in 0 until ow) {
                val x = (data[row + j] + 32) shr 6
                out[i * ow + j] = x.coerceIn(-128, 127).toByte()
            }
        }
        return out
    }

    companion object {
        /** Położenie n-tego współczynnika (kolejność zygzakowa) w bloku 32×32: bity n przeplatają kolumnę i wiersz. */
        val ZIGZAG = IntArray(1024) { n ->
            var x = 0
            var y = 0
            var v = n
            for (i in 0 until 5) {
                x = x or ((v and 1) shl (4 - i)); v = v shr 1
                y = y or ((v and 1) shl (4 - i)); v = v shr 1
            }
            y * 32 + x
        }

        fun backward(p: ShortArray, w: Int, h: Int, rowsize: Int, begin: Int, end: Int) {
            var scale = begin shr 1
            while (scale >= end) {
                filterBv(p, w, h, rowsize, scale)
                filterBh(p, w, h, rowsize, scale)
                scale = scale shr 1
            }
        }

        private fun filterBv(d: ShortArray, w: Int, h0: Int, rowsize: Int, scale: Int) {
            var y = 0
            val s = scale * rowsize
            val s3 = s + s + s
            val h = ((h0 - 1) / scale) + 1
            var p = 0
            while (y - 3 < h) {
                // 1 – lifting
                run {
                    var q = p
                    val e = q + w
                    if (y >= 3 && y + 3 < h) {
                        while (q < e) {
                            val a = d[q - s] + d[q + s]
                            val b = d[q - s3] + d[q + s3]
                            d[q] = (d[q] - (((a shl 3) + a - b + 16) shr 5)).toShort()
                            q += scale
                        }
                    } else if (y < h) {
                        val has1 = y + 1 < h
                        val has3 = y + 3 < h
                        while (q < e) {
                            val a = (if (y >= 1) d[q - s].toInt() else 0) + (if (has1) d[q + s].toInt() else 0)
                            val b = (if (y >= 3) d[q - s3].toInt() else 0) + (if (has3) d[q + s3].toInt() else 0)
                            d[q] = (d[q] - (((a shl 3) + a - b + 16) shr 5)).toShort()
                            q += scale
                        }
                    }
                }
                // 2 – interpolacja
                run {
                    var q = p - s3
                    val e = q + w
                    if (y >= 6 && y < h) {
                        while (q < e) {
                            val a = d[q - s] + d[q + s]
                            val b = d[q - s3] + d[q + s3]
                            d[q] = (d[q] + (((a shl 3) + a - b + 8) shr 4)).toShort()
                            q += scale
                        }
                    } else if (y >= 3) {
                        var q1 = if (y - 2 < h) q + s else q - s
                        while (q < e) {
                            val a = d[q - s] + d[q1]
                            d[q] = (d[q] + ((a + 1) shr 1)).toShort()
                            q += scale
                            q1 += scale
                        }
                    }
                }
                y += 2
                p += s + s
            }
        }

        private fun filterBh(d: ShortArray, w: Int, h: Int, rowsize0: Int, scale: Int) {
            var y = 0
            val s = scale
            val s3 = s + s + s
            val rowsize = rowsize0 * scale
            var p = 0
            while (y < h) {
                var q = p
                val e = p + w
                var a0 = 0; var a1 = 0; var a2 = 0; var a3 = 0
                var b0 = 0; var b1 = 0; var b2 = 0; var b3 = 0
                if (q < e) {
                    if (q + s < e) a2 = d[q + s].toInt()
                    if (q + s3 < e) a3 = d[q + s3].toInt()
                    b3 = d[q] - ((((a1 + a2) shl 3) + (a1 + a2) - a0 - a3 + 16) shr 5)
                    b2 = b3
                    d[q] = b3.toShort()
                    q += s + s
                }
                if (q < e) {
                    a0 = a1; a1 = a2; a2 = a3
                    if (q + s3 < e) a3 = d[q + s3].toInt()
                    b3 = d[q] - ((((a1 + a2) shl 3) + (a1 + a2) - a0 - a3 + 16) shr 5)
                    d[q] = b3.toShort()
                    q += s + s
                }
                if (q < e) {
                    b1 = b2; b2 = b3
                    a0 = a1; a1 = a2; a2 = a3
                    if (q + s3 < e) a3 = d[q + s3].toInt()
                    b3 = d[q] - ((((a1 + a2) shl 3) + (a1 + a2) - a0 - a3 + 16) shr 5)
                    d[q] = b3.toShort()
                    d[q - s3] = (d[q - s3] + ((b1 + b2 + 1) shr 1)).toShort()
                    q += s + s
                }
                while (q + s3 < e) {
                    a0 = a1; a1 = a2; a2 = a3; a3 = d[q + s3].toInt()
                    b0 = b1; b1 = b2; b2 = b3
                    b3 = d[q] - ((((a1 + a2) shl 3) + (a1 + a2) - a0 - a3 + 16) shr 5)
                    d[q] = b3.toShort()
                    d[q - s3] = (d[q - s3] + ((((b1 + b2) shl 3) + (b1 + b2) - b0 - b3 + 8) shr 4)).toShort()
                    q += s + s
                }
                while (q < e) {
                    a0 = a1; a1 = a2; a2 = a3; a3 = 0
                    b0 = b1; b1 = b2; b2 = b3
                    b3 = d[q] - ((((a1 + a2) shl 3) + (a1 + a2) - a0 - a3 + 16) shr 5)
                    d[q] = b3.toShort()
                    d[q - s3] = (d[q - s3] + ((((b1 + b2) shl 3) + (b1 + b2) - b0 - b3 + 8) shr 4)).toShort()
                    q += s + s
                }
                while (q - s3 < e) {
                    b0 = b1; b1 = b2; b2 = b3
                    if (q - s3 >= p) d[q - s3] = (d[q - s3] + ((b1 + b2 + 1) shr 1)).toShort()
                    q += s + s
                }
                y += scale
                p += rowsize
            }
        }
    }
}

/** Dekoder kolejnych „plasterków” współczynników jednej składowej. */
private class IwCodec(private val map: IwMap) {
    private val quantLo = IntArray(16)
    private val quantHi = IntArray(10)
    private var curBand = 0
    private var curBit = 1
    private val ctxStart = IntArray(32)
    private val ctxBucket = IntArray(10 * 8)
    private val ctxMant = IntArray(1)
    private val ctxRoot = IntArray(1)
    private val coeffState = IntArray(256)
    private val bucketState = IntArray(16)

    init {
        for (i in 0 until 4) quantLo[i] = QUANT[i]
        for (i in 4 until 8) quantLo[i] = QUANT[4]
        for (i in 8 until 12) quantLo[i] = QUANT[5]
        for (i in 12 until 16) quantLo[i] = QUANT[6]
        for (j in 1 until 10) quantHi[j] = QUANT[6 + j]
    }

    private fun isNullSlice(band: Int): Boolean {
        if (band == 0) {
            var isNull = true
            for (i in 0 until 16) {
                val t = quantLo[i]
                coeffState[i] = ZERO
                if (t in 1 until 0x8000) {
                    coeffState[i] = UNK
                    isNull = false
                }
            }
            return isNull
        }
        val t = quantHi[band]
        return !(t in 1 until 0x8000)
    }

    /** Zwraca false, gdy wszystkie plasterki zostały już odczytane. */
    fun slice(zp: ZpDecoder): Boolean {
        if (curBit < 0) return false
        if (!isNullSlice(curBand)) {
            val f = BAND_START[curBand]
            val n = BAND_SIZE[curBand]
            for (block in 0 until map.nb) buckets(zp, curBand, block, f, n)
        }
        quantHi[curBand] = quantHi[curBand] shr 1
        if (curBand == 0) for (i in 0 until 16) quantLo[i] = quantLo[i] shr 1
        if (++curBand >= 10) {
            curBand = 0
            curBit++
            if (quantHi[9] == 0) {
                curBit = -1
                return false
            }
        }
        return true
    }

    private fun prepare(f: Int, n: Int, block: Int): Int {
        var bb = 0
        if (f != 0) {
            for (b in 0 until n) {
                var bs = 0
                val pc = map.bucket(block, f + b)
                if (pc == null) {
                    bs = UNK
                } else {
                    for (i in 0 until 16) {
                        val cs = if (pc[i].toInt() != 0) ACTIVE else UNK
                        coeffState[b * 16 + i] = cs
                        bs = bs or cs
                    }
                }
                bucketState[b] = bs
                bb = bb or bs
            }
        } else {
            val pc = map.bucket(block, 0)
            if (pc == null) {
                bb = UNK
            } else {
                for (i in 0 until 16) {
                    var cs = coeffState[i]
                    if (cs != ZERO) cs = if (pc[i].toInt() != 0) ACTIVE else UNK
                    coeffState[i] = cs
                    bb = bb or cs
                }
            }
            bucketState[0] = bb
        }
        return bb
    }

    private fun buckets(zp: ZpDecoder, band: Int, block: Int, f: Int, n: Int) {
        var bb = prepare(f, n, block)
        if (n < 16 || bb and ACTIVE != 0) {
            bb = bb or NEW
        } else if (bb and UNK != 0) {
            if (zp.decode(ctxRoot, 0) != 0) bb = bb or NEW
        }
        if (bb and NEW != 0) {
            for (b in 0 until n) {
                if (bucketState[b] and UNK == 0) continue
                var ctx = 0
                if (band > 0) {
                    val k = (f + b) shl 2
                    val parent = map.bucket(block, k shr 4)
                    if (parent != null) {
                        val kk = k and 0xF
                        if (parent[kk].toInt() != 0) ctx++
                        if (parent[kk + 1].toInt() != 0) ctx++
                        if (parent[kk + 2].toInt() != 0) ctx++
                        if (ctx < 3 && parent[kk + 3].toInt() != 0) ctx++
                    }
                }
                if (bb and ACTIVE != 0) ctx = ctx or 4
                if (zp.decode(ctxBucket, band * 8 + ctx) != 0) bucketState[b] = bucketState[b] or NEW
            }
            var thres = quantHi[band]
            for (b in 0 until n) {
                if (bucketState[b] and NEW == 0) continue
                val cs = b * 16
                var pc = map.bucket(block, f + b)
                if (pc == null) {
                    pc = map.alloc(block, f + b)
                    if (f == 0) {
                        for (i in 0 until 16) if (coeffState[cs + i] != ZERO) coeffState[cs + i] = UNK
                    } else {
                        for (i in 0 until 16) coeffState[cs + i] = UNK
                    }
                }
                var gotcha = 0
                for (i in 0 until 16) if (coeffState[cs + i] and UNK != 0) gotcha++
                for (i in 0 until 16) {
                    if (coeffState[cs + i] and UNK == 0) continue
                    if (band == 0) thres = quantLo[i]
                    var ctx = minOf(gotcha, 7)
                    if (bucketState[b] and ACTIVE != 0) ctx = ctx or 8
                    if (zp.decode(ctxStart, ctx) != 0) {
                        coeffState[cs + i] = coeffState[cs + i] or NEW
                        val half = thres shr 1
                        val coeff = thres + half - (half shr 2)
                        pc[i] = (if (zp.decodeIw() != 0) -coeff else coeff).toShort()
                    }
                    if (coeffState[cs + i] and NEW != 0) gotcha = 0 else if (gotcha > 0) gotcha--
                }
            }
        }
        if (bb and ACTIVE != 0) {
            var thres = quantHi[band]
            for (b in 0 until n) {
                if (bucketState[b] and ACTIVE == 0) continue
                val pc = map.bucket(block, f + b) ?: continue
                val cs = b * 16
                for (i in 0 until 16) {
                    if (coeffState[cs + i] and ACTIVE == 0) continue
                    val orig = pc[i].toInt()
                    var coeff = if (orig < 0) -orig else orig
                    if (band == 0) thres = quantLo[i]
                    if (coeff <= 3 * thres) {
                        coeff += thres shr 2
                        coeff = if (zp.decode(ctxMant, 0) != 0) coeff + (thres shr 1) else coeff - thres + (thres shr 1)
                    } else {
                        coeff = if (zp.decodeIw() != 0) coeff + (thres shr 1) else coeff - thres + (thres shr 1)
                    }
                    pc[i] = (if (orig > 0) coeff else -coeff).toShort()
                }
            }
        }
    }

    private companion object {
        const val ZERO = 1
        const val ACTIVE = 2
        const val NEW = 4
        const val UNK = 8
        val QUANT = intArrayOf(
            0x004000, 0x008000, 0x008000, 0x010000, 0x010000, 0x010000, 0x020000, 0x020000,
            0x020000, 0x040000, 0x040000, 0x040000, 0x080000, 0x040000, 0x040000, 0x080000,
        )
        val BAND_START = intArrayOf(0, 1, 2, 3, 4, 8, 12, 16, 32, 48)
        val BAND_SIZE = intArrayOf(1, 1, 1, 1, 4, 4, 4, 16, 16, 16)
    }
}

/** Obraz IW44 (BG44/FG44/BM44/PM44) dekodowany z kolejnych porcji danych. */
internal class Iw44Image {
    private var y: IwCodec? = null
    private var cb: IwCodec? = null
    private var cr: IwCodec? = null
    private var yMap: IwMap? = null
    private var cbMap: IwMap? = null
    private var crMap: IwMap? = null
    private var serial = 0
    private var slices = 0
    private var crcbDelay = 0
    private var crcbHalf = false
    private var gray = false

    val width get() = yMap?.iw ?: 0
    val height get() = yMap?.ih ?: 0

    fun decodeChunk(data: ByteArray, from: Int, to: Int) {
        var p = from
        fun u8() = if (p < to) data[p++].toInt() and 0xFF else throw DjvuException("Uszkodzony obraz IW44")
        val ser = u8()
        if (ser != serial) throw DjvuException("Uszkodzony obraz IW44")
        val total = slices + u8()
        if (serial == 0) {
            val major = u8()
            val minor = u8()
            if ((major and 0x7F) != 1) throw DjvuException("Nieobsługiwana wersja IW44")
            val w = (u8() shl 8) or u8()
            val h = (u8() shl 8) or u8()
            var delayByte = 0
            if (minor >= 2) delayByte = u8()
            crcbDelay = if (minor >= 2) delayByte and 0x7F else 0
            crcbHalf = minor >= 2 && (delayByte and 0x80) == 0
            gray = major and 0x80 != 0
            if (w <= 0 || h <= 0 || w.toLong() * h > 200_000_000L) throw DjvuException("Uszkodzony obraz IW44")
            yMap = IwMap(w, h).also { y = IwCodec(it) }
            if (!gray) {
                cbMap = IwMap(w, h).also { cb = IwCodec(it) }
                crMap = IwMap(w, h).also { cr = IwCodec(it) }
            }
        }
        val zp = ZpDecoder(data, p, to)
        var flag = true
        while (flag && slices < total) {
            flag = y!!.slice(zp)
            if (!gray && crcbDelay <= slices) {
                flag = cb!!.slice(zp) or flag
                flag = cr!!.slice(zp) or flag
            }
            slices++
        }
        serial++
    }

    /** Szerokość obrazu po zmniejszeniu 1:[s]. */
    fun scaledWidth(s: Int) = (width - 1) / s + 1
    fun scaledHeight(s: Int) = (height - 1) / s + 1

    /** Piksele 0xRRGGBB w skali 1:[s] (potęga dwójki ≤ 32), wiersze od góry. */
    fun rgb(s: Int = 1): IntArray {
        val ym = yMap ?: return IntArray(0)
        val w = scaledWidth(s)
        val h = scaledHeight(s)
        val yy = ym.image(s)
        val out = IntArray(w * h)
        if (gray) {
            for (i in 0 until h) {
                val src = i * w
                val dst = (h - 1 - i) * w
                for (j in 0 until w) {
                    val v = 127 - yy[src + j]
                    out[dst + j] = (v shl 16) or (v shl 8) or v
                }
            }
            return out
        }
        // Chrominancja zapisana w połowie rozdzielczości jest odtwarzana co najmniej w skali 1:2.
        val cs = if (crcbHalf) maxOf(s, 2) else s
        val k = cs / s
        val cw = scaledWidth(cs)
        val bb = cbMap!!.image(cs)
        val rr = crMap!!.image(cs)
        for (i in 0 until h) {
            val src = i * w
            val dst = (h - 1 - i) * w
            val crow = (i / k) * cw
            for (j in 0 until w) {
                val yv = yy[src + j].toInt()
                val b = bb[crow + j / k].toInt()
                val r = rr[crow + j / k].toInt()
                val t1 = b shr 2
                val t2 = r + (r shr 1)
                val t3 = yv + 128 - t1
                val tr = (yv + 128 + t2).coerceIn(0, 255)
                val tg = (t3 - (t2 shr 1)).coerceIn(0, 255)
                val tb = (t3 + (b shl 1)).coerceIn(0, 255)
                out[dst + j] = (tr shl 16) or (tg shl 8) or tb
            }
        }
        return out
    }
}
