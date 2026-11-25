package com.jvmd.notesbridge.model

import java.io.InputStream

fun interface ImageSource {
    fun open(): InputStream
}

enum class FontFamily { SANS, SERIF, MONO }

enum class Baseline { NORMAL, SUPERSCRIPT, SUBSCRIPT }

enum class Alignment { START, CENTER, END, JUSTIFY }

data class TextStyle(
    val bold: Boolean? = null,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val family: FontFamily? = null,
    val fontSizePt: Float? = null,
    val color: Int? = null,
    val baseline: Baseline = Baseline.NORMAL,
)

data class TextRun(
    val text: String,
    val style: TextStyle = TextStyle(),
)

sealed interface FlowBlock

enum class ParagraphRole { BODY, HEADING, QUOTE, CODE, CAPTION }

data class Paragraph(
    val runs: List<TextRun>,
    val role: ParagraphRole = ParagraphRole.BODY,
    val headingLevel: Int = 0,
    val alignment: Alignment = Alignment.START,
    val indentPt: Float = 0f,
    val listMarker: String? = null,
    val spaceBeforePt: Float? = null,
    val spaceAfterPt: Float? = null,
    val lineSpacing: Float? = null,
) : FlowBlock {
    val plainText: String get() = runs.joinToString(separator = "") { it.text }
}

data class TableCell(
    val paragraphs: List<Paragraph>,
    val columnSpan: Int = 1,
)

data class TableRow(
    val cells: List<TableCell>,
)

data class Table(
    val rows: List<TableRow>,
    val headerRowCount: Int = 0,
    val compact: Boolean = false,
) : FlowBlock

data class ImageBlock(
    val source: ImageSource,
    val widthPt: Float? = null,
    val heightPt: Float? = null,
) : FlowBlock

data object PageBreak : FlowBlock

data object HorizontalRule : FlowBlock

data class PageSetup(
    val widthPt: Float,
    val heightPt: Float,
    val marginTopPt: Float = DEFAULT_MARGIN_PT,
    val marginBottomPt: Float = DEFAULT_MARGIN_PT,
    val marginStartPt: Float = DEFAULT_MARGIN_PT,
    val marginEndPt: Float = DEFAULT_MARGIN_PT,
) {
    val contentWidthPt: Float get() = widthPt - marginStartPt - marginEndPt
    val contentHeightPt: Float get() = heightPt - marginTopPt - marginBottomPt

    fun isUsable(): Boolean = contentWidthPt >= MIN_CONTENT_PT && contentHeightPt >= MIN_CONTENT_PT

    companion object {
        const val DEFAULT_MARGIN_PT = 48f
        private const val MIN_CONTENT_PT = 72f
        val A4_PORTRAIT = PageSetup(595f, 842f)
        val A4_LANDSCAPE = PageSetup(842f, 595f, 36f, 36f, 36f, 36f)
    }
}

data class FlowDocument(
    val blocks: List<FlowBlock>,
    val pageSetup: PageSetup = PageSetup.A4_PORTRAIT,
)
