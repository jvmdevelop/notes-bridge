package com.jvmd.notesbridge.formats.odf

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.common.SheetCell
import com.jvmd.notesbridge.formats.common.SheetDocuments
import com.jvmd.notesbridge.formats.common.SheetGrid
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.xml.Namespaces.OFFICE
import com.jvmd.notesbridge.xml.Namespaces.STYLE
import com.jvmd.notesbridge.xml.Namespaces.TABLE
import com.jvmd.notesbridge.xml.Namespaces.TEXT
import com.jvmd.notesbridge.xml.XmlElement
import com.jvmd.notesbridge.xml.XmlText

class OdsReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument {
        val content =
            input.archive.open("content.xml")?.use { context.xml.read(it) } ?: throw ConversionException.Corrupted("missing content.xml")
        val styles = OdfStyles(input.archive.open("styles.xml")?.use { context.xml.read(it) }, content)
        val spreadsheet =
            content.path(OFFICE, "body", "spreadsheet") ?: throw ConversionException.Corrupted("document has no spreadsheet body")
        val sheets = ArrayList<SheetGrid>()
        var budget = context.limits.maxTableCells
        for (table in spreadsheet.children(TABLE, "table")) {
            context.checkCancelled()
            if (isHidden(table, content)) {
                context.warn(ConversionWarning.HIDDEN_CONTENT_SKIPPED)
                continue
            }
            if (budget <= 0) {
                context.warn(ConversionWarning.CONTENT_TRUNCATED)
                break
            }
            val grid = SheetGrid(table.attr(TABLE, "name").orEmpty())
            val reader = TableReader(grid, styles, context, budget)
            reader.readContainer(table)
            budget -= grid.cellCount
            sheets.add(grid)
        }
        return SheetDocuments.build(sheets)
    }

    private fun isHidden(
        table: XmlElement,
        content: XmlElement,
    ): Boolean {
        val styleName = table.attr(TABLE, "style-name") ?: return false
        val style =
            content.child(OFFICE, "automatic-styles")?.elements?.firstOrNull {
                it.matches(STYLE, "style") && it.attr(STYLE, "name") == styleName
            }
        return style?.child(STYLE, "table-properties")?.attr(TABLE, "display") == "false"
    }

    private class TableReader(
        private val grid: SheetGrid,
        private val styles: OdfStyles,
        private val context: ConversionContext,
        private val budget: Int,
    ) {
        private var row = 0
        private var stopped = false

        fun readContainer(container: XmlElement) {
            for (element in container.elements) {
                if (stopped) return
                when {
                    element.matches(TABLE, "table-row") -> readRow(element)

                    element.matches(TABLE, "table-header-rows") || element.matches(TABLE, "table-rows") ||
                        element.matches(TABLE, "table-row-group") -> readContainer(element)
                }
            }
        }

        private fun readRow(element: XmlElement) {
            val repeat = element.attr(TABLE, "number-rows-repeated")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val cells = element.elements.filter { it.matches(TABLE, "table-cell") || it.matches(TABLE, "covered-table-cell") }.toList()
            val hasContent = cells.any { cellText(it).isNotEmpty() }
            if (!hasContent) {
                row += repeat
                return
            }
            val effectiveRepeat = repeat.coerceAtMost(MAX_CONTENT_REPEAT)
            for (copy in 0 until effectiveRepeat) {
                row++
                var column = 0
                for (cell in cells) {
                    val columnRepeat = cell.attr(TABLE, "number-columns-repeated")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val text = cellText(cell)
                    if (text.isEmpty()) {
                        column += columnRepeat
                        continue
                    }
                    val bold = styles.text("table-cell", cell.attr(TABLE, "style-name")).bold == true
                    val numeric = cell.attr(OFFICE, "value-type") in NUMERIC_TYPES
                    for (index in 0 until columnRepeat.coerceAtMost(MAX_CONTENT_REPEAT)) {
                        column++
                        if (column > context.limits.maxTableColumns) {
                            context.warn(ConversionWarning.CONTENT_TRUNCATED)
                            break
                        }
                        grid.put(row, column, SheetCell(text, bold, if (numeric) Alignment.END else Alignment.START))
                        if (grid.cellCount >= budget) {
                            context.warn(ConversionWarning.CONTENT_TRUNCATED)
                            stopped = true
                            return
                        }
                    }
                }
                if (row % 500 == 0) context.checkCancelled()
            }
            row += repeat - effectiveRepeat
        }

        private fun cellText(cell: XmlElement): String {
            val paragraphs = cell.children(TEXT, "p").map { paragraphText(it) }.toList()
            if (paragraphs.isNotEmpty()) return paragraphs.joinToString("\n").trim()
            return cell.attr(OFFICE, "value") ?: cell.attr(OFFICE, "date-value") ?: cell.attr(OFFICE, "string-value") ?: ""
        }

        private fun paragraphText(element: XmlElement): String {
            val builder = StringBuilder()

            fun walk(node: XmlElement) {
                for (child in node.children) {
                    when (child) {
                        is XmlText -> {
                            builder.append(child.text)
                        }

                        is XmlElement -> {
                            when {
                                child.matches(
                                    TEXT,
                                    "s",
                                ) -> builder.append(" ".repeat((child.attr(TEXT, "c")?.toIntOrNull() ?: 1).coerceIn(1, 200)))

                                child.matches(TEXT, "tab") -> builder.append('\t')

                                child.matches(TEXT, "line-break") -> builder.append('\n')

                                child.matches(OFFICE, "annotation") -> Unit

                                else -> walk(child)
                            }
                        }
                    }
                }
            }
            walk(element)
            return builder.toString()
        }

        private companion object {
            const val MAX_CONTENT_REPEAT = 1000
            val NUMERIC_TYPES = setOf("float", "percentage", "currency")
        }
    }
}
