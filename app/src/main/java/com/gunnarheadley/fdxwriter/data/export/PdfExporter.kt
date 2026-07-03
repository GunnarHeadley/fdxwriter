package com.gunnarheadley.fdxwriter.data.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.gunnarheadley.fdxwriter.data.fdx.ElementType
import com.gunnarheadley.fdxwriter.data.fdx.ScreenplayParagraph
import java.io.OutputStream

/**
 * Renders the screenplay to a US-Letter PDF in the standard Courier 12pt screenplay layout
 * (1.5" left margin, 1" elsewhere, ~55 lines per page). Element indents and the right-aligned
 * transitions approximate the industry-standard page.
 */
object PdfExporter {

    private const val PAGE_W = 612 // 8.5" * 72
    private const val PAGE_H = 792 // 11" * 72
    private const val LEFT = 108f // 1.5"
    private const val RIGHT = 540f // page width - 1"
    private const val TOP = 90f // first baseline ~1.25" down
    private const val BOTTOM = 720f // page height - 1"
    private const val LINE = 12f // 6 lines per inch
    private const val CHAR_W = 7.2f // Courier 12pt = 10 chars per inch

    private fun leftFor(type: ElementType): Float = when (type) {
        ElementType.CHARACTER -> 252f // ~3.5"
        ElementType.PARENTHETICAL -> 216f // ~3.0"
        ElementType.DIALOGUE -> 180f // ~2.5"
        else -> LEFT
    }

    fun export(paragraphs: List<ScreenplayParagraph>, out: OutputStream, title: String) {
        val paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            color = Color.BLACK
            isAntiAlias = true
        }
        val doc = PdfDocument()
        var pageNo = 0

        // Optional title page: title centred, author line below.
        if (title.isNotBlank()) {
            pageNo++
            val tp = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            val t = title.uppercase()
            tp.canvas.drawText(t, ((PAGE_W - t.length * CHAR_W) / 2f).coerceAtLeast(LEFT), PAGE_H * 0.4f, paint)
            doc.finishPage(tp)
        }

        pageNo++
        var bodyPage = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var canvas = page.canvas
        var y = TOP

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            bodyPage++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = TOP
            val num = "$bodyPage."
            canvas.drawText(num, RIGHT - num.length * CHAR_W, TOP - LINE * 1.5f, paint)
        }
        fun drawLeft(text: String, x: Float) {
            if (y > BOTTOM) newPage()
            canvas.drawText(text, x, y, paint)
            y += LINE
        }

        for (p in paragraphs) {
            var text = p.plainText.trim()
            if (p.type.displaysUppercase) text = text.uppercase()
            val lead = when (p.type) {
                ElementType.SCENE_HEADING, ElementType.ACTION,
                ElementType.CHARACTER, ElementType.TRANSITION,
                -> 1
                else -> 0
            }
            repeat(lead) { if (y <= BOTTOM) y += LINE }
            if (text.isEmpty()) continue

            val lines = wrap(text, p.type.columnWidth)
            if (p.type == ElementType.TRANSITION) {
                for (ln in lines) {
                    if (y > BOTTOM) newPage()
                    canvas.drawText(ln, (RIGHT - ln.length * CHAR_W).coerceAtLeast(LEFT), y, paint)
                    y += LINE
                }
            } else {
                val x = leftFor(p.type)
                for (ln in lines) drawLeft(ln, x)
            }
        }

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    /** Greedy word-wrap to [maxChars] columns, hard-breaking any word longer than the column. */
    internal fun wrap(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val lines = ArrayList<String>()
        val sb = StringBuilder()
        for (word in text.split(" ")) {
            when {
                sb.isEmpty() -> sb.append(word)
                sb.length + 1 + word.length <= maxChars -> sb.append(' ').append(word)
                else -> {
                    lines.add(sb.toString())
                    sb.setLength(0)
                    sb.append(word)
                }
            }
            while (sb.length > maxChars) {
                lines.add(sb.substring(0, maxChars))
                val rest = sb.substring(maxChars)
                sb.setLength(0)
                sb.append(rest)
            }
        }
        if (sb.isNotEmpty()) lines.add(sb.toString())
        return lines
    }
}
