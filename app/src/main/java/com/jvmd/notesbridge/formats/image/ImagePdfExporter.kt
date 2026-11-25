package com.jvmd.notesbridge.formats.image

import android.graphics.Paint
import android.graphics.RectF
import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.DocumentSource
import com.jvmd.notesbridge.convert.PdfExporter
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.detect.UnsupportedReason
import com.jvmd.notesbridge.model.ImageSource
import com.jvmd.notesbridge.render.BitmapLoader
import com.jvmd.notesbridge.render.PdfSink
import com.jvmd.notesbridge.render.units

class ImagePdfExporter : PdfExporter {
    override val formats: Set<DocumentFormat> =
        setOf(
            DocumentFormat.PNG,
            DocumentFormat.JPEG,
            DocumentFormat.WEBP,
            DocumentFormat.GIF,
            DocumentFormat.BMP,
            DocumentFormat.HEIF,
            DocumentFormat.AVIF,
        )

    override fun export(
        source: DocumentSource,
        sink: PdfSink,
        context: ConversionContext,
    ) {
        val bitmap =
            BitmapLoader.load(ImageSource { source.file.inputStream() }, context.limits.maxStandaloneImagePx)
                ?: throw ConversionException.Unsupported(source.displayName, UnsupportedReason.CORRUPTED)
        try {
            context.checkCancelled()
            val landscape = bitmap.width > bitmap.height
            val pageWidth: Float
            val pageHeight: Float
            if (landscape) {
                pageWidth = LONG_SIDE_PT
                pageHeight = LONG_SIDE_PT * bitmap.height / bitmap.width
            } else {
                pageHeight = LONG_SIDE_PT
                pageWidth = LONG_SIDE_PT * bitmap.width / bitmap.height
            }
            val canvas = sink.beginPage(pageWidth, pageHeight)
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, pageWidth.units, pageHeight.units),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
            sink.endPage()
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val LONG_SIDE_PT = 842f
    }
}
