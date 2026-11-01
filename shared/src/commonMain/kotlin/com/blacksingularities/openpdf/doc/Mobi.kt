package com.blacksingularities.openpdf.doc

import com.blacksingularities.openpdf.i18n.tr

/**
 * Książki Kindle bez DRM: MOBI (Mobipocket) i AZW3/KF8. Tekst w rekordach PDB skompresowany
 * PalmDOC (LZ77) albo nieskompresowany; obrazy w osobnych rekordach (`recindex`, `kindle:embed`).
 */
internal object Mobi {
    fun isMobi(head: ByteArray): Boolean {
        if (head.size < 68) return false
        val type = head.copyOfRange(60, 68).decodeToString()
        return type == "BOOKMOBI" || type == "TEXtREAd"
    }

    fun read(data: ByteArray, fallbackTitle: String): TextDocument {
        fun u16(o: Int) = ((data[o].toInt() and 0xFF) shl 8) or (data[o + 1].toInt() and 0xFF)
        fun u32(o: Int) = (u16(o).toLong() shl 16 or u16(o + 2).toLong()).toInt()
        val count = u16(76)
        val offsets = IntArray(count) { u32(78 + it * 8) }
        fun record(i: Int): ByteArray {
            val end = if (i + 1 < count) offsets[i + 1] else data.size
            return data.copyOfRange(offsets[i], end.coerceAtMost(data.size))
        }
        val r0 = offsets[0]
        val compression = u16(r0)
        val textRecords = u16(r0 + 8)
        if (u16(r0 + 12) != 0) throw UnsupportedFormatException(tr("Książka jest zabezpieczona DRM – nie można jej otworzyć"))
        if (compression == 17480) throw UnsupportedFormatException(tr("Ten rodzaj kompresji MOBI (HUFF/CDIC) nie jest obsługiwany – przekonwertuj książkę do EPUB"))

        val isMobiHeader = data.size > r0 + 20 && data.copyOfRange(r0 + 16, r0 + 20).decodeToString() == "MOBI"
        var encoding = 1252
        var title: String? = null
        var firstImage = -1
        var extraFlags = 0
        if (isMobiHeader) {
            val headerLen = u32(r0 + 20)
            encoding = u32(r0 + 28)
            val nameOff = u32(r0 + 84)
            val nameLen = u32(r0 + 88)
            if (nameLen in 1..1024 && r0 + nameOff + nameLen <= data.size) {
                title = decode(data.copyOfRange(r0 + nameOff, r0 + nameOff + nameLen), encoding).trim()
            }
            firstImage = u32(r0 + 108)
            if (headerLen >= 0xE4) extraFlags = u16(r0 + 0xF2)
        }

        val text = okio.Buffer()
        for (i in 1..minOf(textRecords, count - 1)) {
            var rec = record(i)
            rec = rec.copyOfRange(0, (rec.size - trailingSize(rec, extraFlags)).coerceAtLeast(0))
            val chunk = if (compression == 2) palmDoc(rec) else rec
            text.write(chunk)
        }
        val html = decode(text.readByteArray(), encoding)
        val image: (String) -> ByteArray? = { src ->
            val index = when {
                src.startsWith("rec:") -> src.removePrefix("rec:").toIntOrNull()
                src.startsWith("kindle:embed:") -> base32(src.removePrefix("kindle:embed:").substringBefore('?'))
                else -> null
            }
            if (index == null || firstImage < 0) null
            else (firstImage + index - 1).takeIf { it in 0 until count }?.let { record(it) }
        }
        val doc = DocumentReader.html(html, title ?: fallbackTitle, image)
        if (doc.blocks.isEmpty()) error(tr("Książka nie zawiera tekstu"))
        return TextDocument(title ?: fallbackTitle, doc.author, doc.language, doc.blocks, PageStyle.BOOK)
    }

    private fun decode(bytes: ByteArray, encoding: Int): String =
        if (encoding == 65001) bytes.decodeToString() else decodeText(bytes, "windows-1252")

    /** Liczba w systemie base32 (0-9A-V) z odnośników `kindle:embed:XXXX`. */
    private fun base32(s: String): Int? = s.uppercase().fold(0) { acc, c ->
        val v = when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'V' -> c - 'A' + 10
            else -> return null
        }
        acc * 32 + v
    }

    /** Dodatkowe dane doklejane na końcu rekordów tekstu (indeksy, znaki wielobajtowe). */
    private fun trailingSize(rec: ByteArray, flags: Int): Int {
        var size = 0
        var f = flags shr 1
        while (f != 0) {
            if (f and 1 != 0) {
                var pos = rec.size - size
                var result = 0
                var shift = 0
                while (pos > 0) {
                    val v = rec[pos - 1].toInt() and 0xFF
                    result = result or ((v and 0x7F) shl shift)
                    shift += 7
                    pos--
                    if (v and 0x80 != 0 || shift >= 28) break
                }
                size += result
            }
            f = f shr 1
        }
        if (flags and 1 != 0 && rec.size - size - 1 >= 0) size += (rec[rec.size - size - 1].toInt() and 3) + 1
        return size
    }

    /** Dekompresja PalmDOC (prosty wariant LZ77). */
    private fun palmDoc(src: ByteArray): ByteArray {
        var out = ByteArray(maxOf(4096, src.size * 2))
        var n = 0
        fun put(b: Byte) {
            if (n == out.size) out = out.copyOf(out.size * 2)
            out[n++] = b
        }
        var i = 0
        while (i < src.size) {
            val c = src[i++].toInt() and 0xFF
            when {
                c in 1..8 -> repeat(c) { if (i < src.size) put(src[i++]) }
                c < 0x80 -> put(c.toByte())
                c >= 0xC0 -> {
                    put(' '.code.toByte())
                    put((c xor 0x80).toByte())
                }
                else -> {
                    if (i >= src.size) break
                    val pair = (c shl 8) or (src[i++].toInt() and 0xFF)
                    val dist = (pair shr 3) and 0x7FF
                    val len = (pair and 7) + 3
                    if (dist == 0 || dist > n) continue
                    repeat(len) { put(out[n - dist]) }
                }
            }
        }
        return out.copyOf(n)
    }
}
