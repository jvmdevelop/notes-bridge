package com.jvmd.notesbridge.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.model.ImageSource
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapLoader {
    private const val MAX_SOURCE_PIXELS = 400_000_000L

    fun load(
        source: ImageSource,
        maxDimensionPx: Int,
    ): Bitmap? =
        try {
            decodeRaster(source, maxDimensionPx) ?: decodeSvg(source, maxDimensionPx)
        } catch (error: ConversionException) {
            throw error
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }

    private fun decodeRaster(
        source: ImageSource,
        maxDimensionPx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimensionPx) sample *= 2
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val decoded = source.open().use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val oriented = applyOrientation(decoded, readOrientation(source))
        return downscale(oriented, maxDimensionPx)
    }

    private fun decodeSvg(
        source: ImageSource,
        maxDimensionPx: Int,
    ): Bitmap? {
        val svg =
            try {
                source.open().use { SVG.getFromInputStream(it) }
            } catch (_: SVGParseException) {
                return null
            }
        val (width, height) = SvgSizing.intrinsicSize(svg) ?: return null
        val scale = maxDimensionPx / max(width, height)
        val bitmap =
            createBitmap(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
        bitmap.eraseColor(Color.TRANSPARENT)
        svg.setDocumentWidth(bitmap.width.toFloat())
        svg.setDocumentHeight(bitmap.height.toFloat())
        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
    }

    private fun readOrientation(source: ImageSource): Int =
        try {
            source.open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        } catch (_: RuntimeException) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun applyOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
            }

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> {
                return bitmap
            }
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun downscale(
        bitmap: Bitmap,
        maxDimensionPx: Int,
    ): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimensionPx) return bitmap
        val ratio = maxDimensionPx.toFloat() / largest
        val scaled =
            bitmap.scale(
                ceil(bitmap.width * ratio).toInt().coerceAtLeast(1),
                ceil(bitmap.height * ratio).toInt().coerceAtLeast(1),
            )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
