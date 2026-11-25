package com.jvmd.notesbridge.formats.xlsx

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.common.OoxmlPackage
import com.jvmd.notesbridge.formats.common.SheetCell
import com.jvmd.notesbridge.formats.common.SheetDocuments
import com.jvmd.notesbridge.formats.common.SheetGrid
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.xml.Namespaces
import com.jvmd.notesbridge.xml.Namespaces.R
import com.jvmd.notesbridge.xml.Namespaces.S
import com.jvmd.notesbridge.xml.XmlElement
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class XlsxReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument {
        val pkg = OoxmlPackage(input.archive, context)
        val workbookPath = pkg.mainPart("officeDocument", "xl/workbook.xml")
        val workbook = pkg.xml(workbookPath) ?: throw ConversionException.Corrupted("missing $workbookPath")
        val relationships = pkg.relationships(workbookPath)
        val sharedStrings =
            relationships.values
                .firstOrNull {
                    it.hasType("sharedStrings") && !it.external
                }?.let { readSharedStrings(pkg, it.target, context) }
                .orEmpty()
        val styles = relationships.values.firstOrNull { it.hasType("styles") && !it.external }?.let { pkg.xml(it.target) }
        val formats = cellFormats(styles)
        val boldStyles = boldCellStyles(styles)
        val date1904 = workbook.child(S, "workbookPr")?.attr("date1904")?.let { it == "1" || it == "true" } ?: false
        val sheets = ArrayList<SheetGrid>()
        var totalCells = 0
        for (sheet in workbook.child(S, "sheets")?.children(S, "sheet").orEmpty()) {
            context.checkCancelled()
            val state = sheet.attr("state")
            if (state == "hidden" || state == "veryHidden") {
                context.warn(ConversionWarning.HIDDEN_CONTENT_SKIPPED)
                continue
            }
            val relationship = sheet.attr(R, "id")?.let { relationships[it] } ?: continue
            if (!relationship.hasType("worksheet")) {
                context.warn(ConversionWarning.EMBEDDED_OBJECTS_SKIPPED)
                continue
            }
            val grid = SheetGrid(sheet.attr("name").orEmpty())
            val budget = context.limits.maxTableCells - totalCells
            if (budget <= 0) {
                context.warn(ConversionWarning.CONTENT_TRUNCATED)
                break
            }
            readSheet(pkg, relationship.target, grid, SheetContext(sharedStrings, formats, boldStyles, date1904, budget), context)
            totalCells += grid.cellCount
            sheets.add(grid)
        }
        return SheetDocuments.build(sheets)
    }

    private class SheetContext(
        val sharedStrings: List<String>,
        val formats: List<NumberFormatKind>,
        val boldStyles: Set<Int>,
        val date1904: Boolean,
        val cellBudget: Int,
    )

    private fun cellFormats(styles: XmlElement?): List<NumberFormatKind> {
        if (styles == null) return emptyList()
        val custom =
            styles
                .child(S, "numFmts")
                ?.children(S, "numFmt")
                ?.associate { (it.attr("numFmtId")?.toIntOrNull() ?: -1) to it.attr("formatCode").orEmpty() }
                .orEmpty()
        return styles
            .child(S, "cellXfs")
            ?.children(S, "xf")
            ?.map { xf ->
                val id = xf.attr("numFmtId")?.toIntOrNull() ?: 0
                XlsxNumberFormats.classify(id, custom[id])
            }?.toList()
            .orEmpty()
    }

    private fun boldCellStyles(styles: XmlElement?): Set<Int> {
        if (styles == null) return emptySet()
        val boldFonts =
            styles
                .child(S, "fonts")
                ?.children(S, "font")
                ?.mapIndexedNotNull {
                    index,
                    font,
                    ->
                    font.child(S, "b")?.takeIf { it.attr("val") != "0" && it.attr("val") != "false" }?.let { index }
                }?.toSet()
                .orEmpty()
        return styles
            .child(S, "cellXfs")
            ?.children(S, "xf")
            ?.mapIndexedNotNull { index, xf -> if ((xf.attr("fontId")?.toIntOrNull() ?: -1) in boldFonts) index else null }
            ?.toSet()
            .orEmpty()
    }

    private fun readSharedStrings(
        pkg: OoxmlPackage,
        path: String,
        context: ConversionContext,
    ): List<String> {
        val result = ArrayList<String>()
        val stream = pkg.archive.open(path) ?: return result
        stream.use { input ->
            val parser = context.xml.newParser(input)
            val current = StringBuilder()
            var inItem = false
            var inText = false
            var phoneticDepth = 0
            try {
                while (true) {
                    when (parser.next()) {
                        XmlPullParser.START_TAG -> {
                            if (parser.namespace.normalized() == S) {
                                when (parser.name) {
                                    "si" -> {
                                        inItem = true
                                        current.setLength(0)
                                    }

                                    "t" -> {
                                        inText = inItem && phoneticDepth == 0
                                    }

                                    "rPh" -> {
                                        phoneticDepth++
                                    }
                                }
                            }
                        }

                        XmlPullParser.TEXT -> {
                            if (inText) current.append(parser.text)
                        }

                        XmlPullParser.END_TAG -> {
                            if (parser.namespace.normalized() == S) {
                                when (parser.name) {
                                    "si" -> {
                                        result.add(current.toString())
                                        inItem = false
                                        if (result.size % 5000 == 0) context.checkCancelled()
                                    }

                                    "t" -> {
                                        inText = false
                                    }

                                    "rPh" -> {
                                        phoneticDepth--
                                    }
                                }
                            }
                        }

                        XmlPullParser.END_DOCUMENT -> {
                            return result
                        }
                    }
                }
            } catch (error: XmlPullParserException) {
                throw ConversionException.Corrupted("invalid shared strings", error)
            } catch (error: IOException) {
                throw ConversionException.Corrupted("cannot read shared strings", error)
            }
        }
    }

    private fun readSheet(
        pkg: OoxmlPackage,
        path: String,
        grid: SheetGrid,
        sheet: SheetContext,
        context: ConversionContext,
    ) {
        val stream = pkg.archive.open(path) ?: return
        stream.use { input ->
            val parser = context.xml.newParser(input)
            var rowIndex = 0
            var columnIndex = 0
            var cellType: String? = null
            var cellStyle = 0
            var inValue = false
            var inInlineText = false
            val value = StringBuilder()
            var counted = 0
            try {
                while (true) {
                    when (parser.next()) {
                        XmlPullParser.START_TAG -> {
                            if (parser.namespace.normalized() != S) continue
                            when (parser.name) {
                                "row" -> {
                                    rowIndex = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowIndex + 1)
                                    columnIndex = 0
                                }

                                "c" -> {
                                    columnIndex = parser.getAttributeValue(null, "r")?.let { columnOf(it) } ?: (columnIndex + 1)
                                    cellType = parser.getAttributeValue(null, "t")
                                    cellStyle = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                                    value.setLength(0)
                                }

                                "v" -> {
                                    inValue = true
                                }

                                "t" -> {
                                    inInlineText = cellType == "inlineStr"
                                }
                            }
                        }

                        XmlPullParser.TEXT -> {
                            if (inValue || inInlineText) value.append(parser.text)
                        }

                        XmlPullParser.END_TAG -> {
                            if (parser.namespace.normalized() != S) continue
                            when (parser.name) {
                                "v" -> {
                                    inValue = false
                                }

                                "t" -> {
                                    inInlineText = false
                                }

                                "c" -> {
                                    val text = cellText(value.toString(), cellType, cellStyle, sheet)
                                    if (text.isNotEmpty()) {
                                        if (columnIndex > context.limits.maxTableColumns) {
                                            context.warn(ConversionWarning.CONTENT_TRUNCATED)
                                        } else {
                                            val numeric = cellType == null || cellType == "n"
                                            grid.put(
                                                rowIndex,
                                                columnIndex,
                                                SheetCell(
                                                    text,
                                                    cellStyle in sheet.boldStyles,
                                                    if (numeric &&
                                                        value.isNotEmpty()
                                                    ) {
                                                        Alignment.END
                                                    } else {
                                                        Alignment.START
                                                    },
                                                ),
                                            )
                                            counted++
                                            if (counted % 2000 == 0) context.checkCancelled()
                                            if (counted >= sheet.cellBudget) {
                                                context.warn(ConversionWarning.CONTENT_TRUNCATED)
                                                return
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        XmlPullParser.END_DOCUMENT -> {
                            return
                        }
                    }
                }
            } catch (error: XmlPullParserException) {
                throw ConversionException.Corrupted("invalid worksheet", error)
            } catch (error: IOException) {
                throw ConversionException.Corrupted("cannot read worksheet", error)
            }
        }
    }

    private fun cellText(
        raw: String,
        type: String?,
        style: Int,
        sheet: SheetContext,
    ): String =
        when (type) {
            "s" -> {
                raw
                    .trim()
                    .toIntOrNull()
                    ?.let { sheet.sharedStrings.getOrNull(it) }
                    .orEmpty()
            }

            "inlineStr", "str", "e", "d" -> {
                raw
            }

            "b" -> {
                if (raw.trim() == "1") {
                    "TRUE"
                } else if (raw.isEmpty()) {
                    ""
                } else {
                    "FALSE"
                }
            }

            else -> {
                if (raw.isEmpty()) {
                    ""
                } else {
                    XlsxNumberFormats.format(
                        raw.trim(),
                        sheet.formats.getOrElse(style) { NumberFormatKind.General },
                        sheet.date1904,
                    )
                }
            }
        }

    private fun String?.normalized(): String = Namespaces.normalize(this.orEmpty())

    companion object {
        fun columnOf(reference: String): Int {
            var result = 0
            for (char in reference) {
                if (!char.isLetter()) break
                result = result * 26 + (char.uppercaseChar() - 'A' + 1)
            }
            return result
        }
    }
}
