package com.jvmd.notesbridge.formats.odf

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.common.RunCollector
import com.jvmd.notesbridge.formats.common.Units
import com.jvmd.notesbridge.io.DocumentArchive
import com.jvmd.notesbridge.model.Alignment
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
import com.jvmd.notesbridge.xml.Namespaces.DRAW
import com.jvmd.notesbridge.xml.Namespaces.FO
import com.jvmd.notesbridge.xml.Namespaces.OFFICE
import com.jvmd.notesbridge.xml.Namespaces.SVG
import com.jvmd.notesbridge.xml.Namespaces.TABLE
import com.jvmd.notesbridge.xml.Namespaces.TEXT
import com.jvmd.notesbridge.xml.Namespaces.XLINK
import com.jvmd.notesbridge.xml.XmlElement
import com.jvmd.notesbridge.xml.XmlText

class OdtReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument {
        val archive = input.archive
        val content =
            archive.open("content.xml")?.use { context.xml.read(it) } ?: throw ConversionException.Corrupted("missing content.xml")
        val stylesRoot = archive.open("styles.xml")?.use { context.xml.read(it) }
        val styles = OdfStyles(stylesRoot, content)
        val text = content.path(OFFICE, "body", "text") ?: throw ConversionException.Corrupted("document has no text body")
        val converter = OdtConverter(archive, styles, context)
        converter.convertContainer(text, converter.blocks, ListContext())
        return FlowDocument(converter.blocks, pageSetup(styles))
    }

    private fun pageSetup(styles: OdfStyles): PageSetup {
        val layout = styles.pageLayoutOf(styles.masterPage("Standard")) ?: return PageSetup.A4_PORTRAIT
        val width = Units.lengthToPt(layout.attr(FO, "page-width")) ?: return PageSetup.A4_PORTRAIT
        val height = Units.lengthToPt(layout.attr(FO, "page-height")) ?: return PageSetup.A4_PORTRAIT
        val setup =
            PageSetup(
                widthPt = width,
                heightPt = height,
                marginTopPt = Units.lengthToPt(layout.attr(FO, "margin-top"))?.coerceIn(18f, 144f) ?: 56f,
                marginBottomPt = Units.lengthToPt(layout.attr(FO, "margin-bottom"))?.coerceIn(18f, 144f) ?: 56f,
                marginStartPt = Units.lengthToPt(layout.attr(FO, "margin-left"))?.coerceIn(18f, 144f) ?: 56f,
                marginEndPt = Units.lengthToPt(layout.attr(FO, "margin-right"))?.coerceIn(18f, 144f) ?: 56f,
            )
        return if (setup.isUsable()) setup else PageSetup.A4_PORTRAIT
    }

    data class ListContext(
        val level: Int = -1,
        val listStyle: XmlElement? = null,
        val counters: IntArray = IntArray(10),
    )

    private class OdtConverter(
        private val archive: DocumentArchive,
        private val styles: OdfStyles,
        private val context: ConversionContext,
    ) {
        val blocks = ArrayList<FlowBlock>()
        private var visited = 0

        fun convertContainer(
            container: XmlElement,
            target: MutableList<FlowBlock>,
            list: ListContext,
        ) {
            for (element in container.elements) {
                visited++
                if (visited % 200 == 0) context.checkCancelled()
                when {
                    element.matches(TEXT, "p") -> {
                        convertParagraph(element, target, null, null)
                    }

                    element.matches(TEXT, "h") -> {
                        val level =
                            element.attr(TEXT, "outline-level")?.toIntOrNull() ?: styles.outlineLevelOf(element.attr(TEXT, "style-name"))
                                ?: 1
                        convertParagraph(element, target, level.coerceIn(1, 6), null)
                    }

                    element.matches(TEXT, "list") -> {
                        convertList(element, target, list)
                    }

                    element.matches(TABLE, "table") -> {
                        convertTable(element, target)
                    }

                    element.matches(TEXT, "section") || element.matches(TEXT, "index-body") || element.matches(TEXT, "index-title") -> {
                        convertContainer(element, target, list)
                    }

                    (element.namespace == TEXT && element.name.endsWith("index")) || element.matches(TEXT, "table-of-content") ||
                        element.matches(TEXT, "bibliography") -> {
                        element.child(TEXT, "index-body")?.let { convertContainer(it, target, list) }
                    }

                    element.matches(DRAW, "frame") -> {
                        convertFrame(element, target)
                    }
                }
            }
        }

        private fun convertList(
            list: XmlElement,
            target: MutableList<FlowBlock>,
            parent: ListContext,
        ) {
            val listStyle = styles.listStyle(list.attr(TEXT, "style-name")) ?: parent.listStyle
            val level = parent.level + 1
            val counters = if (parent.level < 0 && list.attr(TEXT, "continue-numbering") != "true") IntArray(10) else parent.counters
            val levelStyle = OdfLists.levelStyle(listStyle, level)
            if (level < counters.size && list.attr(TEXT, "continue-numbering") != "true" && parent.level >= 0) {
                counters[level] = 0
            }
            val nested = ListContext(level, listStyle, counters)
            for (item in list.elements) {
                if (!item.matches(TEXT, "list-item") && !item.matches(TEXT, "list-header")) continue
                val isHeader = item.matches(TEXT, "list-header")
                item.attr(TEXT, "start-value")?.toIntOrNull()?.let { counters[level.coerceAtMost(9)] = it - 1 }
                var markerPending: String? = null
                if (!isHeader) {
                    val index = level.coerceAtMost(9)
                    if (counters[index] == 0) counters[index] = OdfLists.startValue(levelStyle) - 1
                    counters[index]++
                    for (deeper in index + 1 until counters.size) counters[deeper] = 0
                    markerPending = OdfLists.marker(levelStyle, level, counters[index])
                }
                val indent = 18f * (level + 1)
                for (child in item.elements) {
                    when {
                        child.matches(TEXT, "p") || child.matches(TEXT, "h") -> {
                            convertParagraph(child, target, null, ListItem(markerPending, indent))
                            markerPending = null
                        }

                        child.matches(TEXT, "list") -> {
                            convertList(child, target, nested)
                        }

                        else -> {
                            convertContainer(XmlElementWrapper.single(child), target, nested)
                        }
                    }
                }
            }
        }

        data class ListItem(
            val marker: String?,
            val indentPt: Float,
        )

        private fun convertParagraph(
            element: XmlElement,
            target: MutableList<FlowBlock>,
            headingLevel: Int?,
            listItem: ListItem?,
        ) {
            val styleName = element.attr(TEXT, "style-name")
            val paragraphProperties = styles.paragraph("paragraph", styleName)
            if (paragraphProperties.breakBefore == true && target.isNotEmpty() && target.last() !is PageBreak) target.add(PageBreak)
            val baseText = styles.text("paragraph", styleName)
            if (baseText.hidden == true) return
            val collector = RunCollector(collapseWhitespace = true)
            val trailing = ArrayList<FlowBlock>()
            val paragraphFactory = { runs: List<com.jvmd.notesbridge.model.TextRun>, marker: String? ->
                Paragraph(
                    runs = runs,
                    role = if (headingLevel != null) ParagraphRole.HEADING else ParagraphRole.BODY,
                    headingLevel = headingLevel ?: 0,
                    alignment = paragraphProperties.alignment ?: Alignment.START,
                    indentPt = listItem?.indentPt ?: (paragraphProperties.marginLeftPt ?: 0f).coerceAtLeast(0f),
                    listMarker = marker,
                    spaceBeforePt = paragraphProperties.marginTopPt ?: 0f,
                    spaceAfterPt = paragraphProperties.marginBottomPt ?: 0f,
                )
            }
            var marker = listItem?.marker
            var emitted = false
            val flush = { force: Boolean ->
                val runs = collector.take()
                if (runs.isNotEmpty() || force || marker != null) {
                    target.add(paragraphFactory(runs, marker))
                    marker = null
                    emitted = true
                }
            }
            inline(element, baseText, collector, trailing) { image ->
                flush(false)
                target.add(image)
                emitted = true
            }
            if (!collector.isEmpty || !emitted) flush(true)
            target.addAll(trailing)
            if (paragraphProperties.breakAfter == true) target.add(PageBreak)
        }

        private fun inline(
            element: XmlElement,
            properties: OdfTextProperties,
            collector: RunCollector,
            trailing: MutableList<FlowBlock>,
            onImage: (ImageBlock) -> Unit,
        ) {
            val style = properties.toTextStyle(DEFAULT_SIZE_PT)
            for (child in element.children) {
                when (child) {
                    is XmlText -> {
                        collector.append(child.text, style)
                    }

                    is XmlElement -> {
                        when {
                            child.matches(TEXT, "span") -> {
                                val spanProperties = properties.overriddenBy(styles.text("text", child.attr(TEXT, "style-name")))
                                if (spanProperties.hidden == true) {
                                    context.warn(ConversionWarning.HIDDEN_CONTENT_SKIPPED)
                                } else {
                                    inline(child, spanProperties, collector, trailing, onImage)
                                }
                            }

                            child.matches(TEXT, "a") || child.matches(TEXT, "meta") || child.matches(TEXT, "ruby") ||
                                child.matches(TEXT, "ruby-base") -> {
                                inline(child, properties, collector, trailing, onImage)
                            }

                            child.matches(TEXT, "s") -> {
                                collector.appendPreserved(" ".repeat((child.attr(TEXT, "c")?.toIntOrNull() ?: 1).coerceIn(1, 200)), style)
                            }

                            child.matches(TEXT, "tab") -> {
                                collector.appendPreserved("\t", style)
                            }

                            child.matches(TEXT, "line-break") -> {
                                collector.appendLineBreak(style)
                            }

                            child.matches(TEXT, "note") || child.matches(TEXT, "annotation") || child.matches(TEXT, "bookmark") ||
                                child.matches(TEXT, "bookmark-start") -> {
                                Unit
                            }

                            child.matches(DRAW, "frame") -> {
                                frameInline(child, trailing, onImage)
                            }

                            child.matches(DRAW, "a") -> {
                                child.child(DRAW, "frame")?.let { frameInline(it, trailing, onImage) }
                            }

                            child.namespace == TEXT && child.children.isNotEmpty() -> {
                                inline(child, properties, collector, trailing, onImage)
                            }
                        }
                    }
                }
            }
        }

        private fun frameInline(
            frame: XmlElement,
            trailing: MutableList<FlowBlock>,
            onImage: (ImageBlock) -> Unit,
        ) {
            val image = imageBlock(frame)
            if (image != null) {
                onImage(image)
                return
            }
            val textBox = frame.child(DRAW, "text-box")
            if (textBox != null) {
                convertContainer(textBox, trailing, ListContext())
            } else if (frame.child(DRAW, "object") != null || frame.child(DRAW, "object-ole") != null) {
                context.warn(ConversionWarning.EMBEDDED_OBJECTS_SKIPPED)
            }
        }

        private fun convertFrame(
            frame: XmlElement,
            target: MutableList<FlowBlock>,
        ) {
            frameInline(frame, target) { target.add(it) }
        }

        private fun imageBlock(frame: XmlElement): ImageBlock? {
            val image = frame.child(DRAW, "image") ?: return null
            val href = image.attr(XLINK, "href")
            if (href == null) {
                context.warn(ConversionWarning.IMAGES_SKIPPED)
                return null
            }
            if (href.contains("://")) {
                context.warn(ConversionWarning.EXTERNAL_RESOURCES_SKIPPED)
                return null
            }
            val source = archive.imageSource(href.removePrefix("./"))
            if (source == null) {
                context.warn(ConversionWarning.IMAGES_SKIPPED)
                return null
            }
            return ImageBlock(source, Units.lengthToPt(frame.attr(SVG, "width")), Units.lengthToPt(frame.attr(SVG, "height")))
        }

        private fun convertTable(
            table: XmlElement,
            target: MutableList<FlowBlock>,
        ) {
            val rows = ArrayList<TableRow>()
            var headerRows = 0

            fun collectRows(
                container: XmlElement,
                header: Boolean,
            ) {
                for (element in container.elements) {
                    when {
                        element.matches(TABLE, "table-header-rows") -> {
                            collectRows(element, true)
                        }

                        element.matches(TABLE, "table-rows") || element.matches(TABLE, "table-row-group") -> {
                            collectRows(element, header)
                        }

                        element.matches(TABLE, "table-row") -> {
                            val cells =
                                element.elements
                                    .filter { it.matches(TABLE, "table-cell") }
                                    .map { cell ->
                                        val content = ArrayList<FlowBlock>()
                                        convertContainer(cell, content, ListContext())
                                        val paragraphs =
                                            content.flatMap { block ->
                                                when (block) {
                                                    is Paragraph -> {
                                                        listOf(block)
                                                    }

                                                    is Table -> {
                                                        block.rows.flatMap { row -> row.cells.flatMap { it.paragraphs } }
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
                                        TableCell(
                                            paragraphs,
                                            cell.attr(TABLE, "number-columns-spanned")?.toIntOrNull()?.coerceIn(1, 64) ?: 1,
                                        )
                                    }.toList()
                            if (cells.isNotEmpty()) {
                                rows.add(TableRow(cells))
                                if (header) headerRows++
                            }
                        }
                    }
                }
            }
            collectRows(table, false)
            if (rows.isNotEmpty()) target.add(Table(rows, headerRowCount = headerRows))
        }
    }

    private object XmlElementWrapper {
        fun single(element: XmlElement): XmlElement = XmlElement("", "wrapper", emptyMap(), listOf(element))
    }

    private companion object {
        const val DEFAULT_SIZE_PT = 11f
    }
}
