package com.jvmd.notesbridge.formats.docx

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.common.OoxmlPackage
import com.jvmd.notesbridge.formats.common.Relationship
import com.jvmd.notesbridge.formats.common.Units
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.Baseline
import com.jvmd.notesbridge.model.FlowBlock
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.ImageBlock
import com.jvmd.notesbridge.model.PageBreak
import com.jvmd.notesbridge.model.PageSetup
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.ParagraphRole
import com.jvmd.notesbridge.model.Table
import com.jvmd.notesbridge.model.TableCell
import com.jvmd.notesbridge.model.TableRow
import com.jvmd.notesbridge.model.TextRun
import com.jvmd.notesbridge.model.TextStyle
import com.jvmd.notesbridge.xml.Namespaces.A
import com.jvmd.notesbridge.xml.Namespaces.MARKUP_COMPATIBILITY
import com.jvmd.notesbridge.xml.Namespaces.MATH
import com.jvmd.notesbridge.xml.Namespaces.R
import com.jvmd.notesbridge.xml.Namespaces.VML
import com.jvmd.notesbridge.xml.Namespaces.W
import com.jvmd.notesbridge.xml.Namespaces.WP
import com.jvmd.notesbridge.xml.XmlElement

class DocxReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument {
        val pkg = OoxmlPackage(input.archive, context)
        val documentPath = pkg.mainPart("officeDocument", "word/document.xml")
        val root = pkg.xml(documentPath) ?: throw ConversionException.Corrupted("missing $documentPath")
        val body = root.child(W, "body") ?: throw ConversionException.Corrupted("document has no body")
        val relationships = pkg.relationships(documentPath)
        val styles = DocxStyles(relationshipXml(pkg, relationships, "styles"))
        val numbering = DocxNumbering(relationshipXml(pkg, relationships, "numbering"))
        val converter = BodyConverter(pkg, relationships, styles, numbering, context)
        converter.convertContainer(body)
        return FlowDocument(converter.blocks, pageSetup(body.child(W, "sectPr")))
    }

    private fun relationshipXml(
        pkg: OoxmlPackage,
        relationships: Map<String, Relationship>,
        type: String,
    ): XmlElement? = relationships.values.firstOrNull { it.hasType(type) && !it.external }?.let { pkg.xml(it.target) }

    private fun pageSetup(section: XmlElement?): PageSetup {
        val size = section?.child(W, "pgSz")
        val margins = section?.child(W, "pgMar")
        val width = Units.twipsToPt(size?.attr(W, "w")) ?: return PageSetup.A4_PORTRAIT
        val height = Units.twipsToPt(size?.attr(W, "h")) ?: return PageSetup.A4_PORTRAIT
        val setup =
            PageSetup(
                widthPt = width,
                heightPt = height,
                marginTopPt = Units.twipsToPt(margins?.attr(W, "top"))?.let { kotlin.math.abs(it) }?.coerceIn(18f, 144f) ?: 72f,
                marginBottomPt = Units.twipsToPt(margins?.attr(W, "bottom"))?.let { kotlin.math.abs(it) }?.coerceIn(18f, 144f) ?: 72f,
                marginStartPt = Units.twipsToPt(margins?.attr(W, "left"))?.coerceIn(18f, 144f) ?: 72f,
                marginEndPt = Units.twipsToPt(margins?.attr(W, "right"))?.coerceIn(18f, 144f) ?: 72f,
            )
        return if (setup.isUsable()) setup else PageSetup.A4_PORTRAIT
    }

    private class BodyConverter(
        private val pkg: OoxmlPackage,
        private val relationships: Map<String, Relationship>,
        private val styles: DocxStyles,
        private val numbering: DocxNumbering,
        private val context: ConversionContext,
    ) {
        val blocks = ArrayList<FlowBlock>()
        private var visited = 0

        fun convertContainer(container: XmlElement) {
            convertInto(container, blocks)
        }

        private fun convertInto(
            container: XmlElement,
            target: MutableList<FlowBlock>,
        ) {
            for (element in container.elements) {
                visited++
                if (visited % 200 == 0) context.checkCancelled()
                when {
                    element.matches(W, "p") -> convertParagraph(element, target)

                    element.matches(W, "tbl") -> convertTable(element, target)

                    element.matches(W, "sdt") -> element.child(W, "sdtContent")?.let { convertInto(it, target) }

                    element.matches(
                        W,
                        "customXml",
                    ) || element.matches(W, "ins") || element.matches(W, "moveTo") -> convertInto(element, target)

                    element.matches(MARKUP_COMPATIBILITY, "AlternateContent") -> chooseAlternate(element)?.let { convertInto(it, target) }
                }
            }
        }

        private fun chooseAlternate(element: XmlElement): XmlElement? =
            element.child(MARKUP_COMPATIBILITY, "Choice") ?: element.child(MARKUP_COMPATIBILITY, "Fallback")

        private inner class ParagraphBuilder(
            val properties: ParagraphProperties,
            val baseRun: RunProperties,
            val target: MutableList<FlowBlock>,
            val headingLevel: Int?,
            var marker: String?,
            val indentPt: Float,
        ) {
            val runs = ArrayList<TextRun>()
            var emitted = false
            val trailing = ArrayList<FlowBlock>()

            fun flush(force: Boolean) {
                if (runs.isEmpty() && marker == null && !force) return
                target.add(
                    Paragraph(
                        runs = ArrayList(runs),
                        role = if (headingLevel != null) ParagraphRole.HEADING else ParagraphRole.BODY,
                        headingLevel = headingLevel ?: 0,
                        alignment = properties.alignment ?: Alignment.START,
                        indentPt = indentPt,
                        listMarker = marker,
                        spaceBeforePt = properties.spaceBeforePt ?: 0f,
                        spaceAfterPt = properties.spaceAfterPt ?: 0f,
                        lineSpacing = properties.lineSpacing,
                    ),
                )
                runs.clear()
                marker = null
                emitted = true
            }
        }

        private fun convertParagraph(
            element: XmlElement,
            target: MutableList<FlowBlock>,
        ) {
            val direct = ParagraphProperties.parse(element.child(W, "pPr"))
            val styleId = direct?.styleId
            val properties = styles.paragraphProperties(styleId).overriddenBy(direct)
            val baseRun = styles.paragraphRun(styleId)
            if (properties.pageBreakBefore == true) addPageBreak(target)
            val numberingId = properties.numberingId
            val marker = numberingId?.let { numbering.next(it, properties.numberingLevel ?: 0) }
            val indent =
                properties.indentLeftPt ?: marker?.indentPt ?: if (marker != null) 18f * ((properties.numberingLevel ?: 0) + 1) else 0f
            val builder =
                ParagraphBuilder(
                    properties = properties,
                    baseRun = baseRun,
                    target = target,
                    headingLevel = styles.headingLevel(styleId),
                    marker = marker?.text?.takeIf { it.isNotEmpty() },
                    indentPt = indent.coerceAtLeast(0f),
                )
            convertInline(element, builder, null)
            if (builder.runs.isNotEmpty() || !builder.emitted) builder.flush(force = true)
            target.addAll(builder.trailing)
            val sectionType =
                element
                    .child(W, "pPr")
                    ?.child(W, "sectPr")
                    ?.child(W, "type")
                    ?.attr(W, "val")
            if (element.child(W, "pPr")?.child(W, "sectPr") != null && sectionType != "continuous") addPageBreak(target)
        }

        private fun addPageBreak(target: MutableList<FlowBlock>) {
            if (target.isNotEmpty() && target.last() !is PageBreak) target.add(PageBreak)
        }

        private fun convertInline(
            container: XmlElement,
            builder: ParagraphBuilder,
            characterStyle: RunProperties?,
        ) {
            for (element in container.elements) {
                when {
                    element.matches(W, "r") -> {
                        convertRun(element, builder)
                    }

                    element.matches(W, "hyperlink") ||
                        element.matches(W, "ins") ||
                        element.matches(W, "smartTag") ||
                        element.matches(W, "customXml") ||
                        element.matches(W, "fldSimple") ||
                        element.matches(W, "moveTo") ||
                        element.matches(W, "bdo") ||
                        element.matches(W, "dir") -> {
                        convertInline(element, builder, characterStyle)
                    }

                    element.matches(W, "sdt") -> {
                        element.child(W, "sdtContent")?.let { convertInline(it, builder, characterStyle) }
                    }

                    element.matches(MARKUP_COMPATIBILITY, "AlternateContent") -> {
                        chooseAlternate(element)?.let { convertInline(it, builder, characterStyle) }
                    }

                    element.matches(MATH, "oMathPara") || element.matches(MATH, "oMath") -> {
                        val text = element.descendants(MATH, "t").joinToString("") { it.textContent() }
                        if (text.isNotEmpty()) builder.runs.add(TextRun(text, style(builder.baseRun).copy(italic = true)))
                    }
                }
            }
        }

        private fun convertRun(
            run: XmlElement,
            builder: ParagraphBuilder,
        ) {
            val direct = RunProperties.parse(run.child(W, "rPr"))
            val characterStyle = styles.characterRun(run.child(W, "rPr")?.child(W, "rStyle")?.attr(W, "val"))
            val properties = builder.baseRun.overriddenBy(characterStyle).overriddenBy(direct)
            if (properties.hidden == true) {
                context.warn(ConversionWarning.HIDDEN_CONTENT_SKIPPED)
                return
            }
            val textStyle = style(properties)
            for (child in run.elements) {
                when {
                    child.matches(W, "t") -> {
                        appendText(builder, child.textContent(), textStyle, properties)
                    }

                    child.matches(W, "tab") || child.matches(W, "ptab") -> {
                        appendText(builder, "\t", textStyle, properties)
                    }

                    child.matches(W, "br") -> {
                        if (child.attr(W, "type") == "page") {
                            builder.flush(force = false)
                            addPageBreak(builder.target)
                        } else {
                            appendText(builder, "\n", textStyle, properties)
                        }
                    }

                    child.matches(W, "cr") -> {
                        appendText(builder, "\n", textStyle, properties)
                    }

                    child.matches(W, "noBreakHyphen") -> {
                        appendText(builder, "‑", textStyle, properties)
                    }

                    child.matches(W, "sym") -> {
                        symbol(child)?.let { appendText(builder, it, textStyle, properties) }
                    }

                    child.matches(W, "drawing") -> {
                        drawing(child, builder)
                    }

                    child.matches(W, "pict") || child.matches(W, "object") -> {
                        legacyPicture(child, builder)
                    }

                    child.matches(MARKUP_COMPATIBILITY, "AlternateContent") -> {
                        chooseAlternate(child)?.let { choice ->
                            choice.elements.forEach { inner ->
                                when {
                                    inner.matches(W, "drawing") -> drawing(inner, builder)
                                    inner.matches(W, "pict") -> legacyPicture(inner, builder)
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun appendText(
            builder: ParagraphBuilder,
            text: String,
            style: TextStyle,
            properties: RunProperties,
        ) {
            if (text.isEmpty()) return
            val value = if (properties.caps == true) text.uppercase() else text
            val last = builder.runs.lastOrNull()
            if (last != null && last.style == style) {
                builder.runs[builder.runs.size - 1] = last.copy(text = last.text + value)
            } else {
                builder.runs.add(TextRun(value, style))
            }
        }

        private fun symbol(element: XmlElement): String? {
            val code = element.attr(W, "char")?.toIntOrNull(16) ?: return null
            return if (code in 0xF000..0xF0FF) "•" else String(Character.toChars(code))
        }

        private fun style(properties: RunProperties): TextStyle =
            TextStyle(
                bold = properties.bold ?: false,
                italic = properties.italic ?: false,
                underline = properties.underline ?: false,
                strikethrough = properties.strike ?: false,
                family = properties.family,
                fontSizePt = properties.sizePt ?: DEFAULT_SIZE_PT,
                color = properties.color,
                baseline = properties.baseline ?: Baseline.NORMAL,
            )

        private fun drawing(
            drawing: XmlElement,
            builder: ParagraphBuilder,
        ) {
            val container = drawing.child(WP, "inline") ?: drawing.child(WP, "anchor") ?: return
            val extent = container.child(WP, "extent")
            val width = Units.emuToPt(extent?.attr("cx"))
            val height = Units.emuToPt(extent?.attr("cy"))
            val graphicData = container.path(A, "graphic", "graphicData")
            val blip = graphicData?.descendants(A, "blip")?.firstOrNull()
            if (blip != null) {
                addImage(blip.attr(R, "embed"), blip.attr(R, "link"), width, height, builder)
                return
            }
            val textBoxes = container.descendants(W, "txbxContent").toList()
            if (textBoxes.isNotEmpty()) {
                textBoxes.forEach { convertInto(it, builder.trailing) }
                return
            }
            context.warn(ConversionWarning.EMBEDDED_OBJECTS_SKIPPED)
        }

        private fun legacyPicture(
            element: XmlElement,
            builder: ParagraphBuilder,
        ) {
            val imageData = element.descendants(VML, "imagedata").firstOrNull()
            if (imageData != null) {
                val shape = element.descendants(VML, "shape").firstOrNull()
                val (width, height) = vmlSize(shape?.attr("style"))
                addImage(imageData.attr(R, "id"), null, width, height, builder)
                return
            }
            val textBoxes = element.descendants(W, "txbxContent").toList()
            if (textBoxes.isNotEmpty()) {
                textBoxes.forEach { convertInto(it, builder.trailing) }
            } else {
                context.warn(ConversionWarning.EMBEDDED_OBJECTS_SKIPPED)
            }
        }

        private fun vmlSize(style: String?): Pair<Float?, Float?> {
            if (style == null) return null to null
            val values =
                style.split(';').associate { declaration ->
                    declaration.substringBefore(':').trim().lowercase() to declaration.substringAfter(':', "").trim()
                }
            return Units.lengthToPt(values["width"]) to Units.lengthToPt(values["height"])
        }

        private fun addImage(
            embedId: String?,
            linkId: String?,
            width: Float?,
            height: Float?,
            builder: ParagraphBuilder,
        ) {
            val relationship = embedId?.let { relationships[it] }
            if (relationship == null || relationship.external) {
                if (linkId != null ||
                    relationship?.external == true
                ) {
                    context.warn(ConversionWarning.EXTERNAL_RESOURCES_SKIPPED)
                } else {
                    context.warn(ConversionWarning.IMAGES_SKIPPED)
                }
                return
            }
            val source = pkg.archive.imageSource(relationship.target)
            if (source == null) {
                context.warn(ConversionWarning.IMAGES_SKIPPED)
                return
            }
            builder.flush(force = false)
            builder.target.add(ImageBlock(source, width, height))
            builder.emitted = true
        }

        private fun convertTable(
            table: XmlElement,
            target: MutableList<FlowBlock>,
        ) {
            val rows = ArrayList<TableRow>()
            var headerRows = 0
            var bodyStarted = false
            for (row in table.elements.flatMap { rowCandidates(it) }) {
                val cells = ArrayList<TableCell>()
                for (cell in row.elements.flatMap { cellCandidates(it) }) {
                    val properties = cell.child(W, "tcPr")
                    val span =
                        properties
                            ?.child(W, "gridSpan")
                            ?.attr(W, "val")
                            ?.toIntOrNull()
                            ?.coerceIn(1, 64) ?: 1
                    val merge = properties?.child(W, "vMerge")
                    val continuesMerge = merge != null && merge.attr(W, "val") != "restart"
                    val content = ArrayList<FlowBlock>()
                    if (!continuesMerge) convertInto(cell, content)
                    val paragraphs =
                        content.flatMap { block ->
                            when (block) {
                                is Paragraph -> {
                                    listOf(block)
                                }

                                is Table -> {
                                    block.rows.flatMap { nestedRow -> nestedRow.cells.flatMap { it.paragraphs } }
                                }

                                is ImageBlock -> {
                                    context.warn(ConversionWarning.IMAGES_SKIPPED)
                                    emptyList()
                                }

                                else -> {
                                    emptyList()
                                }
                            }
                        }
                    cells.add(TableCell(paragraphs, span))
                }
                if (cells.isEmpty()) continue
                val isHeader = row.child(W, "trPr")?.child(W, "tblHeader") != null
                if (isHeader && !bodyStarted) headerRows++ else bodyStarted = true
                rows.add(TableRow(cells))
            }
            if (rows.isNotEmpty()) target.add(Table(rows, headerRowCount = headerRows))
        }

        private fun rowCandidates(element: XmlElement): List<XmlElement> =
            when {
                element.matches(W, "tr") -> {
                    listOf(element)
                }

                element.matches(W, "sdt") -> {
                    element
                        .child(W, "sdtContent")
                        ?.elements
                        ?.flatMap { rowCandidates(it) }
                        ?.toList()
                        .orEmpty()
                }

                element.matches(W, "customXml") -> {
                    element.elements.flatMap { rowCandidates(it) }.toList()
                }

                else -> {
                    emptyList()
                }
            }

        private fun cellCandidates(element: XmlElement): List<XmlElement> =
            when {
                element.matches(W, "tc") -> {
                    listOf(element)
                }

                element.matches(W, "sdt") -> {
                    element
                        .child(W, "sdtContent")
                        ?.elements
                        ?.flatMap { cellCandidates(it) }
                        ?.toList()
                        .orEmpty()
                }

                element.matches(W, "customXml") -> {
                    element.elements.flatMap { cellCandidates(it) }.toList()
                }

                else -> {
                    emptyList()
                }
            }
    }

    private companion object {
        const val DEFAULT_SIZE_PT = 11f
    }
}
