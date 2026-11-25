package com.jvmd.notesbridge.formats.common

import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.FlowBlock
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.PageBreak
import com.jvmd.notesbridge.model.PageSetup
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.ParagraphRole
import com.jvmd.notesbridge.model.Table
import com.jvmd.notesbridge.model.TableCell
import com.jvmd.notesbridge.model.TableRow
import com.jvmd.notesbridge.model.TextRun
import com.jvmd.notesbridge.model.TextStyle
import java.util.TreeMap

class SheetGrid(
    val name: String,
) {
    private val cells = TreeMap<Int, TreeMap<Int, SheetCell>>()
    var cellCount: Int = 0
        private set

    fun put(
        row: Int,
        column: Int,
        cell: SheetCell,
    ) {
        if (cell.text.isEmpty()) return
        val previous = cells.getOrPut(row) { TreeMap() }.put(column, cell)
        if (previous == null) cellCount++
    }

    val isEmpty: Boolean get() = cellCount == 0

    fun toRows(): List<List<SheetCell?>> {
        if (cells.isEmpty()) return emptyList()
        val firstRow = cells.firstKey()
        val lastRow = cells.lastKey()
        val firstColumn = cells.values.minOf { it.firstKey() }
        val lastColumn = cells.values.maxOf { it.lastKey() }
        return (firstRow..lastRow).map { row ->
            val rowCells = cells[row]
            (firstColumn..lastColumn).map { column -> rowCells?.get(column) }
        }
    }
}

data class SheetCell(
    val text: String,
    val bold: Boolean = false,
    val alignment: Alignment = Alignment.START,
)

object SheetDocuments {
    fun build(sheets: List<SheetGrid>): FlowDocument {
        val visible = sheets.filterNot { it.isEmpty }
        val blocks = ArrayList<FlowBlock>()
        var maxColumns = 0
        for ((index, sheet) in visible.withIndex()) {
            if (index > 0) blocks.add(PageBreak)
            if (visible.size > 1 || sheet.name.isNotBlank()) {
                blocks.add(Paragraph(listOf(TextRun(sheet.name)), role = ParagraphRole.HEADING, headingLevel = 3))
            }
            val rows = sheet.toRows()
            maxColumns = maxOf(maxColumns, rows.firstOrNull()?.size ?: 0)
            blocks.add(
                Table(
                    rows =
                        rows.map { row ->
                            TableRow(
                                row.map { cell ->
                                    if (cell == null) {
                                        TableCell(listOf(Paragraph(emptyList())))
                                    } else {
                                        TableCell(
                                            listOf(
                                                Paragraph(
                                                    listOf(TextRun(cell.text, TextStyle(bold = if (cell.bold) true else null))),
                                                    alignment = cell.alignment,
                                                ),
                                            ),
                                        )
                                    }
                                },
                            )
                        },
                    compact = true,
                ),
            )
        }
        val page = if (maxColumns > 6) PageSetup.A4_LANDSCAPE else PageSetup.A4_PORTRAIT.copy(marginStartPt = 36f, marginEndPt = 36f)
        return FlowDocument(blocks, page)
    }
}
