package com.jvmd.notesbridge.formats.text

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.io.TextDecoding
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.TextRun

class PlainTextReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument = parse(TextDecoding.decode(input.readTextBytes()), context)

    fun parse(
        text: String,
        context: ConversionContext? = null,
    ): FlowDocument {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("\u000C", "\n")
        val lines = normalized.trimEnd('\n').split('\n')
        val blocks =
            lines.mapIndexed { index, line ->
                if (index % 500 == 0) context?.checkCancelled()
                Paragraph(
                    runs = if (line.isEmpty()) emptyList() else listOf(TextRun(line.trimEnd())),
                    spaceAfterPt = 2f,
                )
            }
        return FlowDocument(if (blocks.all { it.runs.isEmpty() }) emptyList() else blocks)
    }
}
