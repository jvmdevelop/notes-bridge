package com.jvmd.notesbridge.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Colors {
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()

    private val NAMED: Map<String, Int> =
        mapOf(
            "black" to 0x000000,
            "white" to 0xFFFFFF,
            "red" to 0xFF0000,
            "green" to 0x008000,
            "lime" to 0x00FF00,
            "blue" to 0x0000FF,
            "yellow" to 0xFFFF00,
            "cyan" to 0x00FFFF,
            "aqua" to 0x00FFFF,
            "magenta" to 0xFF00FF,
            "fuchsia" to 0xFF00FF,
            "gray" to 0x808080,
            "grey" to 0x808080,
            "darkgray" to 0xA9A9A9,
            "darkgrey" to 0xA9A9A9,
            "lightgray" to 0xD3D3D3,
            "lightgrey" to 0xD3D3D3,
            "silver" to 0xC0C0C0,
            "maroon" to 0x800000,
            "olive" to 0x808000,
            "navy" to 0x000080,
            "purple" to 0x800080,
            "teal" to 0x008080,
            "orange" to 0xFFA500,
            "darkblue" to 0x00008B,
            "darkred" to 0x8B0000,
            "darkgreen" to 0x006400,
            "darkcyan" to 0x008B8B,
            "darkmagenta" to 0x8B008B,
            "darkyellow" to 0x808000,
            "brown" to 0xA52A2A,
            "pink" to 0xFFC0CB,
        )

    fun rgb(
        red: Int,
        green: Int,
        blue: Int,
    ): Int = (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

    fun red(color: Int): Int = (color shr 16) and 0xFF

    fun green(color: Int): Int = (color shr 8) and 0xFF

    fun blue(color: Int): Int = color and 0xFF

    fun parseHex(value: String?): Int? {
        val hex = value?.trim()?.removePrefix("#") ?: return null
        return when (hex.length) {
            6 -> {
                hex.toIntOrNull(16)?.let { (0xFF shl 24) or it }
            }

            3 -> {
                val expanded = hex.map { "$it$it" }.joinToString("")
                expanded.toIntOrNull(16)?.let { (0xFF shl 24) or it }
            }

            8 -> {
                hex.substring(2).toIntOrNull(16)?.let { (0xFF shl 24) or it }
            }

            else -> {
                null
            }
        }
    }

    fun parseCss(value: String?): Int? {
        val text = value?.trim()?.lowercase() ?: return null
        if (text.startsWith("#")) return parseHex(text)
        if (text.startsWith("rgb")) {
            val parts =
                text
                    .substringAfter('(')
                    .substringBefore(')')
                    .split(',')
                    .map { it.trim() }
            if (parts.size < 3) return null
            val channels =
                parts.take(3).map { part ->
                    if (part.endsWith("%")) {
                        part.removeSuffix("%").toFloatOrNull()?.let { (it * 2.55f).roundToInt() }
                    } else {
                        part.toFloatOrNull()?.roundToInt()
                    }
                }
            if (channels.any { it == null }) return null
            return rgb(channels[0]!!, channels[1]!!, channels[2]!!)
        }
        return named(text)
    }

    fun named(name: String): Int? = NAMED[name.lowercase()]?.let { (0xFF shl 24) or it }

    fun luminance(color: Int): Float = (0.2126f * red(color) + 0.7152f * green(color) + 0.0722f * blue(color)) / 255f

    fun applyLuminance(
        color: Int,
        modulation: Float,
        offset: Float,
    ): Int {
        val hsl = toHsl(color)
        val lightness = (hsl[2] * modulation + offset).coerceIn(0f, 1f)
        return fromHsl(hsl[0], hsl[1], lightness)
    }

    fun tint(
        color: Int,
        amount: Float,
    ): Int {
        val a = amount.coerceIn(0f, 1f)
        return rgb(
            (red(color) * a + 255 * (1 - a)).roundToInt(),
            (green(color) * a + 255 * (1 - a)).roundToInt(),
            (blue(color) * a + 255 * (1 - a)).roundToInt(),
        )
    }

    fun shade(
        color: Int,
        amount: Float,
    ): Int {
        val a = amount.coerceIn(0f, 1f)
        return rgb((red(color) * a).roundToInt(), (green(color) * a).roundToInt(), (blue(color) * a).roundToInt())
    }

    private fun toHsl(color: Int): FloatArray {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        val maxValue = max(r, max(g, b))
        val minValue = min(r, min(g, b))
        val lightness = (maxValue + minValue) / 2f
        if (maxValue == minValue) return floatArrayOf(0f, 0f, lightness)
        val delta = maxValue - minValue
        val saturation = if (lightness > 0.5f) delta / (2f - maxValue - minValue) else delta / (maxValue + minValue)
        val hue =
            when (maxValue) {
                r -> ((g - b) / delta + (if (g < b) 6f else 0f))
                g -> ((b - r) / delta + 2f)
                else -> ((r - g) / delta + 4f)
            } / 6f
        return floatArrayOf(hue, saturation, lightness)
    }

    private fun fromHsl(
        hue: Float,
        saturation: Float,
        lightness: Float,
    ): Int {
        if (saturation == 0f) {
            val v = (lightness * 255).roundToInt()
            return rgb(v, v, v)
        }
        val q = if (lightness < 0.5f) lightness * (1 + saturation) else lightness + saturation - lightness * saturation
        val p = 2 * lightness - q
        return rgb(
            (hueToChannel(p, q, hue + 1f / 3f) * 255).roundToInt(),
            (hueToChannel(p, q, hue) * 255).roundToInt(),
            (hueToChannel(p, q, hue - 1f / 3f) * 255).roundToInt(),
        )
    }

    private fun hueToChannel(
        p: Float,
        q: Float,
        hueInput: Float,
    ): Float {
        var t = hueInput
        if (t < 0) t += 1f
        if (t > 1) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
}
