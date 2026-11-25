package com.jvmd.notesbridge.formats.xlsx

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

sealed interface NumberFormatKind {
    data object General : NumberFormatKind

    data class Fixed(
        val decimals: Int,
        val grouping: Boolean,
    ) : NumberFormatKind

    data class Percent(
        val decimals: Int,
    ) : NumberFormatKind

    data class DateTime(
        val hasDate: Boolean,
        val hasTime: Boolean,
        val hasSeconds: Boolean,
    ) : NumberFormatKind

    data object Text : NumberFormatKind
}

object XlsxNumberFormats {
    private val BUILT_IN: Map<Int, String> =
        mapOf(
            1 to "0",
            2 to "0.00",
            3 to "#,##0",
            4 to "#,##0.00",
            9 to "0%",
            10 to "0.00%",
            14 to "yyyy-mm-dd",
            15 to "d-mmm-yy",
            16 to "d-mmm",
            17 to "mmm-yy",
            18 to "h:mm AM/PM",
            19 to "h:mm:ss AM/PM",
            20 to "h:mm",
            21 to "h:mm:ss",
            22 to "yyyy-mm-dd h:mm",
            37 to "#,##0",
            38 to "#,##0",
            39 to "#,##0.00",
            40 to "#,##0.00",
            45 to "mm:ss",
            46 to "[h]:mm:ss",
            47 to "mm:ss.0",
            49 to "@",
        )

    fun classify(
        formatId: Int,
        customCode: String?,
    ): NumberFormatKind {
        if (formatId == 0) return NumberFormatKind.General
        val code = customCode ?: BUILT_IN[formatId] ?: return NumberFormatKind.General
        return classifyCode(code)
    }

    fun classifyCode(rawCode: String): NumberFormatKind {
        val code = rawCode.substringBefore(';')
        if (code.equals("General", ignoreCase = true)) return NumberFormatKind.General
        if (code == "@") return NumberFormatKind.Text
        val stripped =
            code
                .replace(Regex("\"[^\"]*\""), "")
                .replace(Regex("\\\\."), "")
                .replace(Regex("\\[(?![hms]+\\])[^\\]]*\\]", RegexOption.IGNORE_CASE), "")
                .lowercase()
        val hasDate =
            stripped.any { it == 'y' || it == 'd' } || (stripped.contains('m') && !stripped.contains('h') && !stripped.contains('s'))
        val hasTime = stripped.contains('h') || stripped.contains('s')
        if (hasDate || hasTime) return NumberFormatKind.DateTime(hasDate, hasTime, stripped.contains('s'))
        val decimals = stripped.substringAfter('.', "").takeWhile { it == '0' || it == '#' }.length
        if (stripped.contains('%')) return NumberFormatKind.Percent(decimals)
        if (stripped.contains('0') || stripped.contains('#')) return NumberFormatKind.Fixed(decimals, stripped.contains(','))
        return NumberFormatKind.General
    }

    fun format(
        raw: String,
        kind: NumberFormatKind,
        date1904: Boolean,
    ): String {
        val value = raw.toDoubleOrNull() ?: return raw
        return when (kind) {
            NumberFormatKind.General, NumberFormatKind.Text -> general(value)
            is NumberFormatKind.Fixed -> fixed(value, kind.decimals, kind.grouping)
            is NumberFormatKind.Percent -> fixed(value * 100, kind.decimals, false) + "%"
            is NumberFormatKind.DateTime -> dateTime(value, kind, date1904) ?: general(value)
        }
    }

    fun general(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        if (value == floor(value) && abs(value) < 1e15) return value.toLong().toString()
        val magnitude = abs(value)
        val decimal = BigDecimal(value).round(MathContext(11, RoundingMode.HALF_UP)).stripTrailingZeros()
        return if (magnitude >= 1e-5 && magnitude < 1e15) decimal.toPlainString() else decimal.toString().replace("E+", "E")
    }

    private fun fixed(
        value: Double,
        decimals: Int,
        grouping: Boolean,
    ): String {
        val pattern = (if (grouping) "#,##0" else "0") + if (decimals > 0) "." + "0".repeat(decimals) else ""
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).apply { roundingMode = RoundingMode.HALF_UP }.format(value)
    }

    private fun dateTime(
        value: Double,
        kind: NumberFormatKind.DateTime,
        date1904: Boolean,
    ): String? {
        if (value < 0 || value > 2_958_465) return null
        val wholeDays = floor(value).toLong()
        val seconds = ((value - wholeDays) * 86_400).roundToLong()
        val base = if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)
        val days = if (!date1904 && wholeDays < 61) wholeDays + 1 else wholeDays
        val dateTime = base.plusDays(days).atStartOfDay().plusSeconds(seconds)
        val pattern =
            when {
                kind.hasDate && kind.hasTime -> if (kind.hasSeconds) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd HH:mm"
                kind.hasTime -> if (kind.hasSeconds) "HH:mm:ss" else "HH:mm"
                else -> "yyyy-MM-dd"
            }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT))
    }
}
