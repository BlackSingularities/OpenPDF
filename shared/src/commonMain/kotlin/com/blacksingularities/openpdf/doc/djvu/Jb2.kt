package com.blacksingularities.openpdf.doc.djvu

/** Kształt JB2: bitmapa [w]×[h], wiersze od dołu (wiersz 0 to dolna krawędź), 1 = czarny. */
internal class Jb2Shape(val w: Int, val h: Int, val bits: ByteArray) {
    operator fun get(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else bits[y * w + x].toInt()
}

/** Prostokąt zajęty przez czarne piksele kształtu (współrzędne włącznie). */
internal class LibRect(val left: Int, val bottom: Int, val right: Int, val top: Int) {
    companion object {
        fun of(s: Jb2Shape): LibRect {
            var right = s.w - 1
            while (right >= 0 && (0 until s.h).none { s[right, it] != 0 }) right--
            var top = s.h - 1
            while (top >= 0 && (0 until s.w).none { s[it, top] != 0 }) top--
            var left = 0
            while (left <= right && (0 until s.h).none { s[left, it] != 0 }) left++
            var bottom = 0
            while (bottom <= top && (0 until s.w).none { s[it, bottom] != 0 }) bottom++
            return LibRect(left, bottom, right, top)
        }
    }
}

/** Słownik kształtów (Djbz) albo kształty strony. */
internal class Jb2Dict(val shapes: MutableList<Jb2Shape> = ArrayList(), val boxes: MutableList<LibRect> = ArrayList())

/** Strona JB2: wymiary i lista odbić kształtów. */
internal class Jb2Image(val width: Int, val height: Int, val shapes: List<Jb2Shape>, val blits: IntArray, val blitCount: Int, val blitColors: IntArray?)

/**
 * Dekoder JB2 (specyfikacja DjVu v3, rozdz. „JB2 Bilevel Image Compression”).
 * Liczby kodowane są drzewem binarnym kontekstów rozwijanym w trakcie dekodowania.
 */
internal class Jb2Decoder(data: ByteArray, from: Int, to: Int, private val inherited: Jb2Dict?) {
    private val zp = ZpDecoder(data, from, to)

    // Komórki drzewa liczb: kontekst bitu, lewe i prawe dziecko.
    private var cellBit = IntArray(1024)
    private var cellLeft = IntArray(1024)
    private var cellRight = IntArray(1024)
    private var cells = 1

    // Korzenie poszczególnych rozkładów (indeksy komórek; 0 = jeszcze nieutworzona).
    private val roots = IntArray(R_COUNT)

    private val bitdist = IntArray(1024)
    private val cbitdist = IntArray(2048)
    private val offsetType = IntArray(1)
    private val refinementFlag = IntArray(1)

    private val shapes = ArrayList<Jb2Shape>()
    private val lib = ArrayList<Int>()          // numer w bibliotece → numer kształtu
    private val libBox = ArrayList<LibRect>()
    private var gotStart = false
    private var width = 0
    private var height = 0

    private var lastLeft = 0
    private var lastRight = 0
    private var lastBottom = 0
    private var lastRowLeft = 0
    private var lastRowBottom = 0
    private val shortList = IntArray(3)
    private var shortPos = 0

    private var blits = IntArray(3 * 256)
    private var blitCount = 0

    private fun resetNumbers() {
        roots.fill(0)
        cells = 1
    }

    private fun num(low0: Int, high0: Int, root: Int): Int {
        var low = low0
        var high = high0
        var negative = false
        var cutoff = 0
        var phase = 1
        var range = -1
        // Bieżąca „wskazówka”: korzeń albo dziecko komórki.
        var holderKind = 0 // 0 = korzeń, 1 = lewe dziecko, 2 = prawe dziecko
        var holder = root
        while (range != 1) {
            var cell = when (holderKind) { 0 -> roots[holder]; 1 -> cellLeft[holder]; else -> cellRight[holder] }
            if (cell == 0) {
                if (cells >= cellBit.size) grow()
                cell = cells++
                cellBit[cell] = 0; cellLeft[cell] = 0; cellRight[cell] = 0
                when (holderKind) { 0 -> roots[holder] = cell; 1 -> cellLeft[holder] = cell; else -> cellRight[holder] = cell }
            }
            val decision = low >= cutoff || (high >= cutoff && zp.decode(cellBit, cell) != 0)
            holderKind = if (decision) 2 else 1
            holder = cell
            when (phase) {
                1 -> {
                    negative = !decision
                    if (negative) {
                        val t = -low - 1
                        low = -high - 1
                        high = t
                    }
                    phase = 2
                    cutoff = 1
                }
                2 -> if (!decision) {
                    phase = 3
                    range = (cutoff + 1) / 2
                    if (range == 1) cutoff = 0 else cutoff -= range / 2
                } else {
                    cutoff += cutoff + 1
                }
                else -> {
                    range /= 2
                    if (range != 1) {
                        if (!decision) cutoff -= range / 2 else cutoff += range / 2
                    } else if (!decision) {
                        cutoff--
                    }
                }
            }
            if (cells > 5_000_000) throw DjvuException("Uszkodzone dane JB2")
        }
        return if (negative) -cutoff - 1 else cutoff
    }

