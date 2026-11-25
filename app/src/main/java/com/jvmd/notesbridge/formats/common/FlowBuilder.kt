package com.jvmd.notesbridge.formats.common

import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.FlowBlock
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.ParagraphRole
import com.jvmd.notesbridge.model.TextRun
import com.jvmd.notesbridge.model.TextStyle

class RunCollector(
    private val collapseWhitespace: Boolean,
) {
    private val runs = ArrayList<TextRun>()
    private val current = StringBuilder()
    private var currentStyle: TextStyle? = null
    private var lastWasSpace = true

    val isEmpty: Boolean get() = runs.isEmpty() && current.isEmpty()

    val hasVisibleText: Boolean get() = runs.any { it.text.isNotBlank() } || current.isNotBlank()

    fun append(
        text: String,
        style: TextStyle,
    ) {
        if (text.isEmpty()) return
        if (currentStyle != style) {
            flushRun()
            currentStyle = style
        }
        if (!collapseWhitespace) {
            current.append(text)
            return
        }
        for (char in text) {
            if (char.isWhitespace() && char != '\u00A0') {
                if (!lastWasSpace) {
                    current.append(' ')
                    lastWasSpace = true
                }
            } else {
                current.append(char)
                lastWasSpace = false
            }
        }
    }

    fun appendPreserved(
        text: String,
        style: TextStyle,
    ) {
        if (text.isEmpty()) return
        if (currentStyle != style) {
            flushRun()
            currentStyle = style
        }
        current.append(text)
        lastWasSpace = text.last().isWhitespace()
    }

    fun appendLineBreak(style: TextStyle) {
        if (currentStyle != style) {
            flushRun()
            currentStyle = style
        }
        val trailingSpace = current.isNotEmpty() && current.last() == ' '
        if (trailingSpace) current.setLength(current.length - 1)
        current.append('\n')
        lastWasSpace = true
    }

    private fun flushRun() {
        val style = currentStyle
        if (current.isNotEmpty() && style != null) runs.add(TextRun(current.toString(), style))
        current.setLength(0)
    }

    fun take(): List<TextRun> {
        flushRun()
        val result = ArrayList(runs)
        runs.clear()
        currentStyle = null
        lastWasSpace = true
        if (collapseWhitespace && result.isNotEmpty()) {
            val last = result.last()
            val trimmed = last.text.trimEnd(' ')
            if (trimmed.isEmpty()) result.removeAt(result.size - 1) else result[result.size - 1] = last.copy(text = trimmed)
        }
        return result
    }
}

class ParagraphSpec(
    val role: ParagraphRole = ParagraphRole.BODY,
    val headingLevel: Int = 0,
    val alignment: Alignment = Alignment.START,
    val indentPt: Float = 0f,
    val listMarker: String? = null,
)

class FlowCollector {
    val blocks = ArrayList<FlowBlock>()

    fun add(block: FlowBlock) {
        blocks.add(block)
    }

    fun addParagraph(
        runs: List<TextRun>,
        spec: ParagraphSpec,
        keepEmpty: Boolean = false,
    ) {
        if (runs.isEmpty() && spec.listMarker == null && !keepEmpty) return
        blocks.add(
            Paragraph(
                runs = runs,
                role = spec.role,
                headingLevel = spec.headingLevel,
                alignment = spec.alignment,
                indentPt = spec.indentPt,
                listMarker = spec.listMarker,
            ),
        )
    }
}
