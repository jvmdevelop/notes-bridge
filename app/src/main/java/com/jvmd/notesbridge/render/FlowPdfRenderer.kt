package com.jvmd.notesbridge.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.FontFamily
import com.jvmd.notesbridge.model.HorizontalRule
import com.jvmd.notesbridge.model.ImageBlock
import com.jvmd.notesbridge.model.PageBreak
import com.jvmd.notesbridge.model.PageSetup
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.ParagraphRole
import com.jvmd.notesbridge.model.Table
import com.jvmd.notesbridge.model.TableRow
import com.jvmd.notesbridge.model.TextRun
import kotlin.math.max
import kotlin.math.min

class FlowPdfRenderer(
    private val sink: PdfSink,
    private val context: ConversionContext,
) {
    private val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f.units
            color = BORDER_COLOR
        }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun render(document: FlowDocument): Int {
        val setup = if (document.pageSetup.isUsable()) document.pageSetup else PageSetup.A4_PORTRAIT
        val cursor = PageCursor(setup)
        val pagesBefore = sink.pageCount
        val blocks = document.blocks
        for ((index, block) in blocks.withIndex()) {
            context.checkCancelled()
            when (block) {
                is Paragraph -> renderParagraph(cursor, block, blocks.getOrNull(index + 1))
                is Table -> renderTable(cursor, block)
                is ImageBlock -> renderImage(cursor, block)
                PageBreak -> cursor.breakPage()
                HorizontalRule -> renderRule(cursor)
            }
        }
        cursor.finish()
        return sink.pageCount - pagesBefore
    }

    private inner class PageCursor(
        val setup: PageSetup,
    ) {
        val left = setup.marginStartPt.units
        val right = (setup.widthPt - setup.marginEndPt).units
        val top = setup.marginTopPt.units
        val bottom = (setup.heightPt - setup.marginBottomPt).units
        val width get() = right - left
        val height get() = bottom - top
        var canvas: Canvas? = null
            private set
        var y = top
        var onRepeatHeader: ((PageCursor) -> Unit)? = null

        val atPageTop: Boolean get() = canvas == null || y <= top + 0.5f
        val remaining: Float get() = if (canvas == null) height else bottom - y

        fun requireCanvas(): Canvas = canvas ?: newPage()

        fun newPage(): Canvas {
            val created = sink.beginPage(setup.widthPt, setup.heightPt)
            canvas = created
            y = top
            val repeat = onRepeatHeader
            if (repeat != null) {
                onRepeatHeader = null
                repeat(this)
                onRepeatHeader = repeat
            }
            return created
        }

        fun ensureSpace(needed: Float) {
            if (canvas == null) {
                newPage()
            } else if (y + needed > bottom && !atPageTop) {
                newPage()
            }
        }

        fun breakPage() {
            if (canvas != null && !atPageTop) {
                sink.endPage()
                canvas = null
                y = top
            }
        }

        fun finish() {
            sink.endPage()
            canvas = null
        }
    }

    private fun defaultsFor(paragraph: Paragraph): TextDefaults =
        when (paragraph.role) {
            ParagraphRole.HEADING -> {
                TextDefaults(
                    sizePt = HEADING_SIZES.getOrElse(paragraph.headingLevel.coerceAtLeast(1) - 1) { BODY_SIZE },
                    bold = true,
                )
            }

            ParagraphRole.CODE -> {
                TextDefaults(sizePt = CODE_SIZE, family = FontFamily.MONO)
            }

            ParagraphRole.QUOTE -> {
                TextDefaults(sizePt = BODY_SIZE, italic = true, color = QUOTE_COLOR)
            }

            ParagraphRole.CAPTION -> {
                TextDefaults(sizePt = CAPTION_SIZE, italic = true, color = QUOTE_COLOR)
            }

            ParagraphRole.BODY -> {
                TextDefaults(sizePt = BODY_SIZE)
            }
        }

    private fun renderParagraph(
        cursor: PageCursor,
        paragraph: Paragraph,
        next: Any?,
    ) {
        val defaults = defaultsFor(paragraph)
        val paint = TextRendering.paint(defaults)
        val baseLineHeight = paint.fontSpacing
        val spaceBefore = (paragraph.spaceBeforePt ?: defaultSpaceBefore(paragraph)).units
        val spaceAfter = (paragraph.spaceAfterPt ?: defaultSpaceAfter(paragraph)).units
        val decorationInset = if (paragraph.role == ParagraphRole.QUOTE || paragraph.role == ParagraphRole.CODE) 8f.units else 0f
        val marker = paragraph.listMarker
        val markerPaint = TextPaint(paint)
        val markerWidth = marker?.let { markerPaint.measureText(it) + 4f.units } ?: 0f
        val indent = min(max(paragraph.indentPt.units, markerWidth) + decorationInset, cursor.width * 0.6f)
        val text = TextRendering.spanned(paragraph.runs, defaults)
        val trimmedLength = text.trimEnd { it == '\n' || it == ' ' }.length
        val content = if (trimmedLength < text.length) text.subSequence(0, trimmedLength) else text
        if (content.isEmpty() && marker == null) {
            if (!cursor.atPageTop) cursor.y += baseLineHeight * (paragraph.lineSpacing ?: 1f)
            return
        }
        val layout =
            TextRendering.layout(
                content,
                paint,
                cursor.width - indent - (if (decorationInset > 0) 4f.units else 0f),
                paragraph.alignment,
                paragraph.lineSpacing ?: 1f,
            )
        if (!cursor.atPageTop) cursor.y += spaceBefore
        if (paragraph.role == ParagraphRole.HEADING && next != null) {
            val keepWithNext = min(layout.height + baseLineHeight * 3, cursor.height * 0.4f)
            cursor.ensureSpace(keepWithNext)
        }
        drawLayoutAcrossPages(cursor, layout, indent) { canvas, sliceTop, sliceHeight, isFirstSlice ->
            when (paragraph.role) {
                ParagraphRole.CODE -> {
                    fillPaint.color = CODE_BACKGROUND
                    canvas.drawRect(
                        cursor.left + indent - decorationInset + 2f.units,
                        sliceTop - 2f.units,
                        cursor.right,
                        sliceTop + sliceHeight + 2f.units,
                        fillPaint,
                    )
                }

                ParagraphRole.QUOTE -> {
                    fillPaint.color = QUOTE_BAR
                    canvas.drawRect(
                        cursor.left + indent - decorationInset,
                        sliceTop,
                        cursor.left + indent - decorationInset + 2f.units,
                        sliceTop + sliceHeight,
                        fillPaint,
                    )
                }

                else -> {
                    Unit
                }
            }
            if (isFirstSlice && marker != null && layout.lineCount > 0) {
                val baseline = sliceTop + layout.getLineBaseline(0)
                canvas.drawText(marker, cursor.left + indent - markerWidth, baseline, markerPaint)
            }
        }
        cursor.y += spaceAfter
    }

    private fun drawLayoutAcrossPages(
        cursor: PageCursor,
        layout: StaticLayout,
        indent: Float,
        decorate: (Canvas, Float, Float, Boolean) -> Unit,
    ) {
        var line = 0
        var firstSlice = true
        while (line < layout.lineCount) {
            context.checkCancelled()
            val canvas = cursor.requireCanvas()
            val sliceStart = layout.getLineTop(line)
            var lastLine = line - 1
            while (lastLine + 1 < layout.lineCount && layout.getLineBottom(lastLine + 1) - sliceStart <= cursor.remaining) lastLine++
            if (lastLine < line) {
                if (!cursor.atPageTop) {
                    cursor.newPage()
                    continue
                }
                lastLine = line
            }
            val sliceHeight = (layout.getLineBottom(lastLine) - sliceStart).toFloat()
            decorate(canvas, cursor.y, sliceHeight, firstSlice)
            canvas.withClip(cursor.left + indent - 1f, cursor.y, cursor.right + 4f.units, cursor.y + sliceHeight) {
                translate(cursor.left + indent, cursor.y - sliceStart)
                layout.draw(this)
            }
            cursor.y += sliceHeight
            line = lastLine + 1
            firstSlice = false
            if (line < layout.lineCount) cursor.newPage()
        }
    }

    private fun defaultSpaceBefore(paragraph: Paragraph): Float =
        when (paragraph.role) {
            ParagraphRole.HEADING -> if (paragraph.headingLevel <= 2) 14f else 10f
            ParagraphRole.CODE, ParagraphRole.QUOTE -> 6f
            else -> 0f
        }

    private fun defaultSpaceAfter(paragraph: Paragraph): Float =
        when (paragraph.role) {
            ParagraphRole.HEADING -> 6f
            ParagraphRole.CODE, ParagraphRole.QUOTE -> 8f
            else -> if (paragraph.listMarker != null) 2f else 6f
        }

    private fun renderRule(cursor: PageCursor) {
        cursor.ensureSpace(12f.units)
        val canvas = cursor.requireCanvas()
        val y = cursor.y + 6f.units
        canvas.drawLine(cursor.left, y, cursor.right, y, borderPaint)
        cursor.y += 12f.units
    }

    private fun renderImage(
        cursor: PageCursor,
        block: ImageBlock,
    ) {
        val bitmap = BitmapLoader.load(block.source, context.limits.maxEmbeddedImagePx)
        if (bitmap == null) {
            context.warn(ConversionWarning.IMAGES_SKIPPED)
            return
        }
        try {
            val naturalWidth = block.widthPt?.units ?: (bitmap.width * 72f / IMAGE_DPI).units
            val naturalHeight = block.heightPt?.units ?: (naturalWidth * bitmap.height / bitmap.width)
            var width = naturalWidth
            var height = if (block.widthPt != null && block.heightPt == null) width * bitmap.height / bitmap.width else naturalHeight
            if (width > cursor.width) {
                height *= cursor.width / width
                width = cursor.width
            }
            if (height > cursor.height) {
                width *= cursor.height / height
                height = cursor.height
            }
            cursor.ensureSpace(height + 4f.units)
            val canvas = cursor.requireCanvas()
            val x = cursor.left + (cursor.width - width) / 2f
            canvas.drawBitmap(bitmap, null, RectF(x, cursor.y + 2f.units, x + width, cursor.y + 2f.units + height), bitmapPaint)
            cursor.y += height + 8f.units
        } finally {
            recycle(bitmap)
        }
    }

    private fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    private class CellLayout(
        val layout: StaticLayout?,
        val x: Float,
        val width: Float,
    )

    private fun renderTable(
        cursor: PageCursor,
        table: Table,
    ) {
        val columnCount = table.rows.maxOfOrNull { row -> row.cells.sumOf { it.columnSpan.coerceAtLeast(1) } } ?: return
        if (columnCount == 0) return
        val padding = (if (table.compact) 2.5f else 4f).units
        var sizePt = if (table.compact) COMPACT_TABLE_SIZE else TABLE_SIZE
        var widths = computeColumnWidths(table, columnCount, sizePt, padding, cursor.width)
        if (table.compact && widths.second > cursor.width) {
            val scaled = (sizePt * cursor.width / widths.second).coerceAtLeast(MIN_TABLE_SIZE)
            if (scaled < sizePt) {
                sizePt = scaled
                widths = computeColumnWidths(table, columnCount, sizePt, padding, cursor.width)
            }
        }
        val columnWidths = widths.first
        val defaults = TextDefaults(sizePt = sizePt)
        val headerDefaults = defaults.copy(bold = true)
        val headerRows = table.rows.take(table.headerRowCount.coerceAtMost(table.rows.size))
        val headerLayouts = headerRows.map { layoutRow(it, columnWidths, headerDefaults, padding, cursor.left) }
        cursor.y += 4f.units
        val previousRepeat = cursor.onRepeatHeader
        for ((rowIndex, row) in table.rows.withIndex()) {
            context.checkCancelled()
            val isHeader = rowIndex < headerRows.size
            val cells = if (isHeader) headerLayouts[rowIndex] else layoutRow(row, columnWidths, defaults, padding, cursor.left)
            val rowHeight = rowHeight(cells, padding, sizePt)
            if (!isHeader && headerLayouts.isNotEmpty()) {
                cursor.onRepeatHeader =
                    { page -> headerLayouts.forEach { drawRow(page, it, rowHeight(it, padding, sizePt), padding, true) } }
            }
            if (rowHeight > cursor.remaining && !cursor.atPageTop) cursor.newPage()
            drawRow(cursor, cells, rowHeight, padding, isHeader)
        }
        cursor.onRepeatHeader = previousRepeat
        cursor.y += 8f.units
    }

    private fun rowHeight(
        cells: List<CellLayout>,
        padding: Float,
        sizePt: Float,
    ): Float {
        val content = cells.maxOfOrNull { it.layout?.height?.toFloat() ?: 0f } ?: 0f
        return max(content, sizePt.units * 1.2f) + padding * 2
    }

    private fun drawRow(
        cursor: PageCursor,
        cells: List<CellLayout>,
        rowHeight: Float,
        padding: Float,
        isHeader: Boolean,
    ) {
        var offset = 0f
        var remainingHeight = rowHeight
        while (remainingHeight > 0f) {
            val canvas = cursor.requireCanvas()
            val slice = min(remainingHeight, cursor.remaining)
            val top = cursor.y
            if (isHeader) {
                fillPaint.color = HEADER_BACKGROUND
                canvas.drawRect(cursor.left, top, cells.lastOrNull()?.let { it.x + it.width } ?: cursor.right, top + slice, fillPaint)
            }
            for (cell in cells) {
                canvas.drawRect(cell.x, top, cell.x + cell.width, top + slice, borderPaint)
                val layout = cell.layout ?: continue
                canvas.withClip(cell.x, top, cell.x + cell.width, top + slice) {
                    translate(cell.x + padding, top + padding - offset)
                    layout.draw(this)
                }
            }
            cursor.y += slice
            remainingHeight -= slice
            offset += slice
            if (remainingHeight > 0f) cursor.newPage()
        }
    }

    private fun layoutRow(
        row: TableRow,
        columnWidths: FloatArray,
        defaults: TextDefaults,
        padding: Float,
        left: Float,
    ): List<CellLayout> {
        val paint = TextRendering.paint(defaults)
        var column = 0
        var x = left
        val result = ArrayList<CellLayout>(row.cells.size)
        for (cell in row.cells) {
            if (column >= columnWidths.size) break
            val span = cell.columnSpan.coerceAtLeast(1).coerceAtMost(columnWidths.size - column)
            val width = (column until column + span).sumOf { columnWidths[it].toDouble() }.toFloat()
            val text = cellText(cell.paragraphs, defaults)
            val alignment = cell.paragraphs.firstOrNull()?.alignment ?: Alignment.START
            val layout = if (text.isEmpty()) null else TextRendering.layout(text, paint, width - padding * 2, alignment)
            result.add(CellLayout(layout, x, width))
            x += width
            column += span
        }
        while (column < columnWidths.size) {
            result.add(CellLayout(null, x, columnWidths[column]))
            x += columnWidths[column]
            column++
        }
        return result
    }

    private fun cellText(
        paragraphs: List<Paragraph>,
        defaults: TextDefaults,
    ): CharSequence {
        val runs = ArrayList<TextRun>()
        paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) runs.add(TextRun("\n"))
            paragraph.listMarker?.let { runs.add(TextRun("$it ")) }
            runs.addAll(paragraph.runs)
        }
        val text = TextRendering.spanned(runs, defaults)
        val trimmed = text.trimEnd().length
        return text.subSequence(0, trimmed)
    }

    private fun computeColumnWidths(
        table: Table,
        columnCount: Int,
        sizePt: Float,
        padding: Float,
        available: Float,
    ): Pair<FloatArray, Float> {
        val paint = TextRendering.paint(TextDefaults(sizePt = sizePt, bold = false))
        val minimum = FloatArray(columnCount) { padding * 2 + sizePt.units }
        val preferred = FloatArray(columnCount) { padding * 2 + sizePt.units }
        val wordCap = available * 0.3f
        for (row in table.rows) {
            var column = 0
            for (cell in row.cells) {
                val span = cell.columnSpan.coerceAtLeast(1)
                if (column >= columnCount) break
                if (span == 1) {
                    val text =
                        cell.paragraphs.joinToString("\n") { paragraph ->
                            (paragraph.listMarker?.let { "$it " } ?: "") +
                                paragraph.plainText
                        }
                    val lines = text.split('\n')
                    val longestLine = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
                    val longestWord = lines.flatMap { it.split(' ', '\t') }.maxOfOrNull { paint.measureText(it) } ?: 0f
                    preferred[column] = max(preferred[column], longestLine + padding * 2 + 1f.units)
                    minimum[column] = max(minimum[column], min(longestWord, wordCap) + padding * 2 + 1f.units)
                }
                column += span
            }
        }
        val sumMinimum = minimum.sum()
        val sumPreferred = preferred.sum()
        val widths =
            when {
                sumPreferred <= available -> {
                    if (table.compact) {
                        preferred
                    } else {
                        val ratio = available / sumPreferred
                        FloatArray(columnCount) { preferred[it] * ratio }
                    }
                }

                sumMinimum <= available -> {
                    val extra = available - sumMinimum
                    val flexible = (sumPreferred - sumMinimum).coerceAtLeast(1f)
                    FloatArray(columnCount) { minimum[it] + extra * (preferred[it] - minimum[it]) / flexible }
                }

                else -> {
                    val ratio = available / sumMinimum
                    FloatArray(columnCount) { minimum[it] * ratio }
                }
            }
        return widths to sumMinimum
    }

    private companion object {
        const val BODY_SIZE = 11f
        const val CODE_SIZE = 9.5f
        const val CAPTION_SIZE = 9.5f
        const val TABLE_SIZE = 10f
        const val COMPACT_TABLE_SIZE = 9f
        const val MIN_TABLE_SIZE = 5f
        const val IMAGE_DPI = 150f
        val HEADING_SIZES = listOf(22f, 18f, 15f, 13f, 12f, 11f)
        val BORDER_COLOR = 0xFF9E9E9E.toInt()
        val HEADER_BACKGROUND = 0xFFEEEEEE.toInt()
        val CODE_BACKGROUND = 0xFFF3F3F3.toInt()
        val QUOTE_BAR = 0xFFBDBDBD.toInt()
        val QUOTE_COLOR = 0xFF424242.toInt()
    }
}
