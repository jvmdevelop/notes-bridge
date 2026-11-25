package com.jvmd.notesbridge.formats.odf

import com.jvmd.notesbridge.formats.common.Units
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.Baseline
import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.model.FontFamily
import com.jvmd.notesbridge.model.TextStyle
import com.jvmd.notesbridge.xml.Namespaces.DRAW
import com.jvmd.notesbridge.xml.Namespaces.FO
import com.jvmd.notesbridge.xml.Namespaces.OFFICE
import com.jvmd.notesbridge.xml.Namespaces.STYLE
import com.jvmd.notesbridge.xml.Namespaces.SVG
import com.jvmd.notesbridge.xml.Namespaces.TEXT
import com.jvmd.notesbridge.xml.XmlElement

data class OdfTextProperties(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val sizePt: Float? = null,
    val sizePercent: Float? = null,
    val color: Int? = null,
    val baseline: Baseline? = null,
    val family: FontFamily? = null,
    val hidden: Boolean? = null,
) {
    fun overriddenBy(other: OdfTextProperties?): OdfTextProperties {
        if (other == null) return this
        val resolvedSize =
            when {
                other.sizePt != null -> other.sizePt
                other.sizePercent != null && sizePt != null -> sizePt * other.sizePercent / 100f
                else -> sizePt
            }
        return OdfTextProperties(
            bold = other.bold ?: bold,
            italic = other.italic ?: italic,
            underline = other.underline ?: underline,
            strike = other.strike ?: strike,
            sizePt = resolvedSize,
            sizePercent = if (resolvedSize == null) other.sizePercent ?: sizePercent else null,
            color = other.color ?: color,
            baseline = other.baseline ?: baseline,
            family = other.family ?: family,
            hidden = other.hidden ?: hidden,
        )
    }

    fun toTextStyle(defaultSizePt: Float?): TextStyle =
        TextStyle(
            bold = bold,
            italic = italic ?: false,
            underline = underline ?: false,
            strikethrough = strike ?: false,
            family = family,
            fontSizePt = sizePt ?: defaultSizePt?.let { size -> sizePercent?.let { size * it / 100f } ?: size },
            color = color,
            baseline = baseline ?: Baseline.NORMAL,
        )

    companion object {
        fun parse(element: XmlElement?): OdfTextProperties? {
            if (element == null) return null
            val size = element.attr(FO, "font-size")
            val position = element.attr(STYLE, "text-position")
            return OdfTextProperties(
                bold = element.attr(FO, "font-weight")?.let { it == "bold" || (it.toIntOrNull() ?: 400) >= 600 },
                italic = element.attr(FO, "font-style")?.let { it == "italic" || it == "oblique" },
                underline = element.attr(STYLE, "text-underline-style")?.let { it != "none" },
                strike = element.attr(STYLE, "text-line-through-style")?.let { it != "none" },
                sizePt = size?.takeUnless { it.endsWith("%") }?.let { Units.lengthToPt(it) },
                sizePercent = size?.takeIf { it.endsWith("%") }?.removeSuffix("%")?.toFloatOrNull(),
                color = Colors.parseHex(element.attr(FO, "color")),
                baseline =
                    when {
                        position == null -> null
                        position.startsWith("super") || position.startsWith("33%") -> Baseline.SUPERSCRIPT
                        position.startsWith("sub") || position.startsWith("-") -> Baseline.SUBSCRIPT
                        position.startsWith("0%") -> Baseline.NORMAL
                        else -> null
                    },
                family = Units.fontFamilyFor(element.attr(STYLE, "font-name") ?: element.attr(FO, "font-family")),
                hidden = element.attr(TEXT, "display")?.let { it == "none" },
            )
        }
    }
}

