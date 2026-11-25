package com.jvmd.notesbridge.render

import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.jvmd.notesbridge.convert.ConversionException
import java.io.Closeable
import java.io.OutputStream
import kotlin.math.roundToInt

class PdfSink(
    private val maxPages: Int,
    private val onPageStarted: (Int) -> Unit = {},
) : Closeable {
    private val document = PdfDocument()
    private var currentPage: PdfDocument.Page? = null

    var pageCount: Int = 0
        private set

    fun beginPage(
        widthPt: Float,
        heightPt: Float,
    ): Canvas {
        endPage()
        if (pageCount >= maxPages) throw ConversionException.TooComplex("more than $maxPages pages")
        pageCount++
        val info =
            PdfDocument.PageInfo
                .Builder(widthPt.roundToInt().coerceAtLeast(1), heightPt.roundToInt().coerceAtLeast(1), pageCount)
                .create()
        val page = document.startPage(info)
        currentPage = page
        onPageStarted(pageCount)
        return page.canvas.apply { scale(1f / UNITS_PER_POINT, 1f / UNITS_PER_POINT) }
    }

    fun endPage() {
        currentPage?.let { document.finishPage(it) }
        currentPage = null
    }

    fun writeTo(output: OutputStream) {
        endPage()
        document.writeTo(output)
    }

    override fun close() {
        currentPage = null
        document.close()
    }

    companion object {
        const val UNITS_PER_POINT = 4f
    }
}

internal val Float.units: Float get() = this * PdfSink.UNITS_PER_POINT
