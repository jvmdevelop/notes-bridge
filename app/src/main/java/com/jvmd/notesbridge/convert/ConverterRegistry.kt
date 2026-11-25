package com.jvmd.notesbridge.convert

import com.jvmd.notesbridge.detect.DocumentFormat

class ConverterRegistry(
    exporters: List<PdfExporter>,
) {
    private val byFormat: Map<DocumentFormat, PdfExporter>

    init {
        val mapping = LinkedHashMap<DocumentFormat, PdfExporter>()
        for (exporter in exporters) {
            for (format in exporter.formats) {
                require(mapping.put(format, exporter) == null) { "Format $format is registered more than once" }
            }
        }
        byFormat = mapping
    }

    val supportedFormats: Set<DocumentFormat> get() = byFormat.keys

    fun exporterFor(format: DocumentFormat): PdfExporter? = byFormat[format]
}
