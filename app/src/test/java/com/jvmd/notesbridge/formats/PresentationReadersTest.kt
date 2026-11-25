package com.jvmd.notesbridge.formats

import com.jvmd.notesbridge.TestSupport
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.formats.odf.OdpReader
import com.jvmd.notesbridge.formats.pptx.PptxReader
import com.jvmd.notesbridge.model.PictureElement
import com.jvmd.notesbridge.model.ShapeElement
import com.jvmd.notesbridge.model.ShapeOutline
import com.jvmd.notesbridge.model.SlideDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationReadersTest {
    private fun texts(
        deck: SlideDeck,
        slide: Int,
    ): List<String> =
        deck.slides[slide].elements.filterIsInstance<ShapeElement>().mapNotNull { shape ->
            shape.text?.paragraphs?.joinToString("\n") { paragraph -> paragraph.runs.joinToString("") { it.text } }
        }

    private fun assertDeck(
        deck: SlideDeck,
        expectedShapeFill: Int,
    ) {
        assertEquals(2, deck.slides.size)
        assertEquals(793.7f, deck.widthPt, 2f)
        assertEquals(446.5f, deck.heightPt, 2f)
        assertTrue(texts(deck, 0).toString(), texts(deck, 0).any { it.contains("NotesBridge Demo Deck") })
        val second = texts(deck, 1)
        assertTrue(second.toString(), second.any { it.contains("Agenda") })
        val agenda =
            deck.slides[1]
                .elements
                .filterIsInstance<ShapeElement>()
                .flatMap { it.text?.paragraphs.orEmpty() }
        val bulleted = agenda.filter { paragraph -> paragraph.runs.any { it.text.contains("Convert it to PDF") } }
        assertEquals(1, bulleted.size)
        assertTrue("bullet expected, got ${bulleted.first().bullet}", bulleted.first().bullet != null)
        val title =
            deck.slides[0]
                .elements
                .filterIsInstance<ShapeElement>()
                .flatMap {
                    it.text?.paragraphs.orEmpty()
                }.flatMap { it.runs }
                .first { it.text.contains("Demo") }
        assertEquals(44f, title.style.fontSizePt ?: 0f, 0.5f)
        assertTrue(title.style.color != null)
        val shape =
            deck.slides[1].elements.filterIsInstance<ShapeElement>().single { element ->
                element.text?.paragraphs?.any { paragraph ->
                    paragraph.runs.any {
                        it.text ==
                            "Shape text"
                    }
                } ==
                    true
            }
        assertEquals(expectedShapeFill, shape.fillColor)
        assertTrue(shape.outline is ShapeOutline.RoundedRectangle)
        assertTrue("picture expected", deck.slides[1].elements.any { it is PictureElement })
    }

    @Test
    fun readsPptx() {
        assertDeck(TestSupport.read(PptxReader(), "sample.pptx", DocumentFormat.PPTX), 0xFF18A303.toInt())
    }

    @Test
    fun readsOdp() {
        assertDeck(TestSupport.read(OdpReader(), "sample.odp", DocumentFormat.ODP), 0xFFFF9900.toInt())
    }
}
