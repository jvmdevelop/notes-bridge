package com.jvmd.notesbridge.formats.csv

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.io.TextDecoding
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.PageSetup
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.Table
import com.jvmd.notesbridge.model.TableCell
import com.jvmd.notesbridge.model.TableRow
import com.jvmd.notesbridge.model.TextRun

class CsvReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument {
        val text = TextDecoding.decode(input.readTextBytes())
        val parsed = CsvParser.parse(text, context.limits.maxTableCells, context.limits.maxTableColumns)
        if (parsed.truncated) context.warn(ConversionWarning.CONTENT_TRUNCATED)
        return toDocument(parsed.rows)
    }

    fun toDocument(rows: List<List<String>>): FlowDocument {
        val nonEmpty = rows.dropLastWhile { row -> row.all { it.isBlank() } }
        if (nonEmpty.isEmpty()) return FlowDocument(emptyList())
        val columns = nonEmpty.maxOf { it.size }
        val tableRows =
            nonEmpty.map { row ->
                TableRow(
                    List(columns) { index ->
                        val value = row.getOrNull(index).orEmpty()
                        TableCell(listOf(Paragraph(if (value.isEmpty()) emptyList() else listOf(TextRun(value)))))
                    },
                )
            }
        val page =
            if (columns >
                PORTRAIT_COLUMN_LIMIT
            ) {
                PageSetup.A4_LANDSCAPE
            } else {
                PageSetup.A4_PORTRAIT.copy(marginStartPt = 36f, marginEndPt = 36f)
            }
        return FlowDocument(listOf(Table(tableRows, headerRowCount = if (nonEmpty.size > 1) 1 else 0, compact = true)), page)
    }

    private companion object {
        const val PORTRAIT_COLUMN_LIMIT = 6
    }
}

object CsvParser {
    data class Result(
        val rows: List<List<String>>,
        val truncated: Boolean,
    )

    private val CANDIDATES = charArrayOf(',', ';', '\t', '|')

    fun detectDelimiter(text: String): Char {
        val sampleLines =
            text
                .lineSequence()
                .filter { it.isNotBlank() }
                .take(20)
                .toList()
        if (sampleLines.isEmpty()) return ','
        var best = ','
        var bestScore = -1.0
        for (candidate in CANDIDATES) {
            val counts = sampleLines.map { countOutsideQuotes(it, candidate) }
            val minimum = counts.minOrNull() ?: 0
            if (minimum == 0 && counts.all { it == 0 }) continue
            val consistent = counts.count { it == counts.first() }.toDouble() / counts.size
            val score = consistent * 10 + counts.average().coerceAtMost(50.0) / 10 + if (minimum > 0) 5 else 0
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun countOutsideQuotes(
        line: String,
        delimiter: Char,
    ): Int {
        var inQuotes = false
        var count = 0
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> count++
            }
        }
        return count
    }

    fun parse(
        text: String,
        maxCells: Int,
        maxColumns: Int,
        delimiter: Char = detectDelimiter(text),
    ): Result {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var cells = 0
        var truncated = false
        var index = 0
        val content = text.removePrefix("")

        fun endField() {
            if (row.size < maxColumns) row.add(field.toString()) else truncated = true
            field.setLength(0)
            cells++
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = ArrayList()
        }
        while (index < content.length) {
            val char = content[index]
            if (inQuotes) {
                if (char == '"') {
                    if (index + 1 < content.length && content[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(char)
                }
            } else {
                when (char) {
                    '"' -> {
                        if (field.isEmpty()) inQuotes = true else field.append(char)
                    }

                    delimiter -> {
                        endField()
                    }

                    '\r' -> {
                        if (index + 1 < content.length && content[index + 1] == '\n') index++
                        endRow()
                    }

                    '\n' -> {
                        endRow()
                    }

                    else -> {
                        field.append(char)
                    }
                }
            }
            if (cells >= maxCells) {
                truncated = true
                break
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return Result(rows, truncated)
    }
}
