package com.jvmd.notesbridge.io

import org.junit.Assert.assertEquals
import org.junit.Test

class PartPathsTest {
    @Test
    fun resolvesRelativeAndAbsoluteTargets() {
        assertEquals("ppt/media/image1.png", PartPaths.resolve("ppt/slides", "../media/image1.png"))
        assertEquals("word/media/a.png", PartPaths.resolve("word", "media/a.png"))
        assertEquals("customXml/item.xml", PartPaths.resolve("word", "/customXml/item.xml"))
        assertEquals("OEBPS/images/x.png", PartPaths.resolve("OEBPS/text", "../images/x.png#frag"))
        assertEquals("a.png", PartPaths.resolve("", "../../a.png"))
    }

    @Test
    fun decodesUrlEncodedNames() {
        assertEquals("Pictures/my image.png", PartPaths.decode("Pictures/my%20image.png"))
        assertEquals("a+b.png", PartPaths.decode("a+b.png"))
    }
}
