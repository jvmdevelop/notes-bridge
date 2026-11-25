package com.jvmd.notesbridge.formats.odf

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.common.RunCollector
import com.jvmd.notesbridge.formats.common.Units
import com.jvmd.notesbridge.formats.pptx.PresetGeometry
import com.jvmd.notesbridge.io.DocumentArchive
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.model.Frame
import com.jvmd.notesbridge.model.ImageSource
import com.jvmd.notesbridge.model.Insets
import com.jvmd.notesbridge.model.PictureElement
import com.jvmd.notesbridge.model.ShapeElement
import com.jvmd.notesbridge.model.ShapeOutline
import com.jvmd.notesbridge.model.Slide
import com.jvmd.notesbridge.model.SlideBackground
import com.jvmd.notesbridge.model.SlideDeck
import com.jvmd.notesbridge.model.SlideElement
import com.jvmd.notesbridge.model.SlideParagraph
import com.jvmd.notesbridge.model.SlideTableCell
import com.jvmd.notesbridge.model.SlideTableRow
import com.jvmd.notesbridge.model.TableElement
import com.jvmd.notesbridge.model.TextBox
import com.jvmd.notesbridge.model.VerticalAnchor
import com.jvmd.notesbridge.xml.Namespaces.DRAW
import com.jvmd.notesbridge.xml.Namespaces.FO
import com.jvmd.notesbridge.xml.Namespaces.OFFICE
import com.jvmd.notesbridge.xml.Namespaces.PRESENTATION
import com.jvmd.notesbridge.xml.Namespaces.SVG
import com.jvmd.notesbridge.xml.Namespaces.TABLE
import com.jvmd.notesbridge.xml.Namespaces.TEXT
import com.jvmd.notesbridge.xml.Namespaces.XLINK
import com.jvmd.notesbridge.xml.XmlElement
import com.jvmd.notesbridge.xml.XmlText
import kotlin.math.cos
import kotlin.math.sin

