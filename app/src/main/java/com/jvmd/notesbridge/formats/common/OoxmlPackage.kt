package com.jvmd.notesbridge.formats.common

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.io.DocumentArchive
import com.jvmd.notesbridge.io.PartPaths
import com.jvmd.notesbridge.xml.Namespaces
import com.jvmd.notesbridge.xml.XmlElement

data class Relationship(
    val id: String,
    val type: String,
    val target: String,
    val external: Boolean,
) {
    fun hasType(suffix: String): Boolean = type.endsWith("/$suffix")
}

class OoxmlPackage(
    val archive: DocumentArchive,
    private val context: ConversionContext,
) {
    private val xmlCache = HashMap<String, XmlElement?>()
    private val relationshipCache = HashMap<String, Map<String, Relationship>>()

    fun xml(path: String): XmlElement? = xmlCache.getOrPut(path) { archive.open(path)?.use { context.xml.read(it) } }

    fun relationships(partPath: String): Map<String, Relationship> =
        relationshipCache.getOrPut(partPath) {
            val directory = PartPaths.directoryOf(partPath)
            val fileName = partPath.substringAfterLast('/')
            val relsPath = if (directory.isEmpty()) "_rels/$fileName.rels" else "$directory/_rels/$fileName.rels"
            val root = xml(relsPath) ?: return@getOrPut emptyMap()
            root
                .children(Namespaces.PACKAGE_RELATIONSHIPS, "Relationship")
                .mapNotNull { element ->
                    val id = element.attr("Id") ?: return@mapNotNull null
                    val target = element.attr("Target") ?: return@mapNotNull null
                    val external = element.attr("TargetMode").equals("External", ignoreCase = true)
                    Relationship(
                        id,
                        element.attr("Type").orEmpty(),
                        if (external) target else PartPaths.resolve(directory, target),
                        external,
                    )
                }.associateBy { it.id }
        }

    fun mainPart(
        relationshipSuffix: String,
        fallback: String,
    ): String =
        relationships("").values.firstOrNull { it.hasType(relationshipSuffix) && !it.external }?.target
            ?: relationships("").values.firstOrNull { it.type.endsWith("officeDocument") && !it.external }?.target
            ?: fallback
}
