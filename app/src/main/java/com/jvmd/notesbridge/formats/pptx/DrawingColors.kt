package com.jvmd.notesbridge.formats.pptx

import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.xml.Namespaces.A
import com.jvmd.notesbridge.xml.XmlElement

class DrawingColors(
    private val scheme: Map<String, Int>,
    private val colorMap: Map<String, String>,
) {
    fun accent(index: Int): Int = scheme["accent$index"] ?: DEFAULT_ACCENT

    fun fromContainer(container: XmlElement?): Int? = container?.elements?.firstNotNullOfOrNull { fromColorElement(it) }

    fun fromColorElement(element: XmlElement): Int? {
        if (element.namespace != A) return null
        val base =
            when (element.name) {
                "srgbClr" -> {
                    Colors.parseHex(element.attr("val"))
                }

                "schemeClr" -> {
                    scheme(element.attr("val"))
                }

                "sysClr" -> {
                    Colors.parseHex(element.attr("lastClr")) ?: systemColor(element.attr("val"))
                }

                "prstClr" -> {
                    element.attr("val")?.let {
                        Colors.named(
                            it.removePrefix("dk").let { name ->
                                if (name !=
                                    it
                                ) {
                                    "dark$name"
                                } else {
                                    it
                                }
                            },
                        )
                            ?: Colors.named(it)
                    }
                }

                "scrgbClr" -> {
                    val red = percent(element.attr("r"))
                    val green = percent(element.attr("g"))
                    val blue = percent(element.attr("b"))
                    if (red != null && green != null &&
                        blue != null
                    ) {
                        Colors.rgb((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
                    } else {
                        null
                    }
                }

                else -> {
                    return null
                }
            } ?: return null
        return applyModifiers(base, element)
    }

    private fun scheme(name: String?): Int? {
        val key = name ?: return null
        val mapped = colorMap[key] ?: DEFAULT_MAP[key] ?: key
        return scheme[mapped]
    }

    private fun systemColor(name: String?): Int? =
        when (name) {
            "windowText", "captionText", "menuText", "btnText" -> Colors.BLACK
            "window", "menu", "btnFace", "btnHighlight" -> Colors.WHITE
            else -> null
        }

    private fun applyModifiers(
        base: Int,
        element: XmlElement,
    ): Int {
        var color = base
        var modulation = 1f
        var offset = 0f
        var luminanceChanged = false
        for (modifier in element.elements) {
            val value = percent(modifier.attr("val")) ?: continue
            when (modifier.name) {
                "lumMod" -> {
                    modulation *= value
                    luminanceChanged = true
                }

                "lumOff" -> {
                    offset += value
                    luminanceChanged = true
                }

                "tint" -> {
                    color = Colors.tint(color, value)
                }

                "shade" -> {
                    color = Colors.shade(color, value)
                }
            }
        }
        return if (luminanceChanged) Colors.applyLuminance(color, modulation, offset) else color
    }

    private fun percent(value: String?): Float? = value?.toFloatOrNull()?.let { it / 100_000f }

    companion object {
        private const val DEFAULT_ACCENT = 0xFF4472C4.toInt()
        private val DEFAULT_MAP = mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2")
        private val FALLBACK_SCHEME =
            mapOf(
                "dk1" to Colors.BLACK,
                "lt1" to Colors.WHITE,
                "dk2" to 0xFF44546A.toInt(),
                "lt2" to 0xFFE7E6E6.toInt(),
                "accent1" to 0xFF4472C4.toInt(),
                "accent2" to 0xFFED7D31.toInt(),
                "accent3" to 0xFFA5A5A5.toInt(),
                "accent4" to 0xFFFFC000.toInt(),
                "accent5" to 0xFF5B9BD5.toInt(),
                "accent6" to 0xFF70AD47.toInt(),
                "hlink" to 0xFF0563C1.toInt(),
                "folHlink" to 0xFF954F72.toInt(),
            )

        fun schemeFrom(theme: XmlElement?): Map<String, Int> {
            val scheme = theme?.path(A, "themeElements", "clrScheme") ?: return FALLBACK_SCHEME
            val parsed = HashMap(FALLBACK_SCHEME)
            for (entry in scheme.elements) {
                val color = entry.elements.firstOrNull() ?: continue
                val value =
                    when (color.name) {
                        "srgbClr" -> Colors.parseHex(color.attr("val"))
                        "sysClr" -> Colors.parseHex(color.attr("lastClr"))
                        else -> null
                    } ?: continue
                parsed[entry.name] = value
            }
            return parsed
        }

        fun mapFrom(element: XmlElement?): Map<String, String> {
            if (element == null) return DEFAULT_MAP
            return listOf("bg1", "tx1", "bg2", "tx2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
                .mapNotNull { key -> element.attr(key)?.let { key to it } }
                .toMap()
                .ifEmpty { DEFAULT_MAP }
        }
    }
}
