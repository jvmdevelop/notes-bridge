package com.jvmd.notesbridge.formats

import com.jvmd.notesbridge.formats.xlsx.NumberFormatKind
import com.jvmd.notesbridge.formats.xlsx.XlsxNumberFormats
import org.junit.Assert.assertEquals
import org.junit.Test

class XlsxNumberFormatsTest {
    @Test
    fun formatsGeneralNumbers() {
        assertEquals("42", XlsxNumberFormats.general(42.0))
        assertEquals("0.1", XlsxNumberFormats.general(0.1 + 0.0000000000000001))
        assertEquals("3.3333333333", XlsxNumberFormats.general(10.0 / 3))
    }

    @Test
    fun classifiesAndFormatsBuiltIns() {
        assertEquals("2026-03-15", XlsxNumberFormats.format("46096", XlsxNumberFormats.classify(14, null), false))
        assertEquals("1,234.50", XlsxNumberFormats.format("1234.5", XlsxNumberFormats.classify(4, null), false))
        assertEquals("12.5%", XlsxNumberFormats.format("0.125", XlsxNumberFormats.classifyCode("0.0%"), false))
        assertEquals("13:30", XlsxNumberFormats.format("0.5625", XlsxNumberFormats.classify(20, null), false))
    }

    @Test
    fun ignoresQuotedLettersInCustomFormats() {
        assertEquals(NumberFormatKind.Fixed(2, false), XlsxNumberFormats.classifyCode("0.00\" days\""))
        assertEquals(NumberFormatKind.DateTime(true, false, false), XlsxNumberFormats.classifyCode("[\$-409]d mmm yyyy"))
    }

    @Test
    fun handles1904DateSystem() {
        assertEquals("1904-01-02", XlsxNumberFormats.format("1", NumberFormatKind.DateTime(true, false, false), true))
    }
}