    private fun grow() {
        val n = cellBit.size * 2
        cellBit = cellBit.copyOf(n); cellLeft = cellLeft.copyOf(n); cellRight = cellRight.copyOf(n)
    }

    private fun fillShort(v: Int) { shortList.fill(v); shortPos = 0 }

    private fun updateShort(v: Int): Int {
        if (++shortPos == 3) shortPos = 0
        val s = shortList
        s[shortPos] = v
        return if (s[0] >= s[1]) {
            if (s[0] > s[2]) (if (s[1] >= s[2]) s[1] else s[2]) else s[0]
        } else {
            if (s[0] < s[2]) (if (s[1] >= s[2]) s[2] else s[1]) else s[0]
        }
    }

    private fun initLibrary() {
        val d = inherited ?: return
        for (i in d.shapes.indices) {
            lib += i
            libBox += d.boxes.getOrNull(i) ?: LibRect.of(d.shapes[i])
        }
    }

    private fun shape(n: Int): Jb2Shape {
        val inh = inherited?.shapes?.size ?: 0
        return if (n < inh) inherited!!.shapes[n] else shapes.getOrNull(n - inh) ?: throw DjvuException("Uszkodzone dane JB2")
    }

    private fun addShape(s: Jb2Shape): Int {
        shapes += s
        return (inherited?.shapes?.size ?: 0) + shapes.size - 1
    }

    private fun addLibrary(shapeNo: Int, s: Jb2Shape) {
        lib += shapeNo
        libBox += LibRect.of(s)
    }

    private fun markSize(): Pair<Int, Int> {
        val w = num(0, BIG_POS, R_ABS_SIZE_X)
        val h = num(0, BIG_POS, R_ABS_SIZE_Y)
        check16(w, h)
        return w to h
    }

    private fun relMarkSize(cw: Int, ch: Int): Pair<Int, Int> {
        val w = cw + num(BIG_NEG, BIG_POS, R_REL_SIZE_X)
        val h = ch + num(BIG_NEG, BIG_POS, R_REL_SIZE_Y)
        check16(w, h)
        return w to h
    }

    private fun check16(w: Int, h: Int) {
        if (w !in 0..0xFFFF || h !in 0..0xFFFF) throw DjvuException("Uszkodzone dane JB2")
    }

    private fun direct(w: Int, h: Int): Jb2Shape {
        val stride = w + 4
        val buf = ByteArray(stride * (h + 2))
        for (y in h - 1 downTo 0) {
            val up2 = (y + 2) * stride + 2
            val up1 = (y + 1) * stride + 2
            val up0 = y * stride + 2
            var ctx = (buf[up2 - 1].toInt() shl 9) or (buf[up2].toInt() shl 8) or (buf[up2 + 1].toInt() shl 7) or
                (buf[up1 - 2].toInt() shl 6) or (buf[up1 - 1].toInt() shl 5) or (buf[up1].toInt() shl 4) or
                (buf[up1 + 1].toInt() shl 3) or (buf[up1 + 2].toInt() shl 2) or
                (buf[up0 - 2].toInt() shl 1) or buf[up0 - 1].toInt()
            var x = 0
            while (x < w) {
                val n = zp.decode(bitdist, ctx)
                buf[up0 + x] = n.toByte()
                x++
                ctx = ((ctx shl 1) and 0x37a) or (buf[up1 + x + 2].toInt() shl 2) or (buf[up2 + x + 1].toInt() shl 7) or n
            }
        }
        return crop(buf, stride, w, h)
    }

    private fun crop(buf: ByteArray, stride: Int, w: Int, h: Int): Jb2Shape {
        val bits = ByteArray(w * h)
        for (y in 0 until h) buf.copyInto(bits, y * w, y * stride + 2, y * stride + 2 + w)
        return Jb2Shape(w, h, bits)
    }

