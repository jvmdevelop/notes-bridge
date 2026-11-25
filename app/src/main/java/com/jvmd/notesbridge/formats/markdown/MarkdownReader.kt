package com.jvmd.notesbridge.formats.markdown

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.html.HtmlFlowBuilder
import com.jvmd.notesbridge.io.TextDecoding
import com.jvmd.notesbridge.model.FlowDocument
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup

class MarkdownReader : DocumentReader<FlowDocument> {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).build()

    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument = parse(TextDecoding.decode(input.readTextBytes()), context)

    fun parse(
        markdown: String,
        context: ConversionContext?,
    ): FlowDocument {
        val html = renderer.render(parser.parse(markdown))
        val body = Jsoup.parseBodyFragment(html).body()
        return FlowDocument(HtmlFlowBuilder(context).build(body))
    }
}
