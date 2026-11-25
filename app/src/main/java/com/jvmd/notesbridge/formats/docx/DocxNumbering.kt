package com.jvmd.notesbridge.formats.docx

import com.jvmd.notesbridge.formats.common.ListNumbering
import com.jvmd.notesbridge.formats.common.NumberStyle
import com.jvmd.notesbridge.formats.common.Units
import com.jvmd.notesbridge.xml.Namespaces.W
import com.jvmd.notesbridge.xml.XmlElement

class DocxNumbering(
    root: XmlElement?,
) {
    private class Level(
        val format: String,
        val text: String,
        val start: Int,
        val indentPt: Float?,
    )

    private val abstractLevels: Map<String, Map<Int, Level>> =
        root
            ?.children(W, "abstractNum")
            ?.associate { abstract ->
                abstract.attr(W, "abstractNumId").orEmpty() to parseLevels(abstract)
            }.orEmpty()

    private val numToAbstract: Map<String, String> =
        root
            ?.children(W, "num")
            ?.associate { num -> num.attr(W, "numId").orEmpty() to num.child(W, "abstractNumId")?.attr(W, "val").orEmpty() }
            .orEmpty()

    private val overrides: Map<String, Map<Int, Level>> =
        root
            ?.children(W, "num")
            ?.associate { num ->
                num.attr(W, "numId").orEmpty() to
                    num
                        .children(W, "lvlOverride")
                        .mapNotNull { override ->
                            val levelIndex = override.attr(W, "ilvl")?.toIntOrNull() ?: return@mapNotNull null
                            override.child(W, "lvl")?.let { levelIndex to parseLevel(it) }
                        }.toMap()
            }.orEmpty()

    private val startOverrides: Map<String, Map<Int, Int>> =
        root
            ?.children(W, "num")
            ?.associate { num ->
                num.attr(W, "numId").orEmpty() to
                    num
                        .children(W, "lvlOverride")
                        .mapNotNull { override ->
                            val levelIndex = override.attr(W, "ilvl")?.toIntOrNull() ?: return@mapNotNull null
                            override
                                .child(W, "startOverride")
                                ?.attr(W, "val")
                                ?.toIntOrNull()
                                ?.let { levelIndex to it }
                        }.toMap()
            }.orEmpty()

    private val counters = HashMap<String, IntArray>()

    private fun parseLevels(abstract: XmlElement): Map<Int, Level> =
        abstract
            .children(W, "lvl")
            .mapNotNull { level -> level.attr(W, "ilvl")?.toIntOrNull()?.let { it to parseLevel(level) } }
            .toMap()

    private fun parseLevel(level: XmlElement): Level =
        Level(
            format = level.child(W, "numFmt")?.attr(W, "val") ?: "decimal",
            text = level.child(W, "lvlText")?.attr(W, "val") ?: "",
            start = level.child(W, "start")?.attr(W, "val")?.toIntOrNull() ?: 1,
            indentPt = level.path(W, "pPr", "ind")?.let { Units.twipsToPt(it.attr(W, "left") ?: it.attr(W, "start")) },
        )

    private fun level(
        numId: String,
        index: Int,
    ): Level? = overrides[numId]?.get(index) ?: abstractLevels[numToAbstract[numId]]?.get(index)

    data class Marker(
        val text: String,
        val indentPt: Float?,
    )

    fun next(
        numId: String,
        levelIndex: Int,
    ): Marker? {
        if (numId == "0") return null
        val index = levelIndex.coerceIn(0, 8)
        val definition = level(numId, index) ?: return null
        val key = numToAbstract[numId] ?: numId
        val values =
            counters.getOrPut(key) {
                IntArray(9) { levelNumber -> (startOverrides[numId]?.get(levelNumber) ?: level(numId, levelNumber)?.start ?: 1) - 1 }
            }
        values[index]++
        for (deeper in index + 1 until 9) values[deeper] = (startOverrides[numId]?.get(deeper) ?: level(numId, deeper)?.start ?: 1) - 1
        val text =
            if (definition.format == "bullet") {
                ListNumbering.normalizeBulletGlyph(definition.text)
            } else if (definition.format == "none") {
                ""
            } else {
                PLACEHOLDER.replace(definition.text) { match ->
                    val referenced = match.groupValues[1].toInt() - 1
                    val referencedLevel = level(numId, referenced)
                    val value = values.getOrElse(referenced) { 1 }.coerceAtLeast(1)
                    ListNumbering.format(value, styleOf(referencedLevel?.format ?: "decimal"))
                }
            }
        return Marker(text, definition.indentPt)
    }

    private fun styleOf(format: String): NumberStyle =
        when (format) {
            "lowerLetter" -> NumberStyle.LOWER_ALPHA
            "upperLetter" -> NumberStyle.UPPER_ALPHA
            "lowerRoman" -> NumberStyle.LOWER_ROMAN
            "upperRoman" -> NumberStyle.UPPER_ROMAN
            "none" -> NumberStyle.NONE
            else -> NumberStyle.DECIMAL
        }

    private companion object {
        val PLACEHOLDER = Regex("%(\\d)")
    }
}
