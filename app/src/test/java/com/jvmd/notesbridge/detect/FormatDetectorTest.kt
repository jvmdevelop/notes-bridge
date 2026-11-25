package com.jvmd.notesbridge.detect

import com.jvmd.notesbridge.TestSupport.fixture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FormatDetectorTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val detector = FormatDetector()

    private fun detect(
        name: String,
        mime: String? = null,
        displayName: String? = name,
    ) = detector.detect(fixture(name), displayName, mime)

    @Test
    fun detectsFormatsBySignatureAndContainer() {
        val expected =
            mapOf(
                "photo.png" to DocumentFormat.PNG,
                "photo.jpg" to DocumentFormat.JPEG,
                "photo.webp" to DocumentFormat.WEBP,
                "photo.bmp" to DocumentFormat.BMP,
                "sample.docx" to DocumentFormat.DOCX,
                "sample.xlsx" to DocumentFormat.XLSX,
                "sample.pptx" to DocumentFormat.PPTX,
                "sample.odt" to DocumentFormat.ODT,
                "sample.ods" to DocumentFormat.ODS,
                "sample.odp" to DocumentFormat.ODP,
                "sample.epub" to DocumentFormat.EPUB,
                "sample.rtf" to DocumentFormat.RTF,
                "sample.svg" to DocumentFormat.SVG,
                "sample.html" to DocumentFormat.HTML,
                "sample.md" to DocumentFormat.MARKDOWN,
                "sample.csv" to DocumentFormat.CSV,
                "sample.txt" to DocumentFormat.PLAIN_TEXT,
                "cp1251.txt" to DocumentFormat.PLAIN_TEXT,
            )
        for ((name, format) in expected) {
            assertEquals(name, DetectionResult.Supported(format), detect(name))
        }
    }

    @Test
    fun contentWinsOverMisleadingNameForContainers() {
        assertEquals(DetectionResult.Supported(DocumentFormat.PPTX), detect("sample.pptx", "application/octet-stream", "download.bin"))
        assertEquals(DetectionResult.Supported(DocumentFormat.DOCX), detect("sample.docx", null, null))
    }

    @Test
    fun reportsUnsupportedFormatsWithReasons() {
        assertEquals(DetectionResult.Unsupported(UnsupportedReason.LEGACY_OFFICE), detect("sample.doc"))
        assertEquals(DetectionResult.Unsupported(UnsupportedReason.TIFF), detect("photo.tiff"))
        assertEquals(DetectionResult.Unsupported(UnsupportedReason.PASSWORD_PROTECTED), detect("sample.doc", displayName = "report.docx"))
    }

    @Test
    fun rejectsContentMismatchAndUnknownBinary() {
        val text = temporary.newFile("fake.docx").apply { writeText("not really a document") }
        assertEquals(DetectionResult.Unsupported(UnsupportedReason.CONTENT_MISMATCH), detector.detect(text, "fake.docx", null))
        val binary = temporary.newFile("blob").apply { writeBytes(ByteArray(512) { (it % 7).toByte() }) }
        assertEquals(DetectionResult.Unsupported(UnsupportedReason.UNKNOWN), detector.detect(binary, "blob", "application/octet-stream"))
        val pdf = temporary.newFile("a.pdf").apply { writeText("%PDF-1.7\n") }
        assertEquals(DetectionResult.Unsupported(UnsupportedReason.ALREADY_PDF), detector.detect(pdf, "a.pdf", "application/pdf"))
    }

    @Test
    fun textStartingWithBmLettersIsNotBitmap() {
        val text = temporary.newFile("notes.txt").apply { writeText("BMW service schedule and other notes for the car") }
        assertEquals(DetectionResult.Supported(DocumentFormat.PLAIN_TEXT), detector.detect(text, "notes.txt", "text/plain"))
    }

    @Test
    fun brokenZipIsCorrupted() {
        val broken = temporary.newFile("broken.docx").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)) }
        assertEquals(DetectionResult.Unsupported(UnsupportedReason.CORRUPTED), detector.detect(broken, "broken.docx", null))
    }
}
