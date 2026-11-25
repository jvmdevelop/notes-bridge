package com.jvmd.notesbridge.formats.pptx

import com.jvmd.notesbridge.model.PathCommand
import com.jvmd.notesbridge.model.ShapeOutline
import kotlin.math.cos
import kotlin.math.sin

object PresetGeometry {
    fun outline(
        preset: String?,
        adjustment: Float?,
    ): ShapeOutline =
        when (preset) {
            null, in RECTANGULAR -> {
                ShapeOutline.Rectangle
            }

            in ROUNDED -> {
                ShapeOutline.RoundedRectangle(adjustment ?: 0.16667f)
            }

            "flowChartTerminator" -> {
                ShapeOutline.RoundedRectangle(0.5f)
            }

            in ELLIPTICAL -> {
                ShapeOutline.Ellipse
            }

            in LINEAR -> {
                ShapeOutline.Line
            }

            "triangle", "flowChartExtract" -> {
                polygon(0.5f to 0f, 1f to 1f, 0f to 1f)
            }

            "flowChartMerge" -> {
                polygon(0f to 0f, 1f to 0f, 0.5f to 1f)
            }

            "rtTriangle" -> {
                polygon(0f to 0f, 1f to 1f, 0f to 1f)
            }

            "diamond", "flowChartDecision" -> {
                polygon(0.5f to 0f, 1f to 0.5f, 0.5f to 1f, 0f to 0.5f)
            }

            "parallelogram", "flowChartInputOutput" -> {
                polygon(0.25f to 0f, 1f to 0f, 0.75f to 1f, 0f to 1f)
            }

            "trapezoid", "flowChartManualOperation" -> {
                polygon(0f to 1f, 0.25f to 0f, 0.75f to 0f, 1f to 1f)
            }

            "pentagon" -> {
                regular(5)
            }

            "hexagon", "flowChartPreparation" -> {
                polygon(0.25f to 0f, 0.75f to 0f, 1f to 0.5f, 0.75f to 1f, 0.25f to 1f, 0f to 0.5f)
            }

            "heptagon" -> {
                regular(7)
            }

            "octagon" -> {
                polygon(0.29f to 0f, 0.71f to 0f, 1f to 0.29f, 1f to 0.71f, 0.71f to 1f, 0.29f to 1f, 0f to 0.71f, 0f to 0.29f)
            }

            "decagon" -> {
                regular(10)
            }

            "dodecagon" -> {
                regular(12)
            }

            "homePlate", "flowChartOffpageConnector" -> {
                polygon(0f to 0f, 0.8f to 0f, 1f to 0.5f, 0.8f to 1f, 0f to 1f)
            }

            "chevron" -> {
                polygon(0f to 0f, 0.8f to 0f, 1f to 0.5f, 0.8f to 1f, 0f to 1f, 0.2f to 0.5f)
            }

            "rightArrow", "stripedRightArrow", "notchedRightArrow" -> {
                polygon(
                    0f to 0.25f,
                    0.7f to 0.25f,
                    0.7f to 0f,
                    1f to 0.5f,
                    0.7f to 1f,
                    0.7f to 0.75f,
                    0f to 0.75f,
                )
            }

            "leftArrow" -> {
                polygon(1f to 0.25f, 0.3f to 0.25f, 0.3f to 0f, 0f to 0.5f, 0.3f to 1f, 0.3f to 0.75f, 1f to 0.75f)
            }

            "upArrow" -> {
                polygon(0.25f to 1f, 0.25f to 0.3f, 0f to 0.3f, 0.5f to 0f, 1f to 0.3f, 0.75f to 0.3f, 0.75f to 1f)
            }

            "downArrow" -> {
                polygon(0.25f to 0f, 0.75f to 0f, 0.75f to 0.7f, 1f to 0.7f, 0.5f to 1f, 0f to 0.7f, 0.25f to 0.7f)
            }

            "leftRightArrow" -> {
                polygon(
                    0f to 0.5f,
                    0.2f to 0f,
                    0.2f to 0.25f,
                    0.8f to 0.25f,
                    0.8f to 0f,
                    1f to 0.5f,
                    0.8f to 1f,
                    0.8f to 0.75f,
                    0.2f to 0.75f,
                    0.2f to 1f,
                )
            }

            "plus", "flowChartPunchedTape", "mathPlus" -> {
                polygon(
                    0.35f to 0f,
                    0.65f to 0f,
                    0.65f to 0.35f,
                    1f to 0.35f,
                    1f to 0.65f,
                    0.65f to 0.65f,
                    0.65f to 1f,
                    0.35f to 1f,
                    0.35f to 0.65f,
                    0f to 0.65f,
                    0f to 0.35f,
                    0.35f to 0.35f,
                )
            }

            "star4" -> {
                star(4, 0.38f)
            }

            "star5" -> {
                star(5, 0.38f)
            }

            "star6" -> {
                star(6, 0.5f)
            }

            "star8" -> {
                star(8, 0.6f)
            }

            "star10", "star12", "star16", "star24", "star32" -> {
                star(preset.removePrefix("star").toInt(), 0.75f)
            }

            else -> {
                ShapeOutline.Rectangle
            }
        }

