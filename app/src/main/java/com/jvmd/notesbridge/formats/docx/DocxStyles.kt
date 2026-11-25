package com.jvmd.notesbridge.formats.docx

import com.jvmd.notesbridge.formats.common.Units
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.Baseline
import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.model.FontFamily
import com.jvmd.notesbridge.xml.Namespaces.W
import com.jvmd.notesbridge.xml.XmlElement

data class RunProperties(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val sizePt: Float? = null,
    val color: Int? = null,
    val baseline: Baseline? = null,
    val hidden: Boolean? = null,
    val caps: Boolean? = null,
    val family: FontFamily? = null,
) {
    fun overriddenBy(other: RunProperties?): RunProperties {
        if (other == null) return this
        return RunProperties(
            bold = other.bold ?: bold,
            italic = other.italic ?: italic,
            underline = other.underline ?: underline,
            strike = other.strike ?: strike,
            sizePt = other.sizePt ?: sizePt,
            color = other.color ?: color,
            baseline = other.baseline ?: baseline,
            hidden = other.hidden ?: hidden,
            caps = other.caps ?: caps,
            family = other.family ?: family,
        )
    }

    companion object {
        fun parse(element: XmlElement?): RunProperties? {
            if (element == null) return null
            return RunProperties(
                bold = toggle(element.child(W, "b")),
                italic = toggle(element.child(W, "i")),
                underline =
                    element.child(W, "u")?.let {
                        val value = it.attr(W, "val")
                        value != "none" && value != "0"
                    },
                strike = toggle(element.child(W, "strike")) ?: toggle(element.child(W, "dstrike")),
                sizePt =
                    element
                        .child(W, "sz")
                        ?.attr(W, "val")
                        ?.toFloatOrNull()
                        ?.let { it / 2f },
                color =
                    element
                        .child(W, "color")
                        ?.attr(W, "val")
                        ?.takeUnless { it.equals("auto", true) }
                        ?.let { Colors.parseHex(it) },
                baseline =
                    when (element.child(W, "vertAlign")?.attr(W, "val")) {
                        "superscript" -> Baseline.SUPERSCRIPT
                        "subscript" -> Baseline.SUBSCRIPT
                        "baseline" -> Baseline.NORMAL
                        else -> null
                    },
                hidden = toggle(element.child(W, "vanish")) ?: toggle(element.child(W, "specVanish")),
                caps = toggle(element.child(W, "caps")),
                family = element.child(W, "rFonts")?.let { fonts -> Units.fontFamilyFor(fonts.attr(W, "ascii") ?: fonts.attr(W, "hAnsi")) },
            )
        }

        private fun toggle(element: XmlElement?): Boolean? {
            if (element == null) return null
            return when (element.attr(W, "val")?.lowercase()) {
                null, "1", "true", "on" -> true
                else -> false
            }
        }
    }
}

data class ParagraphProperties(
    val styleId: String? = null,
    val alignment: Alignment? = null,
    val indentLeftPt: Float? = null,
    val numberingId: String? = null,
    val numberingLevel: Int? = null,
    val outlineLevel: Int? = null,
    val spaceBeforePt: Float? = null,
    val spaceAfterPt: Float? = null,
    val lineSpacing: Float? = null,
    val pageBreakBefore: Boolean? = null,
) {
    fun overriddenBy(other: ParagraphProperties?): ParagraphProperties {
        if (other == null) return this
        return ParagraphProperties(
            styleId = other.styleId ?: styleId,
            alignment = other.alignment ?: alignment,
            indentLeftPt = other.indentLeftPt ?: indentLeftPt,
            numberingId = other.numberingId ?: numberingId,
            numberingLevel = other.numberingLevel ?: numberingLevel,
            outlineLevel = other.outlineLevel ?: outlineLevel,
            spaceBeforePt = other.spaceBeforePt ?: spaceBeforePt,
            spaceAfterPt = other.spaceAfterPt ?: spaceAfterPt,
            lineSpacing = other.lineSpacing ?: lineSpacing,
            pageBreakBefore = other.pageBreakBefore ?: pageBreakBefore,
        )
    }

    companion object {
        fun parse(element: XmlElement?): ParagraphProperties? {
            if (element == null) return null
            val spacing = element.child(W, "spacing")
            val lineRule = spacing?.attr(W, "lineRule")
            val line = spacing?.attr(W, "line")?.toFloatOrNull()
            val indent = element.child(W, "ind")
            return ParagraphProperties(
                styleId = element.child(W, "pStyle")?.attr(W, "val"),
                alignment =
                    when (element.child(W, "jc")?.attr(W, "val")) {
                        "center" -> Alignment.CENTER
                        "right", "end" -> Alignment.END
                        "both", "distribute", "lowKashida", "mediumKashida", "highKashida", "thaiDistribute" -> Alignment.JUSTIFY
                        "left", "start" -> Alignment.START
                        else -> null
                    },
                indentLeftPt = Units.twipsToPt(indent?.attr(W, "left") ?: indent?.attr(W, "start")),
                numberingId = element.child(W, "numPr")?.child(W, "numId")?.attr(W, "val"),
                numberingLevel =
                    element
                        .child(W, "numPr")
                        ?.child(W, "ilvl")
                        ?.attr(W, "val")
                        ?.toIntOrNull(),
                outlineLevel = element.child(W, "outlineLvl")?.attr(W, "val")?.toIntOrNull(),
                spaceBeforePt = Units.twipsToPt(spacing?.attr(W, "before")),
                spaceAfterPt = Units.twipsToPt(spacing?.attr(W, "after")),
                lineSpacing = if (line != null && (lineRule == null || lineRule == "auto")) (line / 240f).coerceIn(0.7f, 3f) else null,
                pageBreakBefore = element.child(W, "pageBreakBefore")?.let { it.attr(W, "val") !in setOf("0", "false", "off") },
            )
        }
    }
}