class OdpReader : DocumentReader<SlideDeck> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): SlideDeck {
        val archive = input.archive
        val content =
            archive.open("content.xml")?.use { context.xml.read(it) } ?: throw ConversionException.Corrupted("missing content.xml")
        val styles = OdfStyles(archive.open("styles.xml")?.use { context.xml.read(it) }, content)
        val presentation =
            content.path(OFFICE, "body", "presentation") ?: throw ConversionException.Corrupted("document has no presentation body")
        val pages = presentation.children(DRAW, "page").toList()
        val firstMaster = styles.masterPage(pages.firstOrNull()?.attr(DRAW, "master-page-name"))
        val layout = styles.pageLayoutOf(firstMaster)
        val width = Units.lengthToPt(layout?.attr(FO, "page-width"))?.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val height = Units.lengthToPt(layout?.attr(FO, "page-height"))?.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val builder = PageBuilder(archive, styles, context)
        val slides =
            pages.map { page ->
                context.checkCancelled()
                val master = styles.masterPage(page.attr(DRAW, "master-page-name"))
                val elements = ArrayList<SlideElement>()
                master?.let { builder.collect(it, elements, isMaster = true) }
                builder.collect(page, elements, isMaster = false)
                Slide(builder.background(page, master), elements)
            }
        return SlideDeck(width, height, slides)
    }

    private class PageBuilder(
        private val archive: DocumentArchive,
        private val styles: OdfStyles,
        private val context: ConversionContext,
    ) {
        fun background(
            page: XmlElement,
            master: XmlElement?,
        ): SlideBackground {
            for (styleName in listOf(page.attr(DRAW, "style-name"), master?.attr(DRAW, "style-name"))) {
                if (styleName == null) continue
                val properties = styles.drawingPage(styleName)
                when (properties.fill) {
                    "solid" -> return SlideBackground(properties.fillColor ?: Colors.WHITE)

                    "gradient" -> return SlideBackground(
                        styles.gradientColor(properties.gradientName) ?: properties.fillColor ?: Colors.WHITE,
                    )

                    "bitmap" -> return SlideBackground(Colors.WHITE, imageSource(styles.fillImage(properties.fillImageName)))
                }
            }
            return SlideBackground(Colors.WHITE)
        }

        fun collect(
            container: XmlElement,
            output: MutableList<SlideElement>,
            isMaster: Boolean,
        ) {
            for (element in container.elements) {
                context.checkCancelled()
                if (element.matches(PRESENTATION, "notes")) continue
                if (isMaster && element.attr(PRESENTATION, "class") != null) continue
                if (element.attr(PRESENTATION, "placeholder") == "true") continue
                when {
                    element.matches(DRAW, "frame") -> {
                        frame(element)?.let { output.add(it) }
                    }

                    element.matches(DRAW, "custom-shape") -> {
                        shape(element, customGeometry(element))?.let { output.add(it) }
                    }

                    element.matches(DRAW, "rect") -> {
                        shape(
                            element,
                            if (element.attr(DRAW, "corner-radius") !=
                                null
                            ) {
                                ShapeOutline.RoundedRectangle(0.15f)
                            } else {
                                ShapeOutline.Rectangle
                            },
                        )?.let { output.add(it) }
                    }

                    element.matches(DRAW, "ellipse") || element.matches(DRAW, "circle") -> {
                        shape(element, ShapeOutline.Ellipse)?.let { output.add(it) }
                    }

                    element.matches(DRAW, "line") || element.matches(DRAW, "connector") -> {
                        line(element)?.let { output.add(it) }
                    }

                    element.matches(DRAW, "g") -> {
                        collect(element, output, isMaster)
                    }
                }
            }
        }

        private fun frameOf(element: XmlElement): Frame? {
            val width = Units.lengthToPt(element.attr(SVG, "width")) ?: return null
            val height = Units.lengthToPt(element.attr(SVG, "height")) ?: return null
            val transform = element.attr(DRAW, "transform")
            if (transform != null) {
                val rotation =
                    Regex("rotate\\s*\\(\\s*([-0-9.eE]+)\\s*\\)")
                        .find(transform)
                        ?.groupValues
                        ?.get(1)
                        ?.toDoubleOrNull() ?: 0.0
                val translate = Regex("translate\\s*\\(\\s*([^ )]+)\\s+([^ )]+)\\s*\\)").find(transform)
                val tx = Units.lengthToPt(translate?.groupValues?.get(1)) ?: 0f
                val ty = Units.lengthToPt(translate?.groupValues?.get(2)) ?: 0f
                val angle = -rotation
                val centerX = tx + (width / 2f) * cos(angle).toFloat() - (height / 2f) * sin(angle).toFloat()
                val centerY = ty + (width / 2f) * sin(angle).toFloat() + (height / 2f) * cos(angle).toFloat()
                return Frame(centerX - width / 2f, centerY - height / 2f, width, height, Math.toDegrees(angle).toFloat())
            }
            val x = Units.lengthToPt(element.attr(SVG, "x")) ?: 0f
            val y = Units.lengthToPt(element.attr(SVG, "y")) ?: 0f
            return Frame(x, y, width, height)
        }

        private fun graphicProperties(element: XmlElement): OdfGraphicProperties {
            val presentationProperties = styles.graphic("presentation", element.attr(PRESENTATION, "style-name"))
            return presentationProperties.overriddenBy(
                element.attr(DRAW, "style-name")?.let { styles.graphic("graphic", it) },
            )
        }

        private fun baseText(element: XmlElement): OdfTextProperties {
            val presentationText = styles.text("presentation", element.attr(PRESENTATION, "style-name"))
            return presentationText.overriddenBy(element.attr(DRAW, "style-name")?.let { styles.text("graphic", it) })
        }

        private fun frame(element: XmlElement): SlideElement? {
            val frame = frameOf(element) ?: return null
            val image = element.child(DRAW, "image")
            if (image != null) {
                val source = imageSource(image.attr(XLINK, "href")) ?: return null
                return PictureElement(frame, source)
            }
            element.child(TABLE, "table")?.let { return table(it, frame, element) }
            if (element.child(DRAW, "object") != null || element.child(DRAW, "object-ole") != null ||
                element.child(DRAW, "plugin") != null
            ) {
                context.warn(ConversionWarning.EMBEDDED_OBJECTS_SKIPPED)
                return null
            }
            val textBox = element.child(DRAW, "text-box") ?: return null
            val properties = graphicProperties(element)
            val text = textBox(textBox, element, properties, VerticalAnchor.TOP)
            val (fill, fillImage) = fill(properties)
            val (stroke, strokeWidth) = stroke(properties)
            if (text.isEmpty && fill == null && stroke == null) return null
            return ShapeElement(frame, ShapeOutline.Rectangle, fill, fillImage, stroke, strokeWidth, text)
        }

        private fun shape(
            element: XmlElement,
            outline: ShapeOutline,
        ): SlideElement? {
            val frame = frameOf(element) ?: return null
            val properties = graphicProperties(element)
            val (fill, fillImage) = fill(properties)
            val (stroke, strokeWidth) = stroke(properties)
            val text = textBox(element, element, properties, VerticalAnchor.MIDDLE)
            if (fill == null && fillImage == null && stroke == null && text.isEmpty) return null
            return ShapeElement(frame, outline, fill, fillImage, stroke, strokeWidth, text)
        }

        private fun line(element: XmlElement): SlideElement? {
            val x1 = Units.lengthToPt(element.attr(SVG, "x1")) ?: return null
            val y1 = Units.lengthToPt(element.attr(SVG, "y1")) ?: return null
            val x2 = Units.lengthToPt(element.attr(SVG, "x2")) ?: return null
            val y2 = Units.lengthToPt(element.attr(SVG, "y2")) ?: return null
            val properties = graphicProperties(element)
            val (stroke, width) = stroke(properties)
            val frame =
                Frame(
                    x = minOf(x1, x2),
                    y = minOf(y1, y2),
                    width = kotlin.math.abs(x2 - x1),
                    height = kotlin.math.abs(y2 - y1),
                    flipHorizontal = x2 < x1,
                    flipVertical = y2 < y1,
                )
            return ShapeElement(frame, ShapeOutline.Line, null, null, stroke ?: Colors.BLACK, if (width > 0f) width else 0.75f, null)
        }

        private fun customGeometry(element: XmlElement): ShapeOutline {
            val type = element.child(DRAW, "enhanced-geometry")?.attr(DRAW, "type")
            val preset =
                when (type) {
                    null, "rectangle", "non-primitive" -> "rect"
                    "round-rectangle", "flowchart-alternate-process" -> "roundRect"
                    "ellipse", "circle", "flowchart-connector" -> "ellipse"
                    "isosceles-triangle" -> "triangle"
                    "right-triangle" -> "rtTriangle"
                    "diamond", "flowchart-decision" -> "diamond"
                    "parallelogram", "flowchart-data" -> "parallelogram"
                    "trapezoid", "flowchart-manual-operation" -> "trapezoid"
                    "pentagon" -> "pentagon"
                    "hexagon", "flowchart-preparation" -> "hexagon"
                    "octagon" -> "octagon"
                    "right-arrow" -> "rightArrow"
                    "left-arrow" -> "leftArrow"
                    "up-arrow" -> "upArrow"
                    "down-arrow" -> "downArrow"
                    "left-right-arrow" -> "leftRightArrow"
                    "cross" -> "plus"
                    "star4" -> "star4"
                    "star5" -> "star5"
                    "star6" -> "star6"
                    "star8" -> "star8"
                    "pentagon-right" -> "homePlate"
                    "chevron" -> "chevron"
                    "flowchart-terminator" -> "flowChartTerminator"
                    else -> "rect"
                }
            return PresetGeometry.outline(preset, null)
        }

        private fun fill(properties: OdfGraphicProperties): Pair<Int?, ImageSource?> =
            when (properties.fill) {
                "solid" -> (properties.fillColor ?: DEFAULT_FILL) to null
                "gradient" -> (styles.gradientColor(properties.gradientName) ?: properties.fillColor) to null
                "bitmap" -> null to imageSource(styles.fillImage(properties.fillImageName))
                else -> null to null
            }

        private fun stroke(properties: OdfGraphicProperties): Pair<Int?, Float> =
            when (properties.stroke) {
                "solid", "dash" -> (properties.strokeColor ?: Colors.BLACK) to (properties.strokeWidthPt?.takeIf { it > 0f } ?: 0.75f)
                else -> null to 0f
            }

        private fun imageSource(href: String?): ImageSource? {
            if (href == null) return null
            if (href.contains("://")) {
                context.warn(ConversionWarning.EXTERNAL_RESOURCES_SKIPPED)
                return null
            }
            return archive.imageSource(href.removePrefix("./")) ?: run {
                context.warn(ConversionWarning.IMAGES_SKIPPED)
                null
            }
        }

        private fun textBox(
            container: XmlElement,
            owner: XmlElement,
            properties: OdfGraphicProperties,
            defaultAnchor: VerticalAnchor,
        ): TextBox {
            val base = baseText(owner)
            val listStyle =
                styles.listStyleOf("presentation", owner.attr(PRESENTATION, "style-name"))
                    ?: styles.listStyleOf("graphic", owner.attr(DRAW, "style-name"))
            val isOutline = owner.attr(PRESENTATION, "class") == "outline"
            val paragraphs = ArrayList<SlideParagraph>()
            collectParagraphs(container, base, listStyle, -1, isOutline, paragraphs)
            val anchor =
                when (properties.verticalAlign) {
                    "middle" -> VerticalAnchor.MIDDLE
                    "bottom" -> VerticalAnchor.BOTTOM
                    "top" -> VerticalAnchor.TOP
                    else -> defaultAnchor
                }
            return TextBox(
                paragraphs = paragraphs,
                insets =
                    Insets(
                        properties.paddingLeft ?: 3.5f,
                        properties.paddingTop ?: 3.5f,
                        properties.paddingRight ?: 3.5f,
                        properties.paddingBottom ?: 3.5f,
                    ),
                anchor = anchor,
                shrinkToFit = properties.shrinkToFit == true,
            )
        }

        private fun collectParagraphs(
            container: XmlElement,
            base: OdfTextProperties,
            listStyle: XmlElement?,
            level: Int,
            outline: Boolean,
            output: MutableList<SlideParagraph>,
        ) {
            for (element in container.elements) {
                when {
                    element.matches(TEXT, "p") || element.matches(TEXT, "h") -> {
                        output.add(paragraph(element, base, null, 0))
                    }

                    element.matches(TEXT, "list") -> {
                        val style = styles.listStyle(element.attr(TEXT, "style-name")) ?: listStyle
                        val nextLevel = level + 1
                        var counter = 0
                        for (item in element.elements) {
                            if (!item.matches(TEXT, "list-item") && !item.matches(TEXT, "list-header")) continue
                            counter++
                            val levelStyle = OdfLists.levelStyle(style, nextLevel)
                            val marker =
                                if (item.matches(TEXT, "list-header")) {
                                    null
                                } else {
                                    OdfLists
                                        .marker(
                                            levelStyle,
                                            nextLevel,
                                            counter + OdfLists.startValue(levelStyle) - 1,
                                        )?.takeIf { it.isNotBlank() }
                                }
                            var first = true
                            for (child in item.elements) {
                                when {
                                    child.matches(TEXT, "p") || child.matches(TEXT, "h") -> {
                                        output.add(paragraph(child, base, if (first) marker else null, nextLevel))
                                        first = false
                                    }

                                    child.matches(TEXT, "list") -> {
                                        collectParagraphs(
                                            XmlElement("", "wrapper", emptyMap(), listOf(child)),
                                            base,
                                            style,
                                            nextLevel,
                                            outline,
                                            output,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun paragraph(
            element: XmlElement,
            base: OdfTextProperties,
            marker: String?,
            level: Int,
        ): SlideParagraph {
            val styleName = element.attr(TEXT, "style-name")
            val paragraphProperties = styles.paragraph("paragraph", styleName)
            val textProperties = base.overriddenBy(styles.text("paragraph", styleName))
            val collector = RunCollector(collapseWhitespace = true)
            inline(element, textProperties, collector)
            val runs = collector.take()
            val size = textProperties.sizePt ?: DEFAULT_SIZE
            val indent = LEVEL_INDENT * level.coerceAtLeast(0) + if (marker != null) BULLET_GAP else 0f
            return SlideParagraph(
                runs = runs,
                alignment = paragraphProperties.alignment ?: Alignment.START,
                bullet = marker,
                indentPt = indent,
                bulletIndentPt = LEVEL_INDENT * level.coerceAtLeast(0),
                spaceBeforePt = paragraphProperties.marginTopPt ?: 0f,
                spaceAfterPt = paragraphProperties.marginBottomPt ?: 0f,
                emptyLineSizePt = size,
            )
        }

        private fun inline(
            element: XmlElement,
            properties: OdfTextProperties,
            collector: RunCollector,
        ) {
            val style = properties.toTextStyle(DEFAULT_SIZE).let { if (it.color == null) it.copy(color = Colors.BLACK) else it }
            for (child in element.children) {
                when (child) {
                    is XmlText -> {
                        collector.append(child.text, style)
                    }

                    is XmlElement -> {
                        when {
                            child.matches(
                                TEXT,
                                "span",
                            ) -> inline(child, properties.overriddenBy(styles.text("text", child.attr(TEXT, "style-name"))), collector)

                            child.matches(
                                TEXT,
                                "s",
                            ) -> collector.appendPreserved(" ".repeat((child.attr(TEXT, "c")?.toIntOrNull() ?: 1).coerceIn(1, 200)), style)

                            child.matches(TEXT, "tab") -> collector.appendPreserved("\t", style)

                            child.matches(TEXT, "line-break") -> collector.appendLineBreak(style)

                            child.matches(TEXT, "a") -> inline(child, properties, collector)

                            child.namespace == TEXT && child.children.isNotEmpty() &&
                                !child.matches(
                                    TEXT,
                                    "note",
                                )
                            -> inline(child, properties, collector)
                        }
                    }
                }
            }
        }

        private fun table(
            table: XmlElement,
            frame: Frame,
            owner: XmlElement,
        ): TableElement {
            val base = baseText(owner)
            val rows = ArrayList<List<SlideTableCell>>()

            fun collectRows(container: XmlElement) {
                for (element in container.elements) {
                    when {
                        element.matches(TABLE, "table-row") -> {
                            val cells =
                                element.elements
                                    .filter { it.matches(TABLE, "table-cell") }
                                    .map { cell ->
                                        val paragraphs = ArrayList<SlideParagraph>()
                                        collectParagraphs(cell, base, null, -1, false, paragraphs)
                                        val cellFill = styles.graphic("table-cell", cell.attr(TABLE, "style-name")).fillColor
                                        SlideTableCell(
                                            TextBox(paragraphs, Insets(3.5f, 2f, 3.5f, 2f)),
                                            cellFill,
                                            cell.attr(TABLE, "number-columns-spanned")?.toIntOrNull() ?: 1,
                                        )
                                    }.toList()
                            if (cells.isNotEmpty()) rows.add(cells)
                        }

                        element.matches(TABLE, "table-header-rows") || element.matches(TABLE, "table-rows") ||
                            element.matches(TABLE, "table-row-group") -> {
                            collectRows(element)
                        }
                    }
                }
            }
            collectRows(table)
            val columns = rows.maxOfOrNull { row -> row.sumOf { it.columnSpan } }?.coerceAtLeast(1) ?: 1
            val rowHeight = if (rows.isEmpty()) frame.height else frame.height / rows.size
            return TableElement(frame, List(columns) { frame.width / columns }, rows.map { SlideTableRow(rowHeight, it) })
        }
    }

    private companion object {
        const val DEFAULT_WIDTH = 793.7f
        const val DEFAULT_HEIGHT = 446.4f
        const val DEFAULT_SIZE = 18f
        const val LEVEL_INDENT = 24f
        const val BULLET_GAP = 18f
        val DEFAULT_FILL = 0xFF729FCF.toInt()
    }
}
