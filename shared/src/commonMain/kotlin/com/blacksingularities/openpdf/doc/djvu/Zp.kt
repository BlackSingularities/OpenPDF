package com.blacksingularities.openpdf.doc.djvu

/** Błąd w strukturze pliku DjVu. */
class DjvuException(message: String) : Exception(message)

/**
 * Adaptacyjny dekoder arytmetyczny ZP (specyfikacja DjVu v3). Konteksty to komórki tablicy [IntArray],
 * wartość komórki – numer stanu w [ZpTable].
 */
internal class ZpDecoder(private val data: ByteArray, private var pos: Int = 0, private val end: Int = data.size) {
    private var a = 0
    private var code = 0
    private var fence = 0
    private var buffer = 0
    private var scount = 0
    private var delay = 25

    init {
        code = (nextByte() shl 8) or nextByte()
        preload()
        fence = if (code >= 0x8000) 0x7fff else code
    }

    private fun nextByte(): Int = if (pos < end) data[pos++].toInt() and 0xFF else 0xFF

    private fun preload() {
        while (scount <= 24) {
            val b = if (pos < end) {
                data[pos++].toInt() and 0xFF
            } else {
                if (--delay < 1) throw DjvuException("Nieoczekiwany koniec danych")
                0xFF
            }
            buffer = (buffer shl 8) or b
            scount += 8
        }
    }

    /** Liczba jedynek na początku 16-bitowej liczby [x]. */
    private fun ffz(x: Int): Int = if (x >= 0xFF00) FFZT[x and 0xFF] + 8 else FFZT[(x shr 8) and 0xFF]

    fun decode(ctx: IntArray, i: Int): Int {
        val state = ctx[i]
        val z = a + ZpTable.P[state]
        if (z <= fence) {
            a = z
            return state and 1
        }
        return decodeSub(ctx, i, z)
    }

    private fun decodeSub(ctx: IntArray, i: Int, zIn: Int): Int {
        val state = ctx[i]
        val bit = state and 1
        var z = zIn
        val d = 0x6000 + ((z + a) shr 2)
        if (z > d) z = d
        return if (z > code) {
            z = 0x10000 - z
            a += z
            code += z
            ctx[i] = ZpTable.DN[state]
            lps()
            bit xor 1
        } else {
            if (a >= ZpTable.M[state]) ctx[i] = ZpTable.UP[state]
            mps(z)
            bit
        }
    }

    /** Bit bez kontekstu (prawdopodobieństwo ½). */
    fun decode(): Int = simple(0x8000 + (a shr 1))

    /** Bit bez kontekstu w odmianie IW44 (prawdopodobieństwo ≈ ⅜ dla jedynki). */
    fun decodeIw(): Int = simple(0x8000 + ((a + a + a) shr 3))

    private fun simple(zIn: Int): Int {
        var z = zIn
        return if (z > code) {
            z = 0x10000 - z
            a += z
            code += z
            lps()
            1
        } else {
            mps(z)
            0
        }
    }

    private fun lps() {
        val shift = ffz(a)
        scount -= shift
        a = (a shl shift) and 0xFFFF
        code = ((code shl shift) and 0xFFFF) or ((buffer ushr scount) and ((1 shl shift) - 1))
        if (scount < 16) preload()
        fence = if (code >= 0x8000) 0x7fff else code
    }

    private fun mps(z: Int) {
        scount -= 1
        a = (z shl 1) and 0xFFFF
        code = ((code shl 1) and 0xFFFF) or ((buffer ushr scount) and 1)
        if (scount < 16) preload()
        fence = if (code >= 0x8000) 0x7fff else code
    }

    private companion object {
        val FFZT = IntArray(256) { i ->
            var n = 0
            var j = i
            while (j and 0x80 != 0) { n++; j = j shl 1 }
            n
        }
    }
}

/** Dekompresja BZZ (transformata Burrowsa–Wheelera + koder ZP) – metadane, tekst, słowniki DjVu. */
internal object Bzz {
    fun decode(data: ByteArray, from: Int = 0, to: Int = data.size): ByteArray {
        val zp = ZpDecoder(data, from, to)
        val ctx = IntArray(300)
        val out = ArrayList<ByteArray>()
        var total = 0
        while (true) {
            val block = block(zp, ctx) ?: break
            out += block
            total += block.size
        }
        val result = ByteArray(total)
        var p = 0
        for (b in out) { b.copyInto(result, p); p += b.size }
        return result
    }

    private fun raw(zp: ZpDecoder, bits: Int): Int {
        var n = 1
        val m = 1 shl bits
        while (n < m) n = (n shl 1) or zp.decode()
        return n - m
    }

    private fun binary(zp: ZpDecoder, ctx: IntArray, base: Int, bits: Int): Int {
        var n = 1
        val m = 1 shl bits
        while (n < m) n = (n shl 1) or zp.decode(ctx, base + n - 1)
        return n - m
    }

    private fun block(zp: ZpDecoder, ctx: IntArray): ByteArray? {
        val size = raw(zp, 24)
        if (size == 0) return null
        if (size > 4096 * 1024) throw DjvuException("Uszkodzony blok BZZ")
        var fshift = 0
        if (zp.decode() != 0) {
            fshift++
            if (zp.decode() != 0) fshift++
        }
        val mtf = IntArray(256) { it }
        val freq = IntArray(4)
        var fadd = 4
        var mtfno = 3
        var marker = -1
        val buf = IntArray(size)
        for (i in 0 until size) {
            val cid = minOf(2, mtfno)
            mtfno = when {
                zp.decode(ctx, cid) != 0 -> 0
                zp.decode(ctx, 3 + cid) != 0 -> 1
                else -> {
                    var base = 6
                    var found = -1
                    for (bits in 1..7) {
                        if (zp.decode(ctx, base) != 0) {
                            found = (1 shl bits) + binary(zp, ctx, base + 1, bits)
                            break
                        }
                        base += 1 + (1 shl bits) - 1
                    }
                    found
                }
            }
            if (mtfno < 0) {
                mtfno = 256
                buf[i] = 0
                marker = i
                continue
            }
            val c = mtf[mtfno]
            buf[i] = c
            fadd += fadd shr fshift
            if (fadd > 0x10000000) {
                fadd = fadd shr 24
                for (k in 0 until 4) freq[k] = freq[k] shr 24
            }
            var fc = fadd
            if (mtfno < 4) fc += freq[mtfno]
            var k = mtfno
            while (k >= 4) { mtf[k] = mtf[k - 1]; k-- }
            while (k > 0 && fc.toUInt() >= freq[k - 1].toUInt()) {
                mtf[k] = mtf[k - 1]
                freq[k] = freq[k - 1]
                k--
            }
            mtf[k] = c
            freq[k] = fc
        }
        if (marker < 1 || marker >= size) throw DjvuException("Uszkodzony blok BZZ")
        // Odwrócenie transformaty BWT.
        val posn = IntArray(size)
        val count = IntArray(256)
        for (i in 0 until size) {
            if (i == marker) continue
            val c = buf[i]
            posn[i] = (c shl 24) or (count[c] and 0xFFFFFF)
            count[c]++
        }
        var last = 1
        for (i in 0 until 256) {
            val t = count[i]
            count[i] = last
            last += t
        }
        val out = ByteArray(size - 1)
        var i = 0
        last = size - 1
        while (last > 0) {
            val n = posn[i]
            val c = n ushr 24
            out[--last] = c.toByte()
            i = count[c] + (n and 0xFFFFFF)
        }
        if (i != marker) throw DjvuException("Uszkodzony blok BZZ")
        return out
    }
}
