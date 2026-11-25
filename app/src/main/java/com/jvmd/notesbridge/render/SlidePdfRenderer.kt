package com.jvmd.notesbridge.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.model.Frame
import com.jvmd.notesbridge.model.PathCommand
import com.jvmd.notesbridge.model.PictureElement
import com.jvmd.notesbridge.model.ShapeElement
import com.jvmd.notesbridge.model.ShapeOutline
import com.jvmd.notesbridge.model.Slide
import com.jvmd.notesbridge.model.SlideDeck
import com.jvmd.notesbridge.model.SlideElement
import com.jvmd.notesbridge.model.SlideParagraph
import com.jvmd.notesbridge.model.TableElement
import com.jvmd.notesbridge.model.TextBox
import com.jvmd.notesbridge.model.VerticalAnchor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SlidePdfRenderer(
    private val sink: PdfSink,
    private val context: ConversionContext,
) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun render(deck: SlideDeck): Int {
        var pages = 0
        for (slide in deck.slides) {
            context.checkCancelled()
            val canvas = sink.beginPage(deck.widthPt, deck.heightPt)
            drawSlide(canvas, slide, deck.widthPt.units, deck.heightPt.units)
            sink.endPage()
            pages++
        }
        return pages
    }

    private fun drawSlide(
        canvas: Canvas,
        slide: Slide,
        width: Float,
        height: Float,
    ) {
        fillPaint.color = slide.background.color ?: Colors.WHITE
        canvas.drawRect(0f, 0f, width, height, fillPaint)
        slide.background.image?.let { source ->
            val bitmap = BitmapLoader.load(source, context.limits.maxEmbeddedImagePx)
            if (bitmap == null) {
                context.warn(ConversionWarning.IMAGES_SKIPPED)
            } else {
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width, height), bitmapPaint)
                bitmap.recycle()
            }
        }
        for (element in slide.elements) {
            context.checkCancelled()
            drawElement(canvas, element)
        }
    }

    private fun drawElement(
        canvas: Canvas,
        element: SlideElement,
    ) {
        val frame = element.frame
        val rect = RectF(frame.x.units, frame.y.units, (frame.x + frame.width).units, (frame.y + frame.height).units)
        canvas.withRotation(frame.rotationDeg, rect.centerX(), rect.centerY()) {
            when (element) {
                is ShapeElement -> drawShape(this, element, rect)
                is PictureElement -> drawPicture(this, element, rect)
                is TableElement -> drawTable(this, element, rect)
            }
        }
    }

    private fun drawShape(
        canvas: Canvas,
        shape: ShapeElement,
        rect: RectF,
    ) {
        val outline = shape.outline
        val path = outlinePath(outline, rect, shape.frame)
        val fillable = outline !is ShapeOutline.Line && !(outline is ShapeOutline.Path && !outline.filled)
        if (fillable) {
            shape.fillColor?.let {
                fillPaint.color = it
                canvas.drawPath(path, fillPaint)
            }
            shape.fillImage?.let { source -> drawClippedImage(canvas, source, rect, path) }
        }
        val strokeColor = shape.strokeColor
        if (strokeColor != null && shape.strokeWidthPt > 0f) {
            strokePaint.color = strokeColor
            strokePaint.strokeWidth = shape.strokeWidthPt.units
            canvas.drawPath(path, strokePaint)
        }
        shape.text?.takeUnless { it.isEmpty }?.let { drawTextBox(canvas, it, rect) }
    }

    private fun outlinePath(
        outline: ShapeOutline,
        rect: RectF,
        frame: Frame,
    ): Path {
        val path = Path()
        when (outline) {
            ShapeOutline.Rectangle -> {
                path.addRect(rect, Path.Direction.CW)
            }

            is ShapeOutline.RoundedRectangle -> {
                val radius = min(rect.width(), rect.height()) * outline.radiusFraction
                path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            }

            ShapeOutline.Ellipse -> {
                path.addOval(rect, Path.Direction.CW)
            }

            ShapeOutline.Line -> {
                val startX = if (frame.flipHorizontal) rect.right else rect.left
                val endX = if (frame.flipHorizontal) rect.left else rect.right
                val startY = if (frame.flipVertical) rect.bottom else rect.top
                val endY = if (frame.flipVertical) rect.top else rect.bottom
                path.moveTo(startX, startY)
                path.lineTo(endX, endY)
            }

            is ShapeOutline.Path -> {
                appendCommands(path, outline.commands, rect, frame)
            }
        }
        return path
    }

    private fun appendCommands(
        path: Path,
        commands: List<PathCommand>,
        rect: RectF,
        frame: Frame,
    ) {
        fun x(value: Float) = if (frame.flipHorizontal) rect.right - value * rect.width() else rect.left + value * rect.width()

        fun y(value: Float) = if (frame.flipVertical) rect.bottom - value * rect.height() else rect.top + value * rect.height()
        var currentX = rect.left
        var currentY = rect.top
        for (command in commands) {
            when (command) {
                is PathCommand.MoveTo -> {
                    currentX = x(command.x)
                    currentY = y(command.y)
                    path.moveTo(currentX, currentY)
                }

                is PathCommand.LineTo -> {
                    currentX = x(command.x)
                    currentY = y(command.y)
                    path.lineTo(currentX, currentY)
                }

                is PathCommand.QuadTo -> {
                    currentX = x(command.x)
                    currentY = y(command.y)
                    path.quadTo(x(command.x1), y(command.y1), currentX, currentY)
                }

                is PathCommand.CubicTo -> {
                    currentX = x(command.x)
                    currentY = y(command.y)
                    path.cubicTo(x(command.x1), y(command.y1), x(command.x2), y(command.y2), currentX, currentY)
                }

                is PathCommand.ArcTo -> {
                    val radiusX = command.radiusX * rect.width()
                    val radiusY = command.radiusY * rect.height()
                    val start = Math.toRadians(command.startAngleDeg.toDouble())
                    val centerX = currentX - radiusX * kotlin.math.cos(start).toFloat()
                    val centerY = currentY - radiusY * kotlin.math.sin(start).toFloat()
                    path.arcTo(
                        RectF(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY),
                        command.startAngleDeg,
                        command.sweepAngleDeg,
                        false,
                    )
                    val end = Math.toRadians((command.startAngleDeg + command.sweepAngleDeg).toDouble())
                    currentX = centerX + radiusX * kotlin.math.cos(end).toFloat()
                    currentY = centerY + radiusY * kotlin.math.sin(end).toFloat()
                }

                PathCommand.Close -> {
                    path.close()
                }
            }
        }
    }

    private fun drawClippedImage(
        canvas: Canvas,
        source: com.jvmd.notesbridge.model.ImageSource,
        rect: RectF,
        clip: Path,
    ) {
        val bitmap = loadFor(source, rect) ?: return
        canvas.withClip(clip) { drawBitmap(bitmap, null, rect, bitmapPaint) }
        bitmap.recycle()
    }

    private fun drawPicture(
        canvas: Canvas,
        picture: PictureElement,
        rect: RectF,
    ) {
        val bitmap = loadFor(picture.source, rect) ?: return
        val crop = picture.crop
        val sourceRect =
            if (crop == null) {
                null
            } else {
                Rect(
                    (bitmap.width * crop.left).roundToInt().coerceIn(0, bitmap.width - 1),
                    (bitmap.height * crop.top).roundToInt().coerceIn(0, bitmap.height - 1),
                    (bitmap.width * (1f - crop.right)).roundToInt().coerceIn(1, bitmap.width),
                    (bitmap.height * (1f - crop.bottom)).roundToInt().coerceIn(1, bitmap.height),
                ).takeIf { it.width() > 0 && it.height() > 0 }
            }
        canvas.withScale(
            if (picture.frame.flipHorizontal) -1f else 1f,
            if (picture.frame.flipVertical) -1f else 1f,
            rect.centerX(),
            rect.centerY(),
        ) {
            drawBitmap(bitmap, sourceRect, rect, bitmapPaint)
        }
        bitmap.recycle()
    }

    private fun loadFor(
        source: com.jvmd.notesbridge.model.ImageSource,
        rect: RectF,
    ): Bitmap? {
        val targetPx =
            (
                max(
                    rect.width(),
                    rect.height(),
                ) / com.jvmd.notesbridge.render.PdfSink.UNITS_PER_POINT * IMAGE_DPI / 72f
            ).roundToInt()
        val bitmap = BitmapLoader.load(source, targetPx.coerceIn(64, context.limits.maxEmbeddedImagePx))
        if (bitmap == null) context.warn(ConversionWarning.IMAGES_SKIPPED)
        return bitmap
    }

    private fun drawTable(
        canvas: Canvas,
        table: TableElement,
        rect: RectF,
    ) {
        val columnWidths = table.columnWidthsPt.map { it.units }
        if (columnWidths.isEmpty()) return
        strokePaint.color = table.borderColor
        strokePaint.strokeWidth = 0.75f.units
        var y = rect.top
        for (row in table.rows) {
            var column = 0
            var x = rect.left
            val cells =
                row.cells.mapNotNull { cell ->
                    if (column >= columnWidths.size) return@mapNotNull null
                    val span = cell.columnSpan.coerceIn(1, columnWidths.size - column)
                    val width = columnWidths.subList(column, column + span).sum()
                    val cellRect = RectF(x, 0f, x + width, 0f)
                    x += width
                    column += span
                    cell to cellRect
                }
            val contentHeight =
                cells.maxOfOrNull { (cell, cellRect) ->
                    measureTextBox(cell.text, cellRect.width()) + (cell.text.insets.top + cell.text.insets.bottom).units
                } ?: 0f
            val rowHeight = max(row.heightPt.units, contentHeight)
            for ((cell, cellRect) in cells) {
                cellRect.top = y
                cellRect.bottom = y + rowHeight
                cell.fillColor?.let {
                    fillPaint.color = it
                    canvas.drawRect(cellRect, fillPaint)
                }
                canvas.drawRect(cellRect, strokePaint)
                drawTextBox(canvas, cell.text, cellRect)
            }
            y += rowHeight
        }
    }

    private class LaidParagraph(
        val paragraph: SlideParagraph,
        val layout: StaticLayout?,
        val emptyHeight: Float,
        val bulletPaint: TextPaint?,
        val textIndent: Float,
        val bulletIndent: Float,
    )

    private fun layoutTextBox(
        box: TextBox,
        width: Float,
        scale: Float,
    ): List<LaidParagraph> =
        box.paragraphs.map { paragraph ->
            val firstSize =
                (
                    paragraph.runs
                        .firstOrNull { it.text.isNotEmpty() }
                        ?.style
                        ?.fontSizePt ?: paragraph.emptyLineSizePt
                ) * scale
            val defaults = TextDefaults(sizePt = firstSize.coerceAtLeast(1f))
            val text = TextRendering.spanned(paragraph.runs, defaults, scale)
            val trimmed = text.trimEnd { it == '\n' }.length
            val content = text.subSequence(0, trimmed)
            val paint = TextRendering.paint(defaults)
            val bulletPaint =
                paragraph.bullet?.let {
                    TextPaint(paint).apply {
                        color = paragraph.bulletColor ?: paragraph.runs
                            .firstOrNull()
                            ?.style
                            ?.color ?: Colors.BLACK
                        typeface = TextRendering.typeface(com.jvmd.notesbridge.model.FontFamily.SANS, false, false)
                    }
                }
            val bulletIndent = paragraph.bulletIndentPt.units * scale
            val minimumTextIndent =
                if (paragraph.bullet != null &&
                    bulletPaint != null
                ) {
                    bulletIndent + bulletPaint.measureText(paragraph.bullet) + bulletPaint.textSize * BULLET_GAP_EM
                } else {
                    0f
                }
            val textIndent = maxOf(paragraph.indentPt.units * scale, minimumTextIndent)
            val availableWidth = (width - textIndent).coerceAtLeast(1f.units)
            val layout =
                if (content.isEmpty()) {
                    null
                } else {
                    TextRendering.layout(
                        content,
                        paint,
                        availableWidth,
                        paragraph.alignment,
                        paragraph.lineSpacing,
                        box.wrap,
                    )
                }
            val emptyHeight = paint.fontSpacing * paragraph.lineSpacing
            LaidParagraph(paragraph, layout, emptyHeight, bulletPaint, textIndent, bulletIndent)
        }

    private fun totalHeight(
        paragraphs: List<LaidParagraph>,
        scale: Float,
    ): Float =
        paragraphs
            .withIndex()
            .sumOf { (index, laid) ->
                val before = if (index == 0) 0f else laid.paragraph.spaceBeforePt.units * scale
                val after = laid.paragraph.spaceAfterPt.units * scale
                (before + (laid.layout?.height?.toFloat() ?: laid.emptyHeight) + after).toDouble()
            }.toFloat()

    private fun measureTextBox(
        box: TextBox,
        width: Float,
    ): Float {
        val inner = width - (box.insets.left + box.insets.right).units
        return totalHeight(layoutTextBox(box, inner, box.fontScale), box.fontScale)
    }

    private fun drawTextBox(
        canvas: Canvas,
        box: TextBox,
        rect: RectF,
    ) {
        if (box.isEmpty) return
        val inner =
            RectF(
                rect.left + box.insets.left.units,
                rect.top + box.insets.top.units,
                rect.right - box.insets.right.units,
                rect.bottom - box.insets.bottom.units,
            )
        if (inner.width() <= 0f) inner.right = inner.left + 1f.units
        var scale = box.fontScale
        var laid = layoutTextBox(box, inner.width(), scale)
        var height = totalHeight(laid, scale)
        if (box.shrinkToFit) {
            var attempts = 0
            while (height > inner.height() && attempts < 8 && scale > 0.35f) {
                scale *= 0.9f
                laid = layoutTextBox(box, inner.width(), scale)
                height = totalHeight(laid, scale)
                attempts++
            }
        }
        var y =
            when (box.anchor) {
                VerticalAnchor.TOP -> inner.top
                VerticalAnchor.MIDDLE -> inner.top + (inner.height() - height) / 2f
                VerticalAnchor.BOTTOM -> inner.bottom - height
            }
        for ((index, item) in laid.withIndex()) {
            if (index > 0) y += item.paragraph.spaceBeforePt.units * scale
            val layout = item.layout
            val x = inner.left + item.textIndent
            if (layout != null) {
                val bullet = item.paragraph.bullet
                val bulletPaint = item.bulletPaint
                if (bullet != null && bulletPaint != null && layout.lineCount > 0) {
                    val bulletX = inner.left + item.bulletIndent
                    canvas.drawText(bullet, bulletX, y + layout.getLineBaseline(0), bulletPaint)
                }
                canvas.withTranslation(x, y) { layout.draw(this) }
                y += layout.height
            } else {
                y += item.emptyHeight
            }
            y += item.paragraph.spaceAfterPt.units * scale
        }
    }

    private companion object {
        const val IMAGE_DPI = 220f
        const val BULLET_GAP_EM = 0.35f
    }
}
