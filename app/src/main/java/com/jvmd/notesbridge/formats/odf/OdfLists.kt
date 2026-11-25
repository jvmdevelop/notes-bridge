package com.jvmd.notesbridge.formats.odf

import com.jvmd.notesbridge.formats.common.ListNumbering
import com.jvmd.notesbridge.formats.common.NumberStyle
import com.jvmd.notesbridge.xml.Namespaces.STYLE
import com.jvmd.notesbridge.xml.Namespaces.TEXT
import com.jvmd.notesbridge.xml.XmlElement

object OdfLists {
    fun levelStyle(
        listStyle: XmlElement?,
        level: Int,
    ): XmlElement? = listStyle?.elements?.firstOrNull { (it.attr(TEXT, "level")?.toIntOrNull() ?: 1) == level + 1 }

    fun isNumbered(levelStyle: XmlElement?): Boolean =
        levelStyle?.matches(TEXT, "list-level-style-number") == true && levelStyle.attr(STYLE, "num-format").orEmpty().isNotEmpty()

    fun marker(
        levelStyle: XmlElement?,
        level: Int,
        counter: Int,
    ): String? {
        if (levelStyle == null) return ListNumbering.bullet(level)
        return when {
            levelStyle.matches(TEXT, "list-level-style-bullet") -> {
                ListNumbering.normalizeBulletGlyph(levelStyle.attr(TEXT, "bullet-char") ?: "•")
            }

            levelStyle.matches(TEXT, "list-level-style-number") -> {
                val format = levelStyle.attr(STYLE, "num-format").orEmpty()
                if (format.isEmpty()) return null
                val style =
                    when (format) {
                        "a" -> NumberStyle.LOWER_ALPHA
                        "A" -> NumberStyle.UPPER_ALPHA
                        "i" -> NumberStyle.LOWER_ROMAN
                        "I" -> NumberStyle.UPPER_ROMAN
                        else -> NumberStyle.DECIMAL
                    }
                levelStyle.attr(STYLE, "num-prefix").orEmpty() + ListNumbering.format(counter, style) +
                    levelStyle.attr(STYLE, "num-suffix").orEmpty()
            }

            levelStyle.matches(TEXT, "list-level-style-image") -> {
                "•"
            }

            else -> {
                ListNumbering.bullet(level)
            }
        }
    }

    fun startValue(levelStyle: XmlElement?): Int = levelStyle?.attr(TEXT, "start-value")?.toIntOrNull() ?: 1
}
