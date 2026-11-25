package com.jvmd.notesbridge.formats.common

object ListNumbering {
    private val BULLETS = listOf("•", "◦", "▪")

    fun bullet(level: Int): String = BULLETS[level.coerceAtLeast(0) % BULLETS.size]

    fun format(
        value: Int,
        style: NumberStyle,
    ): String =
        when (style) {
            NumberStyle.DECIMAL -> value.toString()
            NumberStyle.LOWER_ALPHA -> alpha(value).lowercase()
            NumberStyle.UPPER_ALPHA -> alpha(value)
            NumberStyle.LOWER_ROMAN -> roman(value).lowercase()
            NumberStyle.UPPER_ROMAN -> roman(value)
            NumberStyle.NONE -> ""
        }

    private fun alpha(value: Int): String {
        if (value <= 0) return value.toString()
        val builder = StringBuilder()
        var remaining = value
        while (remaining > 0) {
            remaining--
            builder.append('A' + remaining % 26)
            remaining /= 26
        }
        return builder.reverse().toString()
    }

    private fun roman(value: Int): String {
        if (value <= 0 || value >= 4000) return value.toString()
        val numerals =
            listOf(
                1000 to "M",
                900 to "CM",
                500 to "D",
                400 to "CD",
                100 to "C",
                90 to "XC",
                50 to "L",
                40 to "XL",
                10 to "X",
                9 to "IX",
                5 to "V",
                4 to "IV",
                1 to "I",
            )
        val builder = StringBuilder()
        var remaining = value
        for ((amount, numeral) in numerals) {
            while (remaining >= amount) {
                builder.append(numeral)
                remaining -= amount
            }
        }
        return builder.toString()
    }

    fun normalizeBulletGlyph(text: String): String {
        if (text.isEmpty()) return "•"
        val mapped =
            text.map { char ->
                when {
                    char == '' || char == '' || char == '' || char == '' || char in ''..'' -> '•'
                    char == 'o' && text.length == 1 -> '◦'
                    char == '§' && text.length == 1 -> '▪'
                    char == '·' -> '•'
                    else -> char
                }
            }
        return String(mapped.toCharArray())
    }
}

enum class NumberStyle { DECIMAL, LOWER_ALPHA, UPPER_ALPHA, LOWER_ROMAN, UPPER_ROMAN, NONE }
