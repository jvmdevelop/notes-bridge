package com.jvmd.notesbridge.render

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.Baseline
import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.model.FontFamily
import com.jvmd.notesbridge.model.TextRun
import com.jvmd.notesbridge.model.TextStyle
import kotlin.math.ceil

internal data class TextDefaults(
    val sizePt: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val family: FontFamily = FontFamily.SANS,
    val color: Int = Colors.BLACK,
)

internal object TextRendering {
    private val baseTypefaces =
        mapOf(
            FontFamily.SANS to Typeface.SANS_SERIF,
            FontFamily.SERIF to Typeface.SERIF,
            FontFamily.MONO to Typeface.MONOSPACE,
        )

    fun typeface(
        family: FontFamily,
        bold: Boolean,
        italic: Boolean,
    ): Typeface {
        val style =
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        return Typeface.create(baseTypefaces.getValue(family), style)
    }

    fun paint(defaults: TextDefaults): TextPaint =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = defaults.sizePt.units
            color = defaults.color
            typeface = typeface(defaults.family, defaults.bold, defaults.italic)
        }

    fun spanned(
        runs: List<TextRun>,
        defaults: TextDefaults,
        sizeScale: Float = 1f,
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val start = builder.length
            builder.append(run.text)
            applyStyle(builder, start, builder.length, run.style, defaults, sizeScale)
        }
        return builder
    }

    private fun applyStyle(
        builder: SpannableStringBuilder,
        start: Int,
        end: Int,
        style: TextStyle,
        defaults: TextDefaults,
        sizeScale: Float,
    ) {
        val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        val bold = style.bold ?: defaults.bold
        val italic = style.italic || defaults.italic
        val family = style.family ?: defaults.family
        if (bold != defaults.bold || italic != defaults.italic || family != defaults.family) {
            builder.setSpan(TypefaceStyleSpan(typeface(family, bold, italic)), start, end, flags)
        }
        val size = style.fontSizePt?.let { it * sizeScale } ?: defaults.sizePt
        val baselineFactor = if (style.baseline == Baseline.NORMAL) 1f else 0.7f
        val relative = size * baselineFactor / defaults.sizePt
        if (relative != 1f) builder.setSpan(RelativeSizeSpan(relative), start, end, flags)
        when (style.baseline) {
            Baseline.SUPERSCRIPT -> builder.setSpan(BaselineShiftSpan(0.45f), start, end, flags)
            Baseline.SUBSCRIPT -> builder.setSpan(BaselineShiftSpan(-0.2f), start, end, flags)
            Baseline.NORMAL -> Unit
        }
        style.color?.let { if (it != defaults.color) builder.setSpan(ForegroundColorSpan(it), start, end, flags) }
        if (style.underline) builder.setSpan(UnderlineSpan(), start, end, flags)
        if (style.strikethrough) builder.setSpan(StrikethroughSpan(), start, end, flags)
    }

    @SuppressLint("WrongConstant")
    fun layout(
        text: CharSequence,
        paint: TextPaint,
        widthUnits: Float,
        alignment: Alignment,
        lineSpacing: Float = 1f,
        wrap: Boolean = true,
    ): StaticLayout {
        val width =
            if (wrap) {
                widthUnits.toInt().coerceAtLeast(1)
            } else {
                ceil(Layout.getDesiredWidth(text, paint)).toInt().coerceAtLeast(widthUnits.toInt().coerceAtLeast(1))
            }
        val builder =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(
                    when (alignment) {
                        Alignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                        Alignment.END -> Layout.Alignment.ALIGN_OPPOSITE
                        Alignment.START, Alignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
                    },
                ).setLineSpacing(0f, lineSpacing.coerceIn(0.5f, 4f))
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        if (alignment == Alignment.JUSTIFY) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return builder.build()
    }

    fun desiredWidth(
        text: CharSequence,
        paint: TextPaint,
    ): Float = Layout.getDesiredWidth(text, paint)

    private class TypefaceStyleSpan(
        private val typeface: Typeface,
    ) : MetricAffectingSpan() {
        override fun updateDrawState(paint: TextPaint) {
            apply(paint)
        }

        override fun updateMeasureState(paint: TextPaint) {
            apply(paint)
        }

        private fun apply(paint: TextPaint) {
            paint.typeface = typeface
        }
    }

    private class BaselineShiftSpan(
        private val factor: Float,
    ) : MetricAffectingSpan() {
        override fun updateDrawState(paint: TextPaint) {
            paint.baselineShift += (paint.ascent() * factor).toInt()
        }

        override fun updateMeasureState(paint: TextPaint) {
            paint.baselineShift += (paint.ascent() * factor).toInt()
        }
    }
}