data class OdfParagraphProperties(
    val alignment: Alignment? = null,
    val marginLeftPt: Float? = null,
    val marginTopPt: Float? = null,
    val marginBottomPt: Float? = null,
    val breakBefore: Boolean? = null,
    val breakAfter: Boolean? = null,
) {
    fun overriddenBy(other: OdfParagraphProperties?): OdfParagraphProperties {
        if (other == null) return this
        return OdfParagraphProperties(
            alignment = other.alignment ?: alignment,
            marginLeftPt = other.marginLeftPt ?: marginLeftPt,
            marginTopPt = other.marginTopPt ?: marginTopPt,
            marginBottomPt = other.marginBottomPt ?: marginBottomPt,
            breakBefore = other.breakBefore ?: breakBefore,
            breakAfter = other.breakAfter ?: breakAfter,
        )
    }

    companion object {
        fun parse(element: XmlElement?): OdfParagraphProperties? {
            if (element == null) return null
            return OdfParagraphProperties(
                alignment =
                    when (element.attr(FO, "text-align")) {
                        "center" -> Alignment.CENTER
                        "end", "right" -> Alignment.END
                        "justify" -> Alignment.JUSTIFY
                        "start", "left" -> Alignment.START
                        else -> null
                    },
                marginLeftPt = Units.lengthToPt(element.attr(FO, "margin-left")),
                marginTopPt = Units.lengthToPt(element.attr(FO, "margin-top")),
                marginBottomPt = Units.lengthToPt(element.attr(FO, "margin-bottom")),
                breakBefore = element.attr(FO, "break-before")?.let { it == "page" },
                breakAfter = element.attr(FO, "break-after")?.let { it == "page" },
            )
        }
    }
}

data class OdfGraphicProperties(
    val fill: String? = null,
    val fillColor: Int? = null,
    val fillImageName: String? = null,
    val gradientName: String? = null,
    val stroke: String? = null,
    val strokeColor: Int? = null,
    val strokeWidthPt: Float? = null,
    val verticalAlign: String? = null,
    val paddingLeft: Float? = null,
    val paddingTop: Float? = null,
    val paddingRight: Float? = null,
    val paddingBottom: Float? = null,
    val shrinkToFit: Boolean? = null,
) {
    fun overriddenBy(other: OdfGraphicProperties?): OdfGraphicProperties {
        if (other == null) return this
        return OdfGraphicProperties(
            fill = other.fill ?: fill,
            fillColor = other.fillColor ?: fillColor,
            fillImageName = other.fillImageName ?: fillImageName,
            gradientName = other.gradientName ?: gradientName,
            stroke = other.stroke ?: stroke,
            strokeColor = other.strokeColor ?: strokeColor,
            strokeWidthPt = other.strokeWidthPt ?: strokeWidthPt,
            verticalAlign = other.verticalAlign ?: verticalAlign,
            paddingLeft = other.paddingLeft ?: paddingLeft,
            paddingTop = other.paddingTop ?: paddingTop,
            paddingRight = other.paddingRight ?: paddingRight,
            paddingBottom = other.paddingBottom ?: paddingBottom,
            shrinkToFit = other.shrinkToFit ?: shrinkToFit,
        )
    }

    companion object {
        fun parse(element: XmlElement?): OdfGraphicProperties? {
            if (element == null) return null
            val padding = Units.lengthToPt(element.attr(FO, "padding"))
            return OdfGraphicProperties(
                fill = element.attr(DRAW, "fill"),
                fillColor = Colors.parseHex(element.attr(DRAW, "fill-color")),
                fillImageName = element.attr(DRAW, "fill-image-name"),
                gradientName = element.attr(DRAW, "fill-gradient-name"),
                stroke = element.attr(DRAW, "stroke"),
                strokeColor = Colors.parseHex(element.attr(SVG, "stroke-color")),
                strokeWidthPt = Units.lengthToPt(element.attr(SVG, "stroke-width")),
                verticalAlign = element.attr(DRAW, "textarea-vertical-align"),
                paddingLeft = Units.lengthToPt(element.attr(FO, "padding-left")) ?: padding,
                paddingTop = Units.lengthToPt(element.attr(FO, "padding-top")) ?: padding,
                paddingRight = Units.lengthToPt(element.attr(FO, "padding-right")) ?: padding,
                paddingBottom = Units.lengthToPt(element.attr(FO, "padding-bottom")) ?: padding,
                shrinkToFit =
                    element.attr(DRAW, "fit-to-size")?.let { it == "shrink-to-fit" || it == "true" }
                        ?: element.attr(STYLE, "shrink-to-fit")?.let { it == "true" },
            )
        }
    }
}

