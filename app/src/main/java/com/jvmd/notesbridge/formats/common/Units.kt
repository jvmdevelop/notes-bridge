package com.jvmd.notesbridge.formats.common

object Units {
    const val EMU_PER_POINT = 12700f

    fun emuToPt(value: String?): Float? = value?.toLongOrNull()?.let { it / EMU_PER_POINT }

    fun twipsToPt(value: String?): Float? = value?.toFloatOrNull()?.let { it / 20f }

    fun lengthToPt(value: String?): Float? {
        val text = value?.trim()?.lowercase() ?: return null
        val number = text.takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' }.toFloatOrNull() ?: return null
        return when (text.substring(text.takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' }.length).trim()) {
            "pt", "" -> number
            "cm" -> number * 72f / 2.54f
            "mm" -> number * 72f / 25.4f
            "in", "inch" -> number * 72f
            "pc" -> number * 12f
            "px" -> number * 0.75f
            "em" -> number * 12f
            else -> null
        }
    }

    fun fontFamilyFor(name: String?): com.jvmd.notesbridge.model.FontFamily? {
        val lower = name?.lowercase() ?: return null
        return when {
            MONO_HINTS.any { lower.contains(it) } -> com.jvmd.notesbridge.model.FontFamily.MONO
            lower.contains("sans") -> com.jvmd.notesbridge.model.FontFamily.SANS
            SERIF_HINTS.any { lower.contains(it) } -> com.jvmd.notesbridge.model.FontFamily.SERIF
            else -> com.jvmd.notesbridge.model.FontFamily.SANS
        }
    }

    private val MONO_HINTS = listOf("courier", "consolas", "mono", "menlo", "inconsolata", "lucida console", "source code")
    private val SERIF_HINTS =
        listOf(
            "times",
            "georgia",
            "cambria",
            "garamond",
            "serif",
            "palatino",
            "book antiqua",
            "baskerville",
            "liberation serif",
            "pt serif",
            "noto serif",
            "constantia",
            "minion",
        )
}
