package com.jvmd.notesbridge.convert

import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.render.PdfSink

interface PdfExporter {
    val formats: Set<DocumentFormat>

    fun export(
        source: DocumentSource,
        sink: PdfSink,
        context: ConversionContext,
    )
}
