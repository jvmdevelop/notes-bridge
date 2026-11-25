package com.jvmd.notesbridge.formats

import com.jvmd.notesbridge.formats.csv.CsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {
    @Test
    fun parsesQuotedFieldsAndEmbeddedNewlines() {
        val result = CsvParser.parse("a,\"b,1\",\"line\nbreak\"\r\n\"say \"\"hi\"\"\",,x\n", 1000, 100)
        assertEquals(listOf(listOf("a", "b,1", "line\nbreak"), listOf("say \"hi\"", "", "x")), result.rows)
    }

    @Test
    fun detectsDelimiters() {
        assertEquals(';', CsvParser.detectDelimiter("a;b;c\n1;2;3\n"))
        assertEquals('\t', CsvParser.detectDelimiter("a\tb\n1\t2\n"))
        assertEquals(',', CsvParser.detectDelimiter("name,price\n\"x;y\",2\n"))
    }

    @Test
    fun truncatesAtCellLimit() {
        val result = CsvParser.parse("1,2,3\n4,5,6\n7,8,9\n", 4, 100)
        assertTrue(result.truncated)
        assertTrue(result.rows.sumOf { it.size } <= 5)
    }

    @Test
    fun limitsColumns() {
        val result = CsvParser.parse("1,2,3,4\n", 100, 2)
        assertEquals(listOf(listOf("1", "2")), result.rows)
        assertTrue(result.truncated)
    }
}
