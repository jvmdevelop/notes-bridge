package com.jvmd.notesbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jvmd.notesbridge.InstrumentedSupport.app
import com.jvmd.notesbridge.InstrumentedSupport.fixtureUri
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionResult
import com.jvmd.notesbridge.convert.IncomingDocument
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.detect.UnsupportedReason
import com.jvmd.notesbridge.io.readUpTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EndToEndConversionTest {
    private fun convert(vararg names: String): ConversionResult =
        runBlocking { app.conversionManager.convert(names.map { IncomingDocument.Stream(fixtureUri(it), null) }) {} }

    private fun assertValidPdf(
        name: String,
        format: DocumentFormat,
        minimumPages: Int,
        exactPages: Int? = null,
    ) {
        val result = convert(name)
        assertEquals(listOf(format), result.formats)
        assertTrue(result.file.exists())
        assertEquals("%PDF", result.file.inputStream().use { String(it.readUpTo(4)) })
        val inspection = InstrumentedSupport.inspectPdf(result.file)
        assertEquals(result.pageCount, inspection.pageCount)
        assertTrue("$name produced ${inspection.pageCount} pages", inspection.pageCount >= minimumPages)
        exactPages?.let { assertEquals(name, it, inspection.pageCount) }
        assertTrue("$name first page looks blank (${inspection.firstPageInk})", inspection.firstPageInk > 0.001)
        InstrumentedSupport.archiveForInspection(result.file, "${name.replace('.', '_')}.pdf")
    }

    @Test
    fun convertsImages() {
        listOf(
            "photo.png" to DocumentFormat.PNG,
            "photo.jpg" to DocumentFormat.JPEG,
            "photo.webp" to DocumentFormat.WEBP,
            "photo.bmp" to DocumentFormat.BMP,
        ).forEach { (name, format) ->
            assertValidPdf(name, format, 1, 1)
        }
    }

    @Test
    fun imagePageFollowsImageAspectRatio() {
        val result = convert("photo.png")
        val inspection = InstrumentedSupport.inspectPdf(result.file)
        assertEquals(842, inspection.firstPageWidth)
        assertEquals(561, inspection.firstPageHeight)
    }

    @Test
    fun convertsSvg() = assertValidPdf("sample.svg", DocumentFormat.SVG, 1, 1)

    @Test
    fun convertsPlainText() = assertValidPdf("sample.txt", DocumentFormat.PLAIN_TEXT, 1, 1)

    @Test
    fun convertsCsv() = assertValidPdf("sample.csv", DocumentFormat.CSV, 1, 1)

    @Test
    fun convertsMarkdown() = assertValidPdf("sample.md", DocumentFormat.MARKDOWN, 1)

    @Test
    fun convertsHtml() = assertValidPdf("sample.html", DocumentFormat.HTML, 1)

    @Test
    fun convertsRtf() = assertValidPdf("sample.rtf", DocumentFormat.RTF, 2)

    @Test
    fun convertsDocx() = assertValidPdf("sample.docx", DocumentFormat.DOCX, 2)

    @Test
    fun convertsOdt() = assertValidPdf("sample.odt", DocumentFormat.ODT, 2)

    @Test
    fun convertsXlsx() = assertValidPdf("sample.xlsx", DocumentFormat.XLSX, 2)

    @Test
    fun convertsOds() = assertValidPdf("sample.ods", DocumentFormat.ODS, 2)

    @Test
    fun convertsPptx() = assertValidPdf("sample.pptx", DocumentFormat.PPTX, 2, 2)

    @Test
    fun convertsOdp() = assertValidPdf("sample.odp", DocumentFormat.ODP, 2, 2)

    @Test
    fun convertsEpub() = assertValidPdf("sample.epub", DocumentFormat.EPUB, 2)

    @Test
    fun mergesSeveralDocumentsIntoOnePdf() {
        val result = convert("photo.png", "photo.jpg", "sample.pptx")
        assertEquals(4, result.pageCount)
        assertEquals(4, InstrumentedSupport.inspectPdf(result.file).pageCount)
        assertTrue(result.file.name.startsWith("photo +2"))
    }

    @Test
    fun convertsSharedText() {
        val result =
            runBlocking { app.conversionManager.convert(listOf(IncomingDocument.Text("Shared from a browser\nSecond line", "Article"))) {} }
        assertEquals("Article.pdf", result.file.name)
        assertEquals(1, result.pageCount)
    }

    private fun assertUnsupported(
        name: String,
        reason: UnsupportedReason,
    ) {
        try {
            convert(name)
            fail("$name should not convert")
        } catch (error: ConversionException.Unsupported) {
            assertEquals(reason, error.reason)
        }
    }

    @Test
    fun rejectsLegacyAndUnsupportedFormats() {
        assertUnsupported("sample.doc", UnsupportedReason.LEGACY_OFFICE)
        assertUnsupported("photo.tiff", UnsupportedReason.TIFF)
    }

    @Test
    fun rejectsDrmEpub() {
        try {
            convert("drm.epub")
            fail("DRM EPUB should not convert")
        } catch (_: ConversionException.DrmProtected) {
        }
    }

    @Test
    fun removesTemporaryInputsAfterConversion() {
        convert("sample.docx")
        assertTrue(
            app.fileManager.inputDirectory
                .listFiles()
                .orEmpty()
                .isEmpty(),
        )
    }

    @Test
    fun cancellationStopsConversionAndRemovesOutput() {
        val large = File(InstrumentedSupport.targetContext.cacheDir, "large.txt")
        large.bufferedWriter().use { writer ->
            repeat(120_000) { writer.write("Line $it of a long document that keeps going and going.\n") }
        }
        val outputsBefore =
            app.fileManager.outputDirectory
                .listFiles()
                .orEmpty()
                .map { it.name }
                .toSet()
        runBlocking {
            val job =
                async { app.conversionManager.convert(listOf(IncomingDocument.Stream(android.net.Uri.fromFile(large), "text/plain"))) {} }
            delay(400)
            job.cancel()
            try {
                job.await()
                fail("conversion should have been cancelled")
            } catch (_: CancellationException) {
            }
        }
        val outputsAfter =
            app.fileManager.outputDirectory
                .listFiles()
                .orEmpty()
                .map { it.name }
                .toSet()
        assertEquals(outputsBefore, outputsAfter)
        assertTrue(
            app.fileManager.inputDirectory
                .listFiles()
                .orEmpty()
                .isEmpty(),
        )
        assertFalse(outputsAfter.any { it !in outputsBefore })
    }
}