    private fun cross(w: Int, h: Int, ref: Jb2Shape, box: LibRect): Jb2Shape {
        val xd2c = (w / 2 - w + 1) - ((box.right - box.left + 1) / 2 - box.right)
        val yd2c = (h / 2 - h + 1) - ((box.top - box.bottom + 1) / 2 - box.top)
        if (xd2c !in -15..15 || yd2c !in -15..15) throw DjvuException("Uszkodzone dane JB2")
        val stride = w + 4
        val buf = ByteArray(stride * (h + 2))
        var cy = h - 1 + yd2c
        for (y in h - 1 downTo 0) {
            val up1 = (y + 1) * stride + 2
            val up0 = y * stride + 2
            val r1 = cy + 1
            val r0 = cy
            val rd = cy - 1
            fun c(row: Int, col: Int) = ref[col + xd2c, row]
            var ctx = (buf[up1 - 1].toInt() shl 10) or (buf[up1].toInt() shl 9) or (buf[up1 + 1].toInt() shl 8) or
                (buf[up0 - 1].toInt() shl 7) or (c(r1, 0) shl 6) or
                (c(r0, -1) shl 5) or (c(r0, 0) shl 4) or (c(r0, 1) shl 3) or
                (c(rd, -1) shl 2) or (c(rd, 0) shl 1) or c(rd, 1)
            var x = 0
            while (x < w) {
                val n = zp.decode(cbitdist, ctx)
                buf[up0 + x] = n.toByte()
                x++
                ctx = ((ctx shl 1) and 0x636) or (buf[up1 + x + 1].toInt() shl 8) or (c(r1, x) shl 6) or
                    (c(r0, x + 1) shl 3) or c(rd, x + 1) or (n shl 7)
            }
            cy--
        }
        return crop(buf, stride, w, h)
    }

    private fun relativeLocation(rows: Int, columns: Int): IntArray {
        if (!gotStart) throw DjvuException("Uszkodzone dane JB2")
        val left: Int
        val bottom: Int
        if (zp.decode(offsetType, 0) != 0) {
            left = lastRowLeft + num(BIG_NEG, BIG_POS, R_REL_X_LAST)
            val top = lastRowBottom + num(BIG_NEG, BIG_POS, R_REL_Y_LAST)
            bottom = top - rows + 1
            lastLeft = left; lastRowLeft = left
            lastRight = left + columns - 1
            lastBottom = bottom; lastRowBottom = bottom
            fillShort(bottom)
        } else {
            left = lastRight + num(BIG_NEG, BIG_POS, R_REL_X_CUR)
            bottom = lastBottom + num(BIG_NEG, BIG_POS, R_REL_Y_CUR)
            lastLeft = left
            lastRight = left + columns - 1
            lastBottom = updateShort(bottom)
        }
        return intArrayOf(left - 1, bottom - 1)
    }

    private fun addBlit(left: Int, bottom: Int, shapeNo: Int) {
        if (blitCount * 3 + 3 > blits.size) blits = blits.copyOf(blits.size * 2)
        blits[blitCount * 3] = left
        blits[blitCount * 3 + 1] = bottom
        blits[blitCount * 3 + 2] = shapeNo
        blitCount++
    }

    private fun matchIndex(): Int {
        val m = num(0, lib.size - 1, R_MATCH)
        if (m !in lib.indices) throw DjvuException("Uszkodzone dane JB2")
        return m
    }

