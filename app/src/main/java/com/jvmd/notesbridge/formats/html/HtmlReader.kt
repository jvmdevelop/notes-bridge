package com.jvmd.notesbridge.formats.html

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.model.FlowDocument
import org.jsoup.Jsoup

class HtmlReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument {
        val bytes = input.readTextBytes()
        val document = Jsoup.parse(bytes.inputStream(), null, "")
        return FlowDocument(HtmlFlowBuilder(context).build(document.body()))
    }
}
