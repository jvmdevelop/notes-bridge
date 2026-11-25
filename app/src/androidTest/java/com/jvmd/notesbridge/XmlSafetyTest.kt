package com.jvmd.notesbridge

import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.xml.XmlTreeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class XmlSafetyTest {
    private val reader = XmlTreeReader({ Xml.newPullParser() }, 100_000)

    @Test
    fun platformParserHasDoctypeProcessingDisabled() {
        val parser = reader.newParser("<a/>".byteInputStream())
        assertFalse(parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL))
    }

    @Test
    fun platformParserDoesNotExpandInternalEntities() {
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
        try {
            val root = reader.read(laughs.byteInputStream())
            assertEquals("", root.textContent().replace("lol", "").trim())
            if (root.textContent().length > 10) fail("entities were expanded: ${root.textContent().length} characters")
        } catch (_: ConversionException.Corrupted) {
        }
    }
}
