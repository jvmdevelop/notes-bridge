package com.jvmd.notesbridge.formats

import com.jvmd.notesbridge.TestSupport
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.formats.epub.EpubReader
import com.jvmd.notesbridge.formats.html.HtmlFlowBuilder
import com.jvmd.notesbridge.formats.html.HtmlReader
import com.jvmd.notesbridge.formats.markdown.MarkdownReader
import com.jvmd.notesbridge.formats.text.PlainTextReader
import com.jvmd.notesbridge.model.FontFamily
import com.jvmd.notesbridge.model.ImageBlock
import com.jvmd.notesbridge.model.PageBreak
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.ParagraphRole
import com.jvmd.notesbridge.model.Table
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupReadersTest {
    @Test
    fun readsMarkdownStructure() {
        val context = TestSupport.context()
        val document = TestSupport.read(MarkdownReader(), "sample.md", DocumentFormat.MARKDOWN, context)
        val paragraphs = document.blocks.filterIsInstance<Paragraph>()
        assertEquals(ParagraphRole.HEADING, paragraphs.first().role)
        assertEquals("Meeting notes", paragraphs.first().plainText)
        assertTrue(paragraphs.flatMap { it.runs }.any { it.text == "release plan" && it.style.bold == true })
        assertTrue(paragraphs.flatMap { it.runs }.any { it.text == "drafts" && it.style.strikethrough })
        assertEquals(listOf("•", "•", "◦", "1.", "2."), paragraphs.mapNotNull { it.listMarker })
        val table = document.blocks.filterIsInstance<Table>().single()
        assertEquals(1, table.headerRowCount)
        assertEquals(3, table.rows.size)
        val code = paragraphs.single { it.role == ParagraphRole.CODE }
        assertTrue(code.plainText.contains("println"))
        assertTrue(code.runs.all { it.style.family == FontFamily.MONO })
        assertTrue(paragraphs.any { it.role == ParagraphRole.QUOTE })
        assertTrue(ConversionWarning.EXTERNAL_RESOURCES_SKIPPED in context.warnings)
    }

    @Test
    fun readsHtmlAndSkipsScripts() {
        val context = TestSupport.context()
        val html =
            """
            <html><head><style>p{color:red}</style><script>alert('x')</script></head>
            <body><p style="font-weight:bold">Bold <span style="font-style: italic">mixed</span></p>
            <ul><li>One<ul><li>Two</li></ul></li></ul>
            <p hidden>secret</p><div style="display:none">hidden</div>
            <img src="data:image/png;base64,iVBORw0KGgo=" width="40">
            <img src="https://example.com/a.png"><script>document.write('bad')</script>
            <table><tr><th>H</th></tr><tr><td>V</td></tr></table></body></html>
            """.trimIndent()
        val blocks = HtmlFlowBuilder(context).build(Jsoup.parse(html).body())
        val text = TestSupport.allText(blocks)
        assertFalse(text.contains("alert") || text.contains("bad") || text.contains("secret") || text.contains("hidden"))
        val first = blocks.first() as Paragraph
        assertEquals("Bold mixed", first.plainText)
        assertTrue(first.runs.all { it.style.bold == true })
        assertTrue(
            first.runs
                .last()
                .style.italic,
        )
        assertEquals(listOf("•", "◦"), blocks.filterIsInstance<Paragraph>().mapNotNull { it.listMarker })
        assertEquals(30f, (blocks.filterIsInstance<ImageBlock>().single().widthPt ?: 0f), 0.1f)
        assertTrue(ConversionWarning.EXTERNAL_RESOURCES_SKIPPED in context.warnings)
        assertEquals(1, blocks.filterIsInstance<Table>().single().headerRowCount)
    }

    @Test
    fun readsLibreOfficeHtml() {
        val document = TestSupport.read(HtmlReader(), "sample.html", DocumentFormat.HTML)
        val text = TestSupport.allText(document.blocks)
        assertTrue(text.contains("Quarterly Report"))
        assertTrue(text.contains("Region"))
        val items = document.blocks.filterIsInstance<Paragraph>().filter { it.listMarker != null }
        assertEquals(
            listOf("First bullet point", "Second bullet point", "Nested bullet", "Numbered one", "Numbered two"),
            items.map {
                it.plainText
            },
        )
    }

    @Test
    fun keepsListMarkerWithParagraphInsideListItem() {
        val blocks = HtmlFlowBuilder(null).build(Jsoup.parse("<ul>\n<li>\n<p>Wrapped item</p>\n</li>\n</ul>").body())
        val paragraph = blocks.single() as Paragraph
        assertEquals("•", paragraph.listMarker)
        assertEquals("Wrapped item", paragraph.plainText)
    }

    @Test
    fun readsEpubChaptersAndImages() {
        TestSupport.inspect(EpubReader(), "sample.epub", DocumentFormat.EPUB) { document ->
            val headings =
                document.blocks
                    .filterIsInstance<Paragraph>()
                    .filter { it.role == ParagraphRole.HEADING }
                    .map { it.plainText }
            assertEquals(listOf("Chapter One", "Chapter Two"), headings)
            assertEquals(1, document.blocks.count { it is PageBreak })
            val image = document.blocks.filterIsInstance<ImageBlock>().single()
            assertTrue(
                image.source
                    .open()
                    .use { it.readBytes() }
                    .size > 100,
            )
            assertTrue(TestSupport.allText(document.blocks).contains("bright cold day\u00A0in April"))
        }
    }

    @Test
    fun rejectsDrmProtectedEpub() {
        assertThrows(ConversionException.DrmProtected::class.java) { TestSupport.read(EpubReader(), "drm.epub", DocumentFormat.EPUB) }
    }

    @Test
    fun readsPlainTextLines() {
        val document = TestSupport.read(PlainTextReader(), "sample.txt", DocumentFormat.PLAIN_TEXT)
        val lines = document.blocks.filterIsInstance<Paragraph>().map { it.plainText }
        assertEquals(listOf("Plain text line one", "Строка на кириллице", "", "After blank line"), lines)
    }

    @Test
    fun emptyTextHasNoBlocks() {
        assertTrue(PlainTextReader().parse("\n\n").blocks.isEmpty())
    }
}