    /** Dekoduje strumień; [dict] = true dla słownika Djbz (bez odbić i wymiarów strony). */
    private fun run(dict: Boolean) {
        while (true) {
            val type = num(START, END, R_RECORD)
            var newShape: Jb2Shape? = null
            var toLibrary = false
            var blit: IntArray? = null
            when (type) {
                START -> {
                    width = num(0, BIG_POS, R_IMAGE_SIZE)
                    height = num(0, BIG_POS, R_IMAGE_SIZE)
                    if (!dict && (width == 0 || height == 0)) throw DjvuException("Strona JB2 bez wymiarów")
                    lastLeft = if (dict) 1 else 1 + width
                    lastRowLeft = 0
                    lastRowBottom = if (dict) 0 else height
                    lastRight = 0
                    fillShort(lastRowBottom)
                    gotStart = true
                    zp.decode(refinementFlag, 0)
                    initLibrary()
                }
                NEW_MARK, NEW_MARK_LIBRARY_ONLY, NEW_MARK_IMAGE_ONLY -> {
                    val (w, h) = markSize()
                    newShape = direct(w, h)
                    toLibrary = type != NEW_MARK_IMAGE_ONLY
                    if (type != NEW_MARK_LIBRARY_ONLY) blit = relativeLocation(h, w)
                }
                MATCHED_REFINE, MATCHED_REFINE_LIBRARY_ONLY, MATCHED_REFINE_IMAGE_ONLY -> {
                    val m = matchIndex()
                    val ref = shape(lib[m])
                    val box = libBox[m]
                    val (w, h) = relMarkSize(box.right - box.left + 1, box.top - box.bottom + 1)
                    // Tak jak w DjVuLibre: odmiana „tylko do biblioteki” na stronie nie niesie bitmapy.
                    newShape = if (type == MATCHED_REFINE_LIBRARY_ONLY && !dict) Jb2Shape(w, h, ByteArray(w * h)) else cross(w, h, ref, box)
                    toLibrary = type != MATCHED_REFINE_IMAGE_ONLY
                    if (type != MATCHED_REFINE_LIBRARY_ONLY) blit = relativeLocation(h, w)
                }
                MATCHED_COPY -> {
                    val m = matchIndex()
                    val box = libBox[m]
                    val loc = relativeLocation(box.top - box.bottom + 1, box.right - box.left + 1)
                    addBlit(loc[0] - box.left, loc[1] - box.bottom, lib[m])
                }
                NON_MARK_DATA -> {
                    val (w, h) = markSize()
                    newShape = direct(w, h)
                    val left = num(1, width, R_ABS_LOC_X)
                    val top = num(1, height, R_ABS_LOC_Y)
                    blit = intArrayOf(left - 1, top - h)
                }
                PRESERVED_COMMENT -> {
                    val n = num(0, BIG_POS, R_COMMENT_LENGTH)
                    repeat(n) { num(0, 255, R_COMMENT_BYTE) }
                }
                REQUIRED_DICT_OR_RESET -> if (!gotStart) {
                    val n = num(0, BIG_POS, R_INHERITED)
                    if (n != (inherited?.shapes?.size ?: 0)) throw DjvuException("Brak wymaganego słownika kształtów")
                } else {
                    resetNumbers()
                }
                END -> return
                else -> throw DjvuException("Uszkodzone dane JB2")
            }
            if (newShape != null) {
                val no = addShape(newShape)
                if (toLibrary) addLibrary(no, newShape)
                if (blit != null) addBlit(blit[0], blit[1], no)
            }
        }
    }

    fun decodeDict(): Jb2Dict {
        run(dict = true)
        val all = ArrayList<Jb2Shape>()
        inherited?.let { all += it.shapes }
        all += shapes
        val boxes = ArrayList<LibRect>()
        inherited?.let { d -> d.shapes.indices.forEach { boxes += d.boxes.getOrNull(it) ?: LibRect.of(d.shapes[it]) } }
        for (s in shapes) boxes += LibRect.of(s)
        return Jb2Dict(all, boxes)
    }

    fun decodeImage(): Jb2Image {
        run(dict = false)
        val all = ArrayList<Jb2Shape>()
        inherited?.let { all += it.shapes }
        all += shapes
        return Jb2Image(width, height, all, blits, blitCount, null)
    }

    private companion object {
        const val BIG_POS = 262142
        const val BIG_NEG = -262143

        const val START = 0
        const val NEW_MARK = 1
        const val NEW_MARK_LIBRARY_ONLY = 2
        const val NEW_MARK_IMAGE_ONLY = 3
        const val MATCHED_REFINE = 4
        const val MATCHED_REFINE_LIBRARY_ONLY = 5
        const val MATCHED_REFINE_IMAGE_ONLY = 6
        const val MATCHED_COPY = 7
        const val NON_MARK_DATA = 8
        const val REQUIRED_DICT_OR_RESET = 9
        const val PRESERVED_COMMENT = 10
        const val END = 11

        const val R_RECORD = 0
        const val R_MATCH = 1
        const val R_ABS_LOC_X = 2
        const val R_ABS_LOC_Y = 3
        const val R_ABS_SIZE_X = 4
        const val R_ABS_SIZE_Y = 5
        const val R_IMAGE_SIZE = 6
        const val R_INHERITED = 7
        const val R_REL_X_CUR = 8
        const val R_REL_X_LAST = 9
        const val R_REL_Y_CUR = 10
        const val R_REL_Y_LAST = 11
        const val R_REL_SIZE_X = 12
        const val R_REL_SIZE_Y = 13
        const val R_COMMENT_LENGTH = 14
        const val R_COMMENT_BYTE = 15
        const val R_COUNT = 16
    }
}
