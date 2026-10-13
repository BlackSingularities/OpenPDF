package com.blacksingularities.openpdf.pdf

import android.graphics.BitmapFactory
import com.blacksingularities.openpdf.pdf.PdfOps.alpha
import com.blacksingularities.openpdf.pdf.PdfOps.blue
import com.blacksingularities.openpdf.pdf.PdfOps.floats
import com.blacksingularities.openpdf.pdf.PdfOps.green
import com.blacksingularities.openpdf.pdf.PdfOps.red
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.util.Matrix
import java.util.Calendar
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Zamienia oczekujący element ([Mark]) w adnotację PDF z własnym strumieniem wyglądu.
 * Treść wyglądu rysowana jest w układzie wyświetlanej strony (punkty, oś Y w górę), a macierz
 * [frame] przenosi ją do przestrzeni PDF – dzięki temu obrót strony nie wymaga osobnych przypadków.
 */
internal class AnnotationWriter(
    private val doc: PDDocument,
    private val geo: PageGeo,
    private val author: String,
    private val fonts: PdfOps.FontBox,
) {
    private val dW = geo.displayWidth
    private val dH = geo.displayHeight

    private val frame: Matrix = run {
        val (ox, oy) = geo.toPdf(PointN(0f, 1f))
        val (xx, xy) = geo.toPdf(PointN(1f, 1f))
        val (yx, yy) = geo.toPdf(PointN(0f, 0f))
        Matrix((xx - ox) / dW, (xy - oy) / dW, (yx - ox) / dH, (yy - oy) / dH, ox, oy)
    }

    private fun x(p: PointN) = p.x * dW
    private fun y(p: PointN) = (1f - p.y) * dH

    fun build(mark: Mark): PDAnnotation? = when (mark) {
        is InkMark -> ink(mark.strokes, mark.color, mark.widthPt, mark.highlighter, mark.bounds(), mark.page)
        is SignatureMark -> {
            val strokes = mark.strokes.map { s ->
                s.map { PointN(mark.rect.left + it.x * mark.rect.width, mark.rect.top + it.y * mark.rect.height) }
            }
            ink(strokes, mark.color, mark.widthPt, false, mark.rect.inflate(mark.widthPt / dW), mark.page)
        }
        is ShapeMark -> shape(mark)
        is TextMark -> freeText(mark)
        is NoteMark -> note(mark)
        is MarkupMark -> markup(mark)
        is ImageMark -> image(mark)
    }

    // ------------------------------------------------------------------ wspólne

    private fun base(subtype: String, bounds: RectN, color: Int?, contents: String?): Pair<COSDictionary, PDRectangle> {
        val r = geo.rectToPdf(bounds)
        val rect = PDRectangle(r[0], r[1], max(1f, r[2] - r[0]), max(1f, r[3] - r[1]))
        val d = COSDictionary()
        d.setItem(COSName.TYPE, COSName.ANNOT)
        d.setName(COSName.SUBTYPE, subtype)
        d.setItem(COSName.RECT, rect.cosArray)
        d.setInt(COSName.F, 4) // drukowanie
        d.setString(COSName.T, author)
        d.setString(COSName.getPDFName("NM"), "openpdf-" + UUID.randomUUID().toString())
        val now = Calendar.getInstance()
        d.setDate(COSName.M, now)
        d.setDate(COSName.CREATION_DATE, now)
        if (color != null) d.setItem(COSName.C, floats(red(color), green(color), blue(color)))
        if (!contents.isNullOrEmpty()) d.setString(COSName.CONTENTS, contents)
        return d to rect
    }

    private inline fun finish(d: COSDictionary, rect: PDRectangle, draw: (PDPageContentStream) -> Unit): PDAnnotation {
        val ap = PdfOps.newAppearance(doc, rect)
        PDPageContentStream(doc, ap).use { cs ->
            cs.transform(frame)
            draw(cs)
        }
        val annot = PDAnnotation.createAnnotation(d)
        annot.appearance = PdfOps.appearanceDict(ap)
        return annot
    }

    private fun PDPageContentStream.stroking(color: Int) = setStrokingColor(red(color), green(color), blue(color))
    private fun PDPageContentStream.filling(color: Int) = setNonStrokingColor(red(color), green(color), blue(color))

    /** Wygładzona ścieżka: krzywe przez środki kolejnych odcinków (jak na ekranie). */
    private fun PDPageContentStream.smoothPath(points: List<PointN>) {
        if (points.isEmpty()) return
        moveTo(x(points[0]), y(points[0]))
        if (points.size == 1) {
            lineTo(x(points[0]) + 0.01f, y(points[0]))
            return
        }
        if (points.size == 2) {
            lineTo(x(points[1]), y(points[1]))
            return
        }
        var px = x(points[0])
        var py = y(points[0])
        for (i in 1 until points.size - 1) {
            val cx = x(points[i])
            val cy = y(points[i])
            val mx = (cx + x(points[i + 1])) / 2f
            val my = (cy + y(points[i + 1])) / 2f
            // Krzywa kwadratowa (px,py)-(cx,cy)-(mx,my) zapisana jako sześcienna.
            curveTo(px + 2f / 3f * (cx - px), py + 2f / 3f * (cy - py), mx + 2f / 3f * (cx - mx), my + 2f / 3f * (cy - my), mx, my)
            px = mx
            py = my
        }
        lineTo(x(points.last()), y(points.last()))
    }

    // ------------------------------------------------------------------ rodzaje

    private fun ink(strokes: List<List<PointN>>, color: Int, widthPt: Float, highlighter: Boolean, bounds: RectN, page: Int): PDAnnotation? {
        if (strokes.isEmpty()) return null
        val (d, rect) = base("Ink", bounds, color, null)
        val inkList = COSArray()
        for (s in strokes) {
            val arr = COSArray()
            for (p in s) {
                val (px, py) = geo.toPdf(p)
                arr.add(COSFloat(px))
                arr.add(COSFloat(py))
            }
            inkList.add(arr)
        }
        d.setItem(COSName.getPDFName("InkList"), inkList)
        val bs = COSDictionary()
        bs.setFloat(COSName.W, widthPt)
        d.setItem(COSName.BS, bs)
        if (highlighter) d.setString(COSName.getPDFName("IT"), "InkHighlight")
        return finish(d, rect) { cs ->
            if (highlighter) cs.setGraphicsStateParameters(PdfOps.gsAlpha(0.45f, true))
            else if (alpha(color) < 1f) cs.setGraphicsStateParameters(PdfOps.gsAlpha(alpha(color), false))
            cs.stroking(color)
            cs.setLineWidth(widthPt)
            cs.setLineCapStyle(if (highlighter) 2 else 1)
            cs.setLineJoinStyle(1)
            for (s in strokes) {
                cs.smoothPath(s)
                cs.stroke()
            }
        }
    }

    private fun shape(m: ShapeMark): PDAnnotation {
        val subtype = when (m.kind) {
            ShapeKind.RECT -> "Square"
            ShapeKind.ELLIPSE -> "Circle"
            ShapeKind.LINE, ShapeKind.ARROW -> "Line"
        }
        val (d, rect) = base(subtype, m.bounds(), m.color, null)
        val bs = COSDictionary()
        bs.setFloat(COSName.W, m.widthPt)
        d.setItem(COSName.BS, bs)
        if (m.fill != null && (m.kind == ShapeKind.RECT || m.kind == ShapeKind.ELLIPSE)) {
            d.setItem(COSName.IC, floats(red(m.fill), green(m.fill), blue(m.fill)))
        }
        if (m.kind == ShapeKind.LINE || m.kind == ShapeKind.ARROW) {
            val (x1, y1) = geo.toPdf(m.start)
            val (x2, y2) = geo.toPdf(m.end)
            d.setItem(COSName.L, floats(x1, y1, x2, y2))
            val le = COSArray()
            le.add(COSName.getPDFName("None"))
            le.add(COSName.getPDFName(if (m.kind == ShapeKind.ARROW) "OpenArrow" else "None"))
            d.setItem(COSName.getPDFName("LE"), le)
        }
        return finish(d, rect) { cs ->
            if (alpha(m.color) < 1f) cs.setGraphicsStateParameters(PdfOps.gsAlpha(alpha(m.color), false))
            if (m.fill != null && alpha(m.fill) < 1f) {
                // Półprzezroczyste wypełnienie, nieprzezroczysty obrys.
                val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                gs.cosObject.setFloat(COSName.getPDFName("ca"), alpha(m.fill))
                cs.setGraphicsStateParameters(gs)
            }
            cs.stroking(m.color)
            cs.setLineWidth(m.widthPt)
            cs.setLineJoinStyle(1)
            cs.setLineCapStyle(1)
            val l = min(x(m.start), x(m.end))
            val r = max(x(m.start), x(m.end))
            val b = min(y(m.start), y(m.end))
            val t = max(y(m.start), y(m.end))
            when (m.kind) {
                ShapeKind.RECT -> {
                    cs.addRect(l, b, r - l, t - b)
                    if (m.fill != null) {
                        cs.filling(m.fill)
                        cs.fillAndStroke()
                    } else {
                        cs.stroke()
                    }
                }
                ShapeKind.ELLIPSE -> {
                    val cx = (l + r) / 2f
                    val cy = (b + t) / 2f
                    val rx = (r - l) / 2f
                    val ry = (t - b) / 2f
                    val k = 0.5523f
                    cs.moveTo(cx + rx, cy)
                    cs.curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)
                    cs.curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)
                    cs.curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)
                    cs.curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)
                    cs.closePath()
                    if (m.fill != null) {
                        cs.filling(m.fill)
                        cs.fillAndStroke()
                    } else {
                        cs.stroke()
                    }
                }
                ShapeKind.LINE, ShapeKind.ARROW -> {
                    val x1 = x(m.start)
                    val y1 = y(m.start)
                    val x2 = x(m.end)
                    val y2 = y(m.end)
                    cs.moveTo(x1, y1)
                    cs.lineTo(x2, y2)
                    cs.stroke()
                    if (m.kind == ShapeKind.ARROW) {
                        val h = PdfOps.arrowHead(x1, y1, x2, y2, max(8f, m.widthPt * 4f))
                        cs.moveTo(h[0], h[1])
                        cs.lineTo(x2, y2)
                        cs.lineTo(h[2], h[3])
                        cs.stroke()
                    }
                }
            }
        }
    }

    private fun freeText(m: TextMark): PDAnnotation {
        val font = fonts.font()
        val (d, rect) = base("FreeText", m.rect, null, m.text)
        val rgb = "%.3f %.3f %.3f rg".format(java.util.Locale.US, red(m.color), green(m.color), blue(m.color))
        d.setString(COSName.DA, "/Helv %.1f Tf %s".format(java.util.Locale.US, m.sizePt, rgb))
        val bs = COSDictionary()
        bs.setFloat(COSName.W, 0f)
        d.setItem(COSName.BS, bs)
        if (m.background != null) d.setItem(COSName.C, floats(red(m.background), green(m.background), blue(m.background)))
        return finish(d, rect) { cs ->
            val l = m.rect.left * dW
            val t = (1f - m.rect.top) * dH
            val w = m.rect.width * dW
            val h = m.rect.height * dH
            if (m.background != null) {
                cs.filling(m.background)
                cs.addRect(l, t - h, w, h)
                cs.fill()
            }
            cs.filling(m.color)
            val leading = m.sizePt * TEXT_LEADING
            var baseline = t - TEXT_PAD - m.sizePt * 0.92f
            for (line in m.text.split('\n')) {
                val safe = fonts.safe(font, line)
                if (safe.isNotEmpty()) {
                    cs.beginText()
                    cs.setFont(font, m.sizePt)
                    cs.newLineAtOffset(l + TEXT_PAD, baseline)
                    cs.showText(safe)
                    cs.endText()
                }
                baseline -= leading
            }
        }
    }

    private fun note(m: NoteMark): PDAnnotation {
        val (d, rect) = base("Text", m.bounds(), m.color, m.text)
        d.setName(COSName.NAME, "Comment")
        d.setBoolean(COSName.getPDFName("Open"), false)
        return finish(d, rect) { cs ->
            val b = m.bounds()
            PdfOps.drawNoteIcon(cs, PDRectangle(b.left * dW, (1f - b.bottom) * dH, b.width * dW, b.height * dH), m.color)
        }
    }

    private fun markup(m: MarkupMark): PDAnnotation {
        val subtype = when (m.kind) {
            MarkupKind.HIGHLIGHT -> "Highlight"
            MarkupKind.UNDERLINE -> "Underline"
            MarkupKind.STRIKEOUT -> "StrikeOut"
        }
        val (d, rect) = base(subtype, m.bounds(), m.color, m.text)
        val quads = COSArray()
        for (r in m.rects) {
            // Kolejność narożników jak w Acrobacie: lewy górny, prawy górny, lewy dolny, prawy dolny (w układzie wyświetlania).
            for (p in listOf(PointN(r.left, r.top), PointN(r.right, r.top), PointN(r.left, r.bottom), PointN(r.right, r.bottom))) {
                val (px, py) = geo.toPdf(p)
                quads.add(COSFloat(px))
                quads.add(COSFloat(py))
            }
        }
        d.setItem(COSName.getPDFName("QuadPoints"), quads)
        return finish(d, rect) { cs ->
            when (m.kind) {
                MarkupKind.HIGHLIGHT -> {
                    cs.setGraphicsStateParameters(PdfOps.gsAlpha(0.55f, true))
                    cs.filling(m.color)
                    for (r in m.rects) cs.addRect(r.left * dW, (1f - r.bottom) * dH, r.width * dW, r.height * dH)
                    cs.fill()
                }
                MarkupKind.UNDERLINE, MarkupKind.STRIKEOUT -> {
                    cs.stroking(m.color)
                    for (r in m.rects) {
                        val h = r.height * dH
                        cs.setLineWidth(max(0.7f, h * 0.075f))
                        val yy = if (m.kind == MarkupKind.UNDERLINE) (1f - r.bottom) * dH + h * 0.08f else (1f - r.centerY) * dH
                        cs.moveTo(r.left * dW, yy)
                        cs.lineTo(r.right * dW, yy)
                        cs.stroke()
                    }
                }
            }
        }
    }

    private fun image(m: ImageMark): PDAnnotation? {
        val bmp = BitmapFactory.decodeFile(m.path) ?: return null
        try {
            val img = if (bmp.hasAlpha()) LosslessFactory.createFromImage(doc, bmp) else JPEGFactory.createFromImage(doc, bmp, 0.9f)
            val (d, rect) = base("Stamp", m.rect, null, null)
            d.setName(COSName.NAME, "Image")
            return finish(d, rect) { cs ->
                cs.drawImage(img, m.rect.left * dW, (1f - m.rect.bottom) * dH, m.rect.width * dW, m.rect.height * dH)
            }
        } finally {
            bmp.recycle()
        }
    }

    companion object {
        const val TEXT_LEADING = 1.22f
        const val TEXT_PAD = 2f
    }
}
