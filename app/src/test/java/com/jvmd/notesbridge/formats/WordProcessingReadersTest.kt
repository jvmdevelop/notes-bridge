package com.jvmd.notesbridge.formats

import com.jvmd.notesbridge.TestSupport
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.formats.docx.DocxReader
import com.jvmd.notesbridge.formats.odf.OdtReader
import com.jvmd.notesbridge.formats.rtf.RtfReader
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.ImageBlock
import com.jvmd.notesbridge.model.PageBreak
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.ParagraphRole
import com.jvmd.notesbridge.model.Table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordProcessingReadersTest {
    private fun assertCommonStructure(
        document: FlowDocument,
        expectHeadings: Boolean,
    ) {
        val text = TestSupport.allText(document.blocks)
        assertTrue(text, text.contains("Quarterly Report"))
        assertTrue(text, text.contains("съешь же ещё этих мягких французских булок"))
        assertTrue(text, text.contains("First bullet point"))
        assertTrue(text, text.contains("Nested bullet"))
        assertTrue(text, text.contains("Second page starts here."))
        val paragraphs = document.blocks.filterIsInstance<Paragraph>()
        val bold = paragraphs.flatMap { it.runs }.firstOrNull { it.text.contains("bold") }
        assertEquals(true, bold?.style?.bold)
        val italic = paragraphs.flatMap { it.runs }.firstOrNull { it.text.contains("italic") }
        assertEquals(true, italic?.style?.italic)
        val bullets = paragraphs.filter { it.listMarker != null }
        assertTrue("list markers: ${bullets.map { it.listMarker }}", bullets.size >= 5)
        assertTrue(bullets.any { it.listMarker == "1." } && bullets.any { it.listMarker == "2." })
        val table = document.blocks.filterIsInstance<Table>().single()
        assertEquals(3, table.rows.size)
        assertEquals(
            "Region",
            table.rows[0]
                .cells[0]
                .paragraphs
                .joinToString { it.plainText },
        )
        assertEquals(
            "950",
            table.rows[2]
                .cells[1]
                .paragraphs
                .joinToString { it.plainText },
        )
        assertTrue("image expected", document.blocks.any { it is ImageBlock })
        assertTrue("page break expected", document.blocks.any { it is PageBreak })
        if (expectHeadings) {
            val heading = paragraphs.first { it.plainText == "Quarterly Report" }
            assertEquals(ParagraphRole.HEADING, heading.role)
            assertEquals(1, heading.headingLevel)
        }
    }

    @Test
    fun readsDocx() {
        assertCommonStructure(TestSupport.read(DocxReader(), "sample.docx", DocumentFormat.DOCX), expectHeadings = true)
    }

    @Test
    fun readsOdt() {
        assertCommonStructure(TestSupport.read(OdtReader(), "sample.odt", DocumentFormat.ODT), expectHeadings = true)
    }

    @Test
    fun readsRtf() {
        assertCommonStructure(TestSupport.read(RtfReader(), "sample.rtf", DocumentFormat.RTF), expectHeadings = false)
    }

    @Test
    fun docxImageCarriesSize() {
        TestSupport.inspect(DocxReader(), "sample.docx", DocumentFormat.DOCX) { document ->
            val image = document.blocks.filterIsInstance<ImageBlock>().first()
            assertEquals(170f, image.widthPt ?: 0f, 2f)
            val bytes = image.source.open().use { it.readBytes() }
            assertTrue(bytes.size > 100)
        }
    }

    @Test
    fun docxPageSetupComesFromSection() {
        val document = TestSupport.read(DocxReader(), "sample.docx", DocumentFormat.DOCX)
        assertTrue(document.pageSetup.widthPt in 500f..700f)
        assertTrue(document.pageSetup.heightPt > document.pageSetup.widthPt)
    }
}
