package com.jvmd.notesbridge.formats.html

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.formats.common.FlowCollector
import com.jvmd.notesbridge.formats.common.ListNumbering
import com.jvmd.notesbridge.formats.common.NumberStyle
import com.jvmd.notesbridge.formats.common.ParagraphSpec
import com.jvmd.notesbridge.formats.common.RunCollector
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.Baseline
import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.model.FlowBlock
import com.jvmd.notesbridge.model.FontFamily
import com.jvmd.notesbridge.model.HorizontalRule
import com.jvmd.notesbridge.model.ImageBlock
import com.jvmd.notesbridge.model.ImageSource
import com.jvmd.notesbridge.model.PageBreak
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.ParagraphRole
import com.jvmd.notesbridge.model.Table
import com.jvmd.notesbridge.model.TableCell
import com.jvmd.notesbridge.model.TableRow
import com.jvmd.notesbridge.model.TextStyle
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Base64

class HtmlFlowBuilder(
    private val context: ConversionContext?,
    private val resolveResource: (String) -> ImageSource? = { null },
) {
    private val collector = FlowCollector()
    private var runs = RunCollector(collapseWhitespace = true)
    private var preformatted = 0
    private var pendingMarker: String? = null
    private var visited = 0

    fun build(root: Element): List<FlowBlock> {
        walkBlock(root, BlockContext(), TextStyle())
        flush(BlockContext())
        return collector.blocks
    }

    private data class BlockContext(
        val role: ParagraphRole = ParagraphRole.BODY,
        val headingLevel: Int = 0,
        val alignment: Alignment = Alignment.START,
        val indentPt: Float = 0f,
        val listDepth: Int = 0,
    )

    private fun walkBlock(
        element: Element,
        block: BlockContext,
        style: TextStyle,
    ) {
        for (child in element.childNodes()) walkNode(child, block, style)
    }

    private fun walkNode(
        node: Node,
        block: BlockContext,
        style: TextStyle,
    ) {
        visited++
        if (visited % 2000 == 0) context?.checkCancelled()
        when (node) {
            is TextNode -> appendText(node.wholeText, style)
            is Element -> walkElement(node, block, style)
        }
    }

    private fun appendText(
        text: String,
        style: TextStyle,
    ) {
        if (preformatted > 0) {
            val parts = text.split('\n')
            parts.forEachIndexed { index, part ->
                if (index > 0) runs.appendLineBreak(style)
                if (part.isNotEmpty()) runs.append(part, style)
            }
        } else {
            runs.append(text, style)
        }
    }

    private fun walkElement(
        element: Element,
        block: BlockContext,
        inherited: TextStyle,
    ) {
        val tag = element.normalName()
        if (tag in SKIPPED_TAGS) {
            if (tag in EMBEDDED_TAGS) context?.warn(ConversionWarning.EMBEDDED_OBJECTS_SKIPPED)
            return
        }
        val css = parseInlineCss(element.attr("style"))
        if (css["display"] == "none" || element.hasAttr("hidden")) return
        val style = applyInlineStyle(tag, element, css, inherited)
        val alignment = alignmentFrom(element, css) ?: block.alignment
        if (css["page-break-before"] == "always" || css["break-before"] == "page") {
            flush(block)
            collector.add(PageBreak)
        }
        when (tag) {
            "br" -> {
                runs.appendLineBreak(style)
            }

            "img" -> {
                image(element, block)
            }

            "svg" -> {
                svg(element, block)
            }

            "hr" -> {
                flush(block)
                collector.add(HorizontalRule)
            }

            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush(block)
                val heading = block.copy(role = ParagraphRole.HEADING, headingLevel = tag[1].digitToInt(), alignment = alignment)
                walkBlock(element, heading, style)
                flush(heading)
            }

            "pre" -> {
                flush(block)
                val code = block.copy(role = ParagraphRole.CODE, alignment = Alignment.START)
                preformatted++
                walkBlock(element, code, style.copy(family = FontFamily.MONO))
                preformatted--
                flush(code)
            }

            "blockquote" -> {
                flush(block)
                val quote = block.copy(role = ParagraphRole.QUOTE, indentPt = block.indentPt + 12f, alignment = alignment)
                walkBlock(element, quote, style)
                flush(quote)
            }

            "ul", "ol", "menu" -> {
                list(element, block, style, ordered = tag == "ol")
            }

            "li" -> {
                flush(block)
                val item = block.copy(indentPt = block.indentPt + 18f, alignment = alignment)
                pendingMarker = ListNumbering.bullet(block.listDepth)
                walkBlock(element, item, style)
                flush(item)
                pendingMarker = null
            }

            "dt" -> {
                flush(block)
                walkBlock(element, block, style.copy(bold = true))
                flush(block)
            }

            "dd" -> {
                flush(block)
                val definition = block.copy(indentPt = block.indentPt + 18f)
                walkBlock(element, definition, style)
                flush(definition)
            }

            "figcaption", "caption" -> {
                flush(block)
                val caption = block.copy(role = ParagraphRole.CAPTION, alignment = Alignment.CENTER)
                walkBlock(element, caption, style)
                flush(caption)
            }

            "table" -> {
                table(element, block)
            }

            "q" -> {
                runs.append("“", style)
                walkBlock(element, block, style)
                runs.append("”", style)
            }

            "math" -> {
                runs.append(element.text(), style)
            }

            else -> {
                if (tag in BLOCK_TAGS) {
                    flush(block)
                    val nested = block.copy(alignment = alignment)
                    walkBlock(element, nested, style)
                    flush(nested)
                } else {
                    walkBlock(element, block, style)
                }
            }
        }
    }

    private fun list(
        element: Element,
        block: BlockContext,
        style: TextStyle,
        ordered: Boolean,
    ) {
        flush(block)
        val numberStyle =
            when (element.attr("type")) {
                "a" -> NumberStyle.LOWER_ALPHA
                "A" -> NumberStyle.UPPER_ALPHA
                "i" -> NumberStyle.LOWER_ROMAN
                "I" -> NumberStyle.UPPER_ROMAN
                else -> NumberStyle.DECIMAL
            }
        var counter = element.attr("start").toIntOrNull() ?: 1
        val nested = block.copy(listDepth = block.listDepth + 1)
        for (child in element.childNodes()) {
            if (child is Element && child.normalName() == "li") {
                child.attr("value").toIntOrNull()?.let { counter = it }
                val item = nested.copy(indentPt = block.indentPt + 18f)
                pendingMarker = if (ordered) ListNumbering.format(counter, numberStyle) + "." else ListNumbering.bullet(block.listDepth)
                walkBlock(child, item, applyInlineStyle("li", child, parseInlineCss(child.attr("style")), style))
                flush(item)
                pendingMarker = null
                counter++
            } else {
                walkNode(child, nested, style)
            }
        }
        flush(block)
    }

    private fun table(
        element: Element,
        block: BlockContext,
    ) {
        flush(block)
        val rows = ArrayList<TableRow>()
        var headerRows = 0
        var seenBody = false
        for (row in element.select("tr")) {
            if (row.closest("table") !== element) continue
            val cells =
                row.children().filter { it.normalName() == "td" || it.normalName() == "th" }.map { cell ->
                    val nested = HtmlFlowBuilder(context, resolveResource)
                    val paragraphs = nested.build(cell).flatMap { flattenToParagraphs(it) }
                    TableCell(paragraphs, cell.attr("colspan").toIntOrNull()?.coerceIn(1, 100) ?: 1)
                }
            if (cells.isEmpty()) continue
            val isHeader = row.parent()?.normalName() == "thead" || row.children().all { it.normalName() == "th" }
            if (isHeader && !seenBody) headerRows++ else seenBody = true
            rows.add(TableRow(cells))
        }
        if (rows.isNotEmpty()) collector.add(Table(rows, headerRowCount = headerRows))
    }

    private fun flattenToParagraphs(block: FlowBlock): List<Paragraph> =
        when (block) {
            is Paragraph -> {
                listOf(block)
            }

            is Table -> {
                block.rows.flatMap { row -> row.cells.flatMap { it.paragraphs } }
            }

            is ImageBlock -> {
                context?.warn(ConversionWarning.IMAGES_SKIPPED)
                emptyList()
            }

            else -> {
                emptyList()
            }
        }

    private fun image(
        element: Element,
        block: BlockContext,
    ) {
        val source = resolveImage(element.attr("src").ifEmpty { element.attr("data-src") }) ?: return
        flush(block)
        collector.add(ImageBlock(source, pxToPt(element.attr("width")), pxToPt(element.attr("height"))))
    }

    private fun svg(
        element: Element,
        block: BlockContext,
    ) {
        val image = element.getElementsByTag("image").firstOrNull()
        if (image != null) {
            val href = image.attr("xlink:href").ifEmpty { image.attr("href") }
            val source = resolveImage(href) ?: return
            flush(block)
            collector.add(ImageBlock(source))
            return
        }
        val markup = element.outerHtml()
        if (!markup.contains("xmlns")) {
            element.attr("xmlns", "http://www.w3.org/2000/svg")
        }
        val bytes = element.outerHtml().toByteArray(Charsets.UTF_8)
        flush(block)
        collector.add(ImageBlock(ImageSource { bytes.inputStream() }, pxToPt(element.attr("width")), pxToPt(element.attr("height"))))
    }

    private fun resolveImage(reference: String): ImageSource? {
        val trimmed = reference.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            val header = trimmed.substringBefore(',')
            val payload = trimmed.substringAfter(',', "")
            val bytes =
                runCatching {
                    if (header.contains(";base64", ignoreCase = true)) {
                        Base64.getMimeDecoder().decode(payload)
                    } else {
                        java.net.URLDecoder
                            .decode(payload, Charsets.UTF_8.name())
                            .toByteArray(Charsets.UTF_8)
                    }
                }.getOrNull()
            if (bytes == null) {
                context?.warn(ConversionWarning.IMAGES_SKIPPED)
                return null
            }
            return ImageSource { bytes.inputStream() }
        }
        val lower = trimmed.lowercase()
        if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("//") || lower.startsWith("file:") ||
            lower.startsWith("content:")
        ) {
            context?.warn(ConversionWarning.EXTERNAL_RESOURCES_SKIPPED)
            return null
        }
        val resolved = resolveResource(trimmed)
        if (resolved == null) context?.warn(ConversionWarning.EXTERNAL_RESOURCES_SKIPPED)
        return resolved
    }

    private fun flush(block: BlockContext) {
        if (runs.isEmpty) return
        val taken = runs.take()
        if (taken.all { it.text.isBlank() }) return
        collector.addParagraph(
            taken,
            ParagraphSpec(block.role, block.headingLevel, block.alignment, block.indentPt, pendingMarker),
        )
        pendingMarker = null
    }

    private fun applyInlineStyle(
        tag: String,
        element: Element,
        css: Map<String, String>,
        inherited: TextStyle,
    ): TextStyle {
        var style = inherited
        when (tag) {
            "b", "strong" -> style = style.copy(bold = true)
            "i", "em", "cite", "var", "dfn", "address" -> style = style.copy(italic = true)
            "u", "ins" -> style = style.copy(underline = true)
            "s", "strike", "del" -> style = style.copy(strikethrough = true)
            "code", "kbd", "samp", "tt" -> style = style.copy(family = FontFamily.MONO)
            "sup" -> style = style.copy(baseline = Baseline.SUPERSCRIPT)
            "sub" -> style = style.copy(baseline = Baseline.SUBSCRIPT)
            "a" -> if (element.hasAttr("href")) style = style.copy(color = LINK_COLOR, underline = true)
            "th" -> style = style.copy(bold = true)
            "font" -> Colors.parseCss(element.attr("color"))?.let { style = style.copy(color = it) }
        }
        css["font-weight"]?.let { weight ->
            val numeric = weight.toIntOrNull()
            style = style.copy(bold = weight == "bold" || weight == "bolder" || (numeric != null && numeric >= 600))
        }
        css["font-style"]?.let { style = style.copy(italic = it == "italic" || it == "oblique") }
        css["text-decoration"]?.let {
            if (it.contains("underline")) style = style.copy(underline = true)
            if (it.contains("line-through")) style = style.copy(strikethrough = true)
        }
        css["color"]?.let { value -> Colors.parseCss(value)?.let { style = style.copy(color = it) } }
        return style
    }

    private fun alignmentFrom(
        element: Element,
        css: Map<String, String>,
    ): Alignment? {
        val value =
            css["text-align"] ?: element.attr("align").lowercase().ifEmpty { null }
                ?: if (element.normalName() == "center") "center" else null
        return when (value) {
            "center" -> Alignment.CENTER
            "right", "end" -> Alignment.END
            "justify" -> Alignment.JUSTIFY
            "left", "start" -> Alignment.START
            else -> null
        }
    }

    private fun parseInlineCss(style: String): Map<String, String> {
        if (style.isBlank()) return emptyMap()
        return style
            .split(';')
            .mapNotNull { declaration ->
                val name = declaration.substringBefore(':', "").trim().lowercase()
                val value =
                    declaration
                        .substringAfter(':', "")
                        .trim()
                        .lowercase()
                        .removeSuffix("!important")
                        .trim()
                if (name.isEmpty() || value.isEmpty()) null else name to value
            }.toMap()
    }

    private fun pxToPt(value: String): Float? =
        value
            .trim()
            .removeSuffix("px")
            .toFloatOrNull()
            ?.takeIf { it > 0 }
            ?.let { it * 0.75f }

    private companion object {
        val LINK_COLOR = 0xFF1A0DAB.toInt()
        val SKIPPED_TAGS =
            setOf(
                "script",
                "style",
                "template",
                "head",
                "title",
                "meta",
                "link",
                "iframe",
                "object",
                "embed",
                "video",
                "audio",
                "canvas",
                "noembed",
                "input",
                "select",
                "textarea",
                "button",
                "map",
                "area",
                "track",
                "source",
                "param",
            )
        val EMBEDDED_TAGS = setOf("iframe", "object", "embed", "video", "audio", "canvas")
        val BLOCK_TAGS =
            setOf(
                "p",
                "div",
                "section",
                "article",
                "header",
                "footer",
                "main",
                "nav",
                "aside",
                "figure",
                "center",
                "address",
                "dl",
                "form",
                "fieldset",
                "legend",
                "details",
                "summary",
                "body",
                "html",
                "hgroup",
                "tbody",
                "thead",
                "tfoot",
                "tr",
            )
    }
}
