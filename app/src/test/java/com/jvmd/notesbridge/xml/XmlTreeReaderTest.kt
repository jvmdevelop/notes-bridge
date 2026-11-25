package com.jvmd.notesbridge.xml

import com.jvmd.notesbridge.convert.ConversionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

class XmlTreeReaderTest {
    private val reader = XmlTreeReader({ KXmlParser() }, 1_000)

    @Test
    fun buildsNamespacedTree() {
        val root = reader.read("""<a:root xmlns:a="urn:a" a:id="1"><a:child>text</a:child></a:root>""".byteInputStream())
        assertEquals("root", root.name)
        assertEquals("1", root.attr("urn:a", "id"))
        assertEquals("text", root.child("urn:a", "child")?.textContent())
    }

    @Test
    fun doesNotExpandEntitiesFromDocumentTypeDeclaration() {
        val laughs =
            """
            <?xml version="1.0"?>
            <!DOCTYPE lolz [
              <!ENTITY lol "lol">
              <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
              <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
            ]>
            <lolz>&lol3;</lolz>
            """.trimIndent()
        assertThrows(ConversionException.Corrupted::class.java) { reader.read(laughs.byteInputStream()) }
    }

    @Test
    fun disablesDoctypeProcessingOnParser() {
        val parser = reader.newParser("<a/>".byteInputStream())
        assertEquals(false, parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL))
    }

    @Test
    fun enforcesElementLimit() {
        val xml = "<r>" + "<e/>".repeat(2_000) + "</r>"
        assertThrows(ConversionException.TooComplex::class.java) { reader.read(xml.byteInputStream()) }
    }
}
