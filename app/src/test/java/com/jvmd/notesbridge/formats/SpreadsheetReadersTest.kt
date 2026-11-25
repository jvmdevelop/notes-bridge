package com.jvmd.notesbridge.formats

import com.jvmd.notesbridge.TestSupport
import com.jvmd.notesbridge.convert.ConversionLimits
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.formats.csv.CsvReader
import com.jvmd.notesbridge.formats.odf.OdsReader
import com.jvmd.notesbridge.formats.xlsx.XlsxReader
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.ParagraphRole
import com.jvmd.notesbridge.model.Table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetReadersTest {
    private fun cells(table: Table): List<List<String>> =
        table.rows.map { row -> row.cells.map { cell -> cell.paragraphs.joinToString("") { it.plainText } } }

    private fun assertWorkbook(
        document: FlowDocument,
        percent: String,
    ) {
        val headings =
            document.blocks
                .filterIsInstance<Paragraph>()
                .filter { it.role == ParagraphRole.HEADING }
                .map { it.plainText }
        assertEquals(listOf("Sales", "Notes"), headings)
        val tables = document.blocks.filterIsInstance<Table>()
        assertEquals(2, tables.size)
        val sales = cells(tables[0])
        assertEquals(listOf("Product", "Units", "Date", "Share"), sales[0])
        assertEquals("1500", sales[1][1])
        assertEquals("2026-03-15", sales[1][2])
        assertEquals(percent, sales[1][3])
        assertEquals(3, sales.size)
        assertEquals(listOf(listOf("Second sheet")), cells(tables[1]))
        assertEquals(
            true,
            tables[0]
                .rows[0]
                .cells[0]
                .paragraphs[0]
                .runs[0]
                .style.bold,
        )
    }

    @Test
    fun readsXlsxWithNumberFormats() {
        assertWorkbook(TestSupport.read(XlsxReader(), "sample.xlsx", DocumentFormat.XLSX), "12.5%")
    }

    @Test
    fun readsOdsWithoutExpandingEmptyRepeats() {
        val started = System.nanoTime()
        val document = TestSupport.read(OdsReader(), "sample.ods", DocumentFormat.ODS)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
        val sales = cells(document.blocks.filterIsInstance<Table>()[0])
        assertEquals("1500", sales[1][1])
        assertEquals(3, sales.size)
    }

    @Test
    fun readsCsvWithDetectedDelimiter() {
        val document = TestSupport.read(CsvReader(), "sample.csv", DocumentFormat.CSV)
        val table = document.blocks.single() as Table
        assertEquals(1, table.headerRowCount)
        val rows = cells(table)
        assertEquals(listOf("Product", "Units", "Date", "Share"), rows[0])
        assertEquals("2,75", rows[2][1])
    }

    @Test
    fun truncatesLargeSheetsWithWarning() {
        val context = TestSupport.context(ConversionLimits(maxTableCells = 3))
        TestSupport.read(XlsxReader(), "sample.xlsx", DocumentFormat.XLSX, context)
        assertTrue(ConversionWarning.CONTENT_TRUNCATED in context.warnings)
    }
}
