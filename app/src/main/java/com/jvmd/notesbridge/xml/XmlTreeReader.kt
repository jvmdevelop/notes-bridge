package com.jvmd.notesbridge.xml

import com.jvmd.notesbridge.convert.ConversionException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

class XmlTreeReader(
    private val parserFactory: () -> XmlPullParser,
    private val maxElements: Int,
) {
    fun newParser(input: InputStream): XmlPullParser {
        val parser = parserFactory()
        disableDoctypeProcessing(parser)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(input, null)
        return parser
    }

    private fun disableDoctypeProcessing(parser: XmlPullParser) {
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        } catch (_: XmlPullParserException) {
            if (parser.getFeature(
                    XmlPullParser.FEATURE_PROCESS_DOCDECL,
                )
            ) {
                throw ConversionException.TooComplex("XML parser cannot disable DTD processing")
            }
        }
    }

    fun read(input: InputStream): XmlElement =
        try {
            build(newParser(input))
        } catch (error: XmlPullParserException) {
            throw ConversionException.Corrupted("invalid XML: ${error.message}", error)
        } catch (error: IOException) {
            throw ConversionException.Corrupted("cannot read XML: ${error.message}", error)
        }

    private fun build(parser: XmlPullParser): XmlElement {
        val stack = ArrayDeque<PendingElement>()
        var root: XmlElement? = null
        var elementCount = 0
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    elementCount++
                    if (elementCount > maxElements) throw ConversionException.TooComplex("more than $maxElements XML elements")
                    val attributes = HashMap<String, String>(parser.attributeCount * 2)
                    for (index in 0 until parser.attributeCount) {
                        val namespace = Namespaces.normalize(parser.getAttributeNamespace(index).orEmpty())
                        val key =
                            if (namespace.isEmpty()) {
                                parser.getAttributeName(
                                    index,
                                )
                            } else {
                                "{$namespace}${parser.getAttributeName(index)}"
                            }
                        attributes[key] = parser.getAttributeValue(index)
                    }
                    stack.addLast(PendingElement(Namespaces.normalize(parser.namespace.orEmpty()), parser.name, attributes))
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    stack.lastOrNull()?.children?.add(XmlText(parser.text.orEmpty()))
                }

                XmlPullParser.END_TAG -> {
                    val pending = stack.removeLast()
                    val element = XmlElement(pending.namespace, pending.name, pending.attributes, pending.children)
                    val parent = stack.lastOrNull()
                    if (parent == null) root = element else parent.children.add(element)
                }

                XmlPullParser.END_DOCUMENT -> {
                    return root ?: throw ConversionException.Corrupted("empty XML document")
                }
            }
        }
    }

    private class PendingElement(
        val namespace: String,
        val name: String,
        val attributes: Map<String, String>,
    ) {
        val children = ArrayList<XmlNode>()
    }
}
