package com.jvmd.notesbridge.xml

sealed interface XmlNode

class XmlText(
    val text: String,
) : XmlNode

class XmlElement(
    val namespace: String,
    val name: String,
    private val attributes: Map<String, String>,
    val children: List<XmlNode>,
) : XmlNode {
    val elements: Sequence<XmlElement> get() = children.asSequence().filterIsInstance<XmlElement>()

    fun attr(name: String): String? = attributes[name]

    fun attr(
        namespace: String,
        name: String,
    ): String? = attributes["{$namespace}$name"]

    fun child(
        namespace: String,
        name: String,
    ): XmlElement? = elements.firstOrNull { it.namespace == namespace && it.name == name }

    fun children(
        namespace: String,
        name: String,
    ): Sequence<XmlElement> = elements.filter { it.namespace == namespace && it.name == name }

    fun path(
        namespace: String,
        vararg names: String,
    ): XmlElement? {
        var current: XmlElement? = this
        for (name in names) {
            current = current?.child(namespace, name) ?: return null
        }
        return current
    }

    fun descendants(): Sequence<XmlElement> =
        sequence {
            for (element in elements) {
                yield(element)
                yieldAll(element.descendants())
            }
        }

    fun descendants(
        namespace: String,
        name: String,
    ): Sequence<XmlElement> = descendants().filter { it.namespace == namespace && it.name == name }

    fun matches(
        namespace: String,
        name: String,
    ): Boolean = this.namespace == namespace && this.name == name

    fun textContent(): String {
        val builder = StringBuilder()
        appendText(builder)
        return builder.toString()
    }

    private fun appendText(builder: StringBuilder) {
        for (child in children) {
            when (child) {
                is XmlText -> builder.append(child.text)
                is XmlElement -> child.appendText(builder)
            }
        }
    }
}
