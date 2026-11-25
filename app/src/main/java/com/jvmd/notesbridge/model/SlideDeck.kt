package com.jvmd.notesbridge.model

data class SlideDeck(
    val widthPt: Float,
    val heightPt: Float,
    val slides: List<Slide>,
)

data class Slide(
    val background: SlideBackground,
    val elements: List<SlideElement>,
)

data class SlideBackground(
    val color: Int? = null,
    val image: ImageSource? = null,
)

data class Frame(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDeg: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
)

sealed interface PathCommand {
    data class MoveTo(
        val x: Float,
        val y: Float,
    ) : PathCommand

    data class LineTo(
        val x: Float,
        val y: Float,
    ) : PathCommand

    data class QuadTo(
        val x1: Float,
        val y1: Float,
        val x: Float,
        val y: Float,
    ) : PathCommand

    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : PathCommand

    data class ArcTo(
        val radiusX: Float,
        val radiusY: Float,
        val startAngleDeg: Float,
        val sweepAngleDeg: Float,
    ) : PathCommand

    data object Close : PathCommand
}

sealed interface ShapeOutline {
    data object Rectangle : ShapeOutline

    data class RoundedRectangle(
        val radiusFraction: Float,
    ) : ShapeOutline

    data object Ellipse : ShapeOutline

    data object Line : ShapeOutline

    data class Path(
        val commands: List<PathCommand>,
        val filled: Boolean = true,
    ) : ShapeOutline
}

enum class VerticalAnchor { TOP, MIDDLE, BOTTOM }

data class Insets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class SlideParagraph(
    val runs: List<TextRun>,
    val alignment: Alignment = Alignment.START,
    val bullet: String? = null,
    val bulletColor: Int? = null,
    val indentPt: Float = 0f,
    val bulletIndentPt: Float = 0f,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 0f,
    val lineSpacing: Float = 1f,
    val emptyLineSizePt: Float = 18f,
)

data class TextBox(
    val paragraphs: List<SlideParagraph>,
    val insets: Insets,
    val anchor: VerticalAnchor = VerticalAnchor.TOP,
    val fontScale: Float = 1f,
    val shrinkToFit: Boolean = false,
    val wrap: Boolean = true,
) {
    val isEmpty: Boolean get() = paragraphs.all { paragraph -> paragraph.runs.all { it.text.isBlank() } }
}

sealed interface SlideElement {
    val frame: Frame
}

data class ShapeElement(
    override val frame: Frame,
    val outline: ShapeOutline,
    val fillColor: Int? = null,
    val fillImage: ImageSource? = null,
    val strokeColor: Int? = null,
    val strokeWidthPt: Float = 0f,
    val text: TextBox? = null,
) : SlideElement

data class Crop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class PictureElement(
    override val frame: Frame,
    val source: ImageSource,
    val crop: Crop? = null,
) : SlideElement

data class SlideTableCell(
    val text: TextBox,
    val fillColor: Int? = null,
    val columnSpan: Int = 1,
)

data class SlideTableRow(
    val heightPt: Float,
    val cells: List<SlideTableCell>,
)

data class TableElement(
    override val frame: Frame,
    val columnWidthsPt: List<Float>,
    val rows: List<SlideTableRow>,
    val borderColor: Int = 0xFF7F7F7F.toInt(),
) : SlideElement
