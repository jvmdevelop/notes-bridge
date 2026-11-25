package com.jvmd.notesbridge.convert

import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.SlideDeck
import com.jvmd.notesbridge.render.FlowPdfRenderer
import com.jvmd.notesbridge.render.PdfSink
import com.jvmd.notesbridge.render.SlidePdfRenderer

class FlowPdfExporter(
    override val formats: Set<DocumentFormat>,
    private val reader: DocumentReader<FlowDocument>,
) : PdfExporter {
    override fun export(
        source: DocumentSource,
        sink: PdfSink,
        context: ConversionContext,
    ) {
        ReaderInput(source, context.limits).use { input ->
            val document = reader.read(input, context)
            context.checkCancelled()
            if (document.blocks.isEmpty()) throw ConversionException.Empty()
            val pages = FlowPdfRenderer(sink, context).render(document)
            if (pages == 0) throw ConversionException.Empty()
        }
    }
}

class SlidePdfExporter(
    override val formats: Set<DocumentFormat>,
    private val reader: DocumentReader<SlideDeck>,
) : PdfExporter {
    override fun export(
        source: DocumentSource,
        sink: PdfSink,
        context: ConversionContext,
    ) {
        ReaderInput(source, context.limits).use { input ->
            val deck = reader.read(input, context)
            context.checkCancelled()
            if (deck.slides.isEmpty()) throw ConversionException.Empty()
            SlidePdfRenderer(sink, context).render(deck)
        }
    }
}
