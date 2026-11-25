package com.jvmd.notesbridge

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionLimits
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.DocumentSource
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.model.FlowBlock
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.Table
import com.jvmd.notesbridge.xml.XmlTreeReader
import org.kxml2.io.KXmlParser
import java.io.File

object TestSupport {
    fun context(limits: ConversionLimits = ConversionLimits()): ConversionContext =
        ConversionContext(limits, XmlTreeReader({ KXmlParser() }, limits.maxXmlElements))

    fun fixture(name: String): File {
        val url = requireNotNull(TestSupport::class.java.classLoader?.getResource(name)) { "Missing fixture $name" }
        return File(url.toURI())
    }

    fun <T> read(
        reader: DocumentReader<T>,
        name: String,
        format: DocumentFormat,
        context: ConversionContext = context(),
    ): T = ReaderInput(DocumentSource(fixture(name), name, format), context.limits).use { reader.read(it, context) }

    fun <T> inspect(
        reader: DocumentReader<T>,
        name: String,
        format: DocumentFormat,
        block: (T) -> Unit,
    ) {
        val context = context()
        ReaderInput(DocumentSource(fixture(name), name, format), context.limits).use { block(reader.read(it, context)) }
    }

    fun <T> readFile(
        reader: DocumentReader<T>,
        file: File,
        format: DocumentFormat,
        context: ConversionContext = context(),
    ): T = ReaderInput(DocumentSource(file, file.name, format), context.limits).use { reader.read(it, context) }

    fun allText(blocks: List<FlowBlock>): String =
        blocks.joinToString("\n") { block ->
            when (block) {
                is Paragraph -> {
                    block.plainText
                }

                is Table -> {
                    block.rows.joinToString(
                        "\n",
                    ) { row ->
                        row.cells.joinToString(" | ") { cell ->
                            cell.paragraphs.joinToString(" ") {
                                it.plainText
                            }
                        }
                    }
                }

                else -> {
                    ""
                }
            }
        }
}
