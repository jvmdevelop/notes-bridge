package com.jvmd.notesbridge.formats.svg

import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.DocumentSource
import com.jvmd.notesbridge.convert.PdfExporter
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.render.PdfSink
import com.jvmd.notesbridge.render.SvgSizing
import com.jvmd.notesbridge.render.units
import kotlin.math.max

class SvgPdfExporter : PdfExporter {
    override val formats: Set<DocumentFormat> = setOf(DocumentFormat.SVG)

    override fun export(
        source: DocumentSource,
        sink: PdfSink,
        context: ConversionContext,
    ) {
        if (source.file.length() > context.limits.maxTextBytes) throw ConversionException.TooLarge(context.limits.maxTextBytes)
        val svg =
            try {
                source.file.inputStream().use { SVG.getFromInputStream(it) }
            } catch (error: SVGParseException) {
                throw ConversionException.Corrupted("invalid SVG", error)
            }
        val (width, height) = SvgSizing.intrinsicSize(svg) ?: throw ConversionException.Empty()
        val scale = LONG_SIDE_PT / max(width, height)
        val pageWidth = width * scale
        val pageHeight = height * scale
        context.checkCancelled()
        val canvas = sink.beginPage(pageWidth, pageHeight)
        svg.setDocumentWidth(pageWidth.units)
        svg.setDocumentHeight(pageHeight.units)
        svg.renderToCanvas(canvas)
        sink.endPage()
    }

    private companion object {
        const val LONG_SIDE_PT = 842f
    }
}
