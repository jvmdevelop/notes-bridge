package com.jvmd.notesbridge.convert

import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.formats.csv.CsvReader
import com.jvmd.notesbridge.formats.docx.DocxReader
import com.jvmd.notesbridge.formats.epub.EpubReader
import com.jvmd.notesbridge.formats.html.HtmlReader
import com.jvmd.notesbridge.formats.image.ImagePdfExporter
import com.jvmd.notesbridge.formats.markdown.MarkdownReader
import com.jvmd.notesbridge.formats.odf.OdpReader
import com.jvmd.notesbridge.formats.odf.OdsReader
import com.jvmd.notesbridge.formats.odf.OdtReader
import com.jvmd.notesbridge.formats.pptx.PptxReader
import com.jvmd.notesbridge.formats.rtf.RtfReader
import com.jvmd.notesbridge.formats.svg.SvgPdfExporter
import com.jvmd.notesbridge.formats.text.PlainTextReader
import com.jvmd.notesbridge.formats.xlsx.XlsxReader

object DefaultConverters {
    fun registry(): ConverterRegistry =
        ConverterRegistry(
            listOf(
                ImagePdfExporter(),
                SvgPdfExporter(),
                FlowPdfExporter(setOf(DocumentFormat.PLAIN_TEXT), PlainTextReader()),
                FlowPdfExporter(setOf(DocumentFormat.CSV), CsvReader()),
                FlowPdfExporter(setOf(DocumentFormat.MARKDOWN), MarkdownReader()),
                FlowPdfExporter(setOf(DocumentFormat.HTML), HtmlReader()),
                FlowPdfExporter(setOf(DocumentFormat.RTF), RtfReader()),
                FlowPdfExporter(setOf(DocumentFormat.DOCX), DocxReader()),
                FlowPdfExporter(setOf(DocumentFormat.ODT), OdtReader()),
                FlowPdfExporter(setOf(DocumentFormat.XLSX), XlsxReader()),
                FlowPdfExporter(setOf(DocumentFormat.ODS), OdsReader()),
                FlowPdfExporter(setOf(DocumentFormat.EPUB), EpubReader()),
                SlidePdfExporter(setOf(DocumentFormat.PPTX), PptxReader()),
                SlidePdfExporter(setOf(DocumentFormat.ODP), OdpReader()),
            ),
        )
}