    private val ROUNDED =
        setOf(
            "roundRect",
            "flowChartAlternateProcess",
            "round1Rect",
            "round2SameRect",
            "round2DiagRect",
            "wedgeRoundRectCallout",
        )

    private val ELLIPTICAL =
        setOf(
            "ellipse",
            "flowChartConnector",
            "donut",
            "cloud",
            "cloudCallout",
            "wedgeEllipseCallout",
            "flowChartOr",
            "flowChartSummingJunction",
            "pie",
            "chord",
            "smileyFace",
            "noSmoking",
        )

    private val LINEAR =
        setOf(
            "line",
            "straightConnector1",
            "bentConnector2",
            "bentConnector3",
            "bentConnector4",
            "bentConnector5",
            "curvedConnector2",
            "curvedConnector3",
            "curvedConnector4",
            "curvedConnector5",
        )

    private val RECTANGULAR =
        setOf(
            "rect",
            "flowChartProcess",
            "flowChartPredefinedProcess",
            "flowChartInternalStorage",
            "snip1Rect",
            "snip2SameRect",
            "snip2DiagRect",
            "snipRoundRect",
            "frame",
            "can",
            "cube",
            "bevel",
            "plaque",
            "foldedCorner",
            "wedgeRectCallout",
            "flowChartDocument",
            "flowChartMultidocument",
            "flowChartPunchedCard",
        )

    private fun polygon(vararg points: Pair<Float, Float>): ShapeOutline {
        val commands = ArrayList<PathCommand>(points.size + 1)
        points.forEachIndexed { index, (x, y) -> commands.add(if (index == 0) PathCommand.MoveTo(x, y) else PathCommand.LineTo(x, y)) }
        commands.add(PathCommand.Close)
        return ShapeOutline.Path(commands)
    }

    private fun regular(sides: Int): ShapeOutline {
        val points =
            (0 until sides).map { index ->
                val angle = -Math.PI / 2 + 2 * Math.PI * index / sides
                (0.5f + 0.5f * cos(angle).toFloat()) to (0.5f + 0.5f * sin(angle).toFloat())
            }
        return polygon(*points.toTypedArray())
    }

    private fun star(
        tips: Int,
        innerRatio: Float,
    ): ShapeOutline {
        val points =
            (0 until tips * 2).map { index ->
                val angle = -Math.PI / 2 + Math.PI * index / tips
                val radius = if (index % 2 == 0) 0.5f else 0.5f * innerRatio
                (0.5f + radius * cos(angle).toFloat()) to (0.5f + radius * sin(angle).toFloat())
            }
        return polygon(*points.toTypedArray())
    }
}
