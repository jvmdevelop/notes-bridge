package com.jvmd.notesbridge.convert

import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.render.PdfSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConverterRegistryTest {
    private class FakeExporter(
        override val formats: Set<DocumentFormat>,
    ) : PdfExporter {
        override fun export(
            source: DocumentSource,
            sink: PdfSink,
            context: ConversionContext,
        ) = Unit
    }

    @Test
    fun rejectsDuplicateRegistrations() {
        assertThrows(IllegalArgumentException::class.java) {
            ConverterRegistry(listOf(FakeExporter(setOf(DocumentFormat.PNG)), FakeExporter(setOf(DocumentFormat.PNG))))
        }
    }

    @Test
    fun defaultRegistryCoversEveryDeclaredFormat() {
        assertEquals(DocumentFormat.entries.toSet(), DefaultConverters.registry().supportedFormats)
    }
}