class OdfStyles(
    vararg roots: XmlElement?,
) {
    private val styles = HashMap<Pair<String, String>, XmlElement>()
    private val defaults = HashMap<String, XmlElement>()
    private val listStyles = HashMap<String, XmlElement>()
    private val fillImages = HashMap<String, String>()
    private val gradients = HashMap<String, Int>()
    private val masterPages = HashMap<String, XmlElement>()
    private val pageLayouts = HashMap<String, XmlElement>()
    private var firstMasterPage: XmlElement? = null

    init {
        for (root in roots) {
            if (root == null) continue
            for (section in listOf(
                root.child(OFFICE, "styles"),
                root.child(OFFICE, "automatic-styles"),
                root.child(OFFICE, "master-styles"),
            )) {
                section ?: continue
                for (element in section.elements) {
                    when {
                        element.matches(STYLE, "style") -> {
                            val name = element.attr(STYLE, "name") ?: continue
                            styles[(element.attr(STYLE, "family") ?: "") to name] = element
                        }

                        element.matches(STYLE, "default-style") -> {
                            element.attr(STYLE, "family")?.let { defaults[it] = element }
                        }

                        element.matches(TEXT, "list-style") -> {
                            element.attr(STYLE, "name")?.let { listStyles[it] = element }
                        }

                        element.matches(DRAW, "fill-image") -> {
                            val name = element.attr(DRAW, "name") ?: continue
                            element.attr(com.jvmd.notesbridge.xml.Namespaces.XLINK, "href")?.let { fillImages[name] = it }
                        }

                        element.matches(DRAW, "gradient") -> {
                            val name = element.attr(DRAW, "name") ?: continue
                            Colors.parseHex(element.attr(DRAW, "start-color"))?.let { gradients[name] = it }
                        }

                        element.matches(STYLE, "master-page") -> {
                            val name = element.attr(STYLE, "name") ?: continue
                            masterPages[name] = element
                            if (firstMasterPage == null) firstMasterPage = element
                        }

                        element.matches(STYLE, "page-layout") -> {
                            element.attr(STYLE, "name")?.let { pageLayouts[it] = element }
                        }
                    }
                }
            }
        }
    }

    private fun chain(
        family: String,
        name: String?,
    ): List<XmlElement> {
        val result = ArrayList<XmlElement>()
        var current = name?.let { styles[family to it] }
        val seen = HashSet<String>()
        while (current != null && seen.add(current.attr(STYLE, "name").orEmpty())) {
            result.add(0, current)
            current = current.attr(STYLE, "parent-style-name")?.let { styles[family to it] }
        }
        defaults[family]?.let { result.add(0, it) }
        return result
    }

    fun text(
        family: String,
        name: String?,
    ): OdfTextProperties =
        chain(
            family,
            name,
        ).fold(OdfTextProperties()) { acc, style -> acc.overriddenBy(OdfTextProperties.parse(style.child(STYLE, "text-properties"))) }

    fun paragraph(
        family: String,
        name: String?,
    ): OdfParagraphProperties =
        chain(
            family,
            name,
        ).fold(
            OdfParagraphProperties(),
        ) { acc, style -> acc.overriddenBy(OdfParagraphProperties.parse(style.child(STYLE, "paragraph-properties"))) }

    fun graphic(
        family: String,
        name: String?,
    ): OdfGraphicProperties =
        chain(family, name).fold(OdfGraphicProperties()) { acc, style ->
            acc.overriddenBy(
                OdfGraphicProperties.parse(style.child(STYLE, "graphic-properties") ?: style.child(STYLE, "drawing-page-properties")),
            )
        }

    fun drawingPage(name: String?): OdfGraphicProperties =
        chain(
            "drawing-page",
            name,
        ).fold(
            OdfGraphicProperties(),
        ) { acc, style -> acc.overriddenBy(OdfGraphicProperties.parse(style.child(STYLE, "drawing-page-properties"))) }

    fun styleExists(
        family: String,
        name: String?,
    ): Boolean = name != null && styles.containsKey(family to name)

    fun listStyleOf(
        family: String,
        name: String?,
    ): XmlElement? {
        for (style in chain(family, name).asReversed()) {
            style.attr(STYLE, "list-style-name")?.let { listName -> listStyles[listName]?.let { return it } }
            style.child(STYLE, "graphic-properties")?.child(TEXT, "list-style")?.let { return it }
        }
        return null
    }

    fun listStyle(name: String?): XmlElement? = name?.let { listStyles[it] }

    fun fillImage(name: String?): String? = name?.let { fillImages[it] }

    fun gradientColor(name: String?): Int? = name?.let { gradients[it] }

    fun masterPage(name: String?): XmlElement? = name?.let { masterPages[it] } ?: firstMasterPage

    fun pageLayoutOf(masterPage: XmlElement?): XmlElement? =
        masterPage?.attr(STYLE, "page-layout-name")?.let { pageLayouts[it] }?.child(STYLE, "page-layout-properties")

    fun isParagraphBreakingStyle(name: String?): Boolean = paragraph("paragraph", name).breakBefore == true

    fun outlineLevelOf(name: String?): Int? =
        chain("paragraph", name).asReversed().firstNotNullOfOrNull { it.attr(STYLE, "default-outline-level")?.toIntOrNull() }
}
