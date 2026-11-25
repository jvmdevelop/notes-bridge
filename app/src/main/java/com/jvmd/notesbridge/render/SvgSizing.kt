package com.jvmd.notesbridge.render

import com.caverock.androidsvg.SVG

object SvgSizing {
    private const val DEFAULT_SIZE = 512f

    fun intrinsicSize(svg: SVG): Pair<Float, Float>? {
        val viewBox = svg.documentViewBox
        val width = svg.documentWidth.takeIf { it > 0 }
        val height = svg.documentHeight.takeIf { it > 0 }
        return when {
            width != null && height != null -> {
                width to height
            }

            viewBox != null && viewBox.width() > 0 && viewBox.height() > 0 -> {
                val ratio = viewBox.height() / viewBox.width()
                when {
                    width != null -> width to width * ratio
                    height != null -> height / ratio to height
                    else -> viewBox.width() to viewBox.height()
                }
            }

            width != null -> {
                width to width
            }

            height != null -> {
                height to height
            }

            else -> {
                DEFAULT_SIZE to DEFAULT_SIZE
            }
        }.takeIf { it.first > 0 && it.second > 0 }
    }
}
