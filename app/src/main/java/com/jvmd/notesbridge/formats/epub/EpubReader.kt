package com.jvmd.notesbridge.formats.epub

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.html.HtmlFlowBuilder
import com.jvmd.notesbridge.io.DocumentArchive
import com.jvmd.notesbridge.io.PartPaths
import com.jvmd.notesbridge.model.FlowBlock
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.PageBreak
import com.jvmd.notesbridge.xml.Namespaces.OCF_CONTAINER
import com.jvmd.notesbridge.xml.Namespaces.OPF
import com.jvmd.notesbridge.xml.Namespaces.XML_ENCRYPTION
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

class EpubReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument {
        val archive = input.archive
        checkEncryption(archive, context)
        val container =
            archive.open("META-INF/container.xml")?.use { context.xml.read(it) }
                ?: throw ConversionException.Corrupted("missing META-INF/container.xml")
        val packagePath =
            container
                .child(OCF_CONTAINER, "rootfiles")
                ?.children(OCF_CONTAINER, "rootfile")
                ?.firstOrNull { it.attr("media-type") == null || it.attr("media-type") == "application/oebps-package+xml" }
                ?.attr("full-path")
                ?: throw ConversionException.Corrupted("EPUB has no package document")
        val packageDocument =
            archive.open(packagePath)?.use { context.xml.read(it) } ?: throw ConversionException.Corrupted("missing $packagePath")
        val baseDirectory = PartPaths.directoryOf(packagePath)
        val manifest =
            packageDocument
                .child(OPF, "manifest")
                ?.children(OPF, "item")
                ?.mapNotNull { item ->
                    val id = item.attr("id") ?: return@mapNotNull null
                    val href = item.attr("href") ?: return@mapNotNull null
                    id to (PartPaths.resolve(baseDirectory, href) to item.attr("media-type").orEmpty())
                }?.toMap()
                .orEmpty()
        val spine =
            packageDocument
                .child(OPF, "spine")
                ?.children(OPF, "itemref")
                ?.filter { it.attr("linear") != "no" }
                ?.mapNotNull { manifest[it.attr("idref")] }
                ?.toList()
                .orEmpty()
        if (spine.isEmpty()) throw ConversionException.Empty()
        val blocks = ArrayList<FlowBlock>()
        for ((path, mediaType) in spine) {
            context.checkCancelled()
            if (mediaType.isNotEmpty() && mediaType != "application/xhtml+xml" && mediaType != "text/html") continue
            val bytes = archive.readBytes(path) ?: continue
            if (bytes.size > context.limits.maxTextBytes) throw ConversionException.TooLarge(context.limits.maxTextBytes)
            val document = Jsoup.parse(bytes.inputStream(), null, "", Parser.htmlParser())
            val chapterDirectory = PartPaths.directoryOf(path)
            val chapter =
                HtmlFlowBuilder(context) { reference ->
                    archive.imageSource(PartPaths.resolve(chapterDirectory, reference))
                }.build(document.body())
            if (chapter.isEmpty()) continue
            if (blocks.isNotEmpty()) blocks.add(PageBreak)
            blocks.addAll(chapter)
        }
        return FlowDocument(blocks)
    }

    private fun checkEncryption(
        archive: DocumentArchive,
        context: ConversionContext,
    ) {
        val encryption = archive.open("META-INF/encryption.xml")?.use { context.xml.read(it) } ?: return
        val protectedResources =
            encryption.descendants(XML_ENCRYPTION, "EncryptedData").filter { data ->
                val algorithm = data.child(XML_ENCRYPTION, "EncryptionMethod")?.attr("Algorithm").orEmpty()
                algorithm !in FONT_OBFUSCATION
            }
        if (protectedResources.any()) throw ConversionException.DrmProtected()
    }

    private companion object {
        val FONT_OBFUSCATION = setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")
    }
}