class DocxStyles(
    root: XmlElement?,
) {
    private class Style(
        val id: String,
        val type: String,
        val name: String,
        val basedOn: String?,
        val run: RunProperties?,
        val paragraph: ParagraphProperties?,
    )

    private val styles: Map<String, Style> =
        root
            ?.children(W, "style")
            ?.mapNotNull { element ->
                val id = element.attr(W, "styleId") ?: return@mapNotNull null
                Style(
                    id = id,
                    type = element.attr(W, "type") ?: "paragraph",
                    name =
                        element
                            .child(W, "name")
                            ?.attr(W, "val")
                            .orEmpty()
                            .lowercase(),
                    basedOn = element.child(W, "basedOn")?.attr(W, "val"),
                    run = RunProperties.parse(element.child(W, "rPr")),
                    paragraph = ParagraphProperties.parse(element.child(W, "pPr")),
                )
            }?.associateBy { it.id }
            .orEmpty()

    private val defaultParagraphStyleId: String? =
        root
            ?.children(W, "style")
            ?.firstOrNull {
                it.attr(W, "type") == "paragraph" && it.attr(W, "default") in setOf("1", "true", "on")
            }?.attr(W, "styleId")

    val defaultRun: RunProperties = RunProperties.parse(root?.path(W, "docDefaults", "rPrDefault", "rPr")) ?: RunProperties()

    val defaultParagraph: ParagraphProperties =
        ParagraphProperties.parse(root?.path(W, "docDefaults", "pPrDefault", "pPr")) ?: ParagraphProperties()

    private fun chain(id: String?): List<Style> {
        val result = ArrayList<Style>()
        var current = id?.let { styles[it] } ?: return result
        val seen = HashSet<String>()
        while (seen.add(current.id)) {
            result.add(0, current)
            current = current.basedOn?.let { styles[it] } ?: break
        }
        return result
    }

    fun paragraphStyleId(id: String?): String? = id ?: defaultParagraphStyleId

    fun paragraphProperties(id: String?): ParagraphProperties =
        chain(paragraphStyleId(id)).fold(defaultParagraph) { acc, style -> acc.overriddenBy(style.paragraph) }

    fun paragraphRun(id: String?): RunProperties =
        chain(paragraphStyleId(id)).fold(defaultRun) { acc, style -> acc.overriddenBy(style.run) }

    fun characterRun(id: String?): RunProperties? {
        val styleChain = chain(id)
        if (styleChain.isEmpty()) return null
        return styleChain.fold(RunProperties()) { acc, style -> acc.overriddenBy(style.run) }
    }

    fun headingLevel(id: String?): Int? {
        for (style in chain(paragraphStyleId(id)).asReversed()) {
            HEADING_NAME.matchEntire(style.name)?.let { return it.groupValues[1].toInt().coerceIn(1, 6) }
            if (style.name == "title") return 1
            if (style.name == "subtitle") return 2
            style.paragraph?.outlineLevel?.let { if (it in 0..5) return it + 1 }
        }
        return null
    }

    private companion object {
        val HEADING_NAME = Regex("heading\\s*(\\d)")
    }
}
