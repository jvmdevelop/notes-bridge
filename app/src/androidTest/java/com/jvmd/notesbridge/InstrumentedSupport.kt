package com.jvmd.notesbridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.jvmd.notesbridge.launch.NotesLauncher
import java.io.File

object InstrumentedSupport {
    val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val app: NotesBridgeApplication get() = targetContext.applicationContext as NotesBridgeApplication

    fun fixtureFile(name: String): File {
        val directory = File(targetContext.cacheDir, "test-fixtures").apply { mkdirs() }
        val target = File(directory, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    fun fixtureUri(name: String): Uri = Uri.fromFile(fixtureFile(name))

    fun sharedContentUri(name: String): Uri {
        val directory = File(app.fileManager.outputDirectory, "shared-test").apply { mkdirs() }
        val target = File(directory, name)
        fixtureFile(name).copyTo(target, overwrite = true)
        return FileProvider.getUriForFile(targetContext, NotesLauncher.authority(targetContext), target)
    }

    data class PdfInspection(
        val pageCount: Int,
        val firstPageInk: Double,
        val firstPageWidth: Int,
        val firstPageHeight: Int,
    )

    fun inspectPdf(file: File): PdfInspection {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    var ink = 0
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    for (pixel in pixels) {
                        if (Color.red(pixel) < 235 || Color.green(pixel) < 235 || Color.blue(pixel) < 235) ink++
                    }
                    bitmap.recycle()
                    return PdfInspection(renderer.pageCount, ink.toDouble() / pixels.size, page.width, page.height)
                }
            }
        }
    }

    fun archiveForInspection(
        file: File,
        name: String,
    ) {
        val directory = targetContext.getExternalFilesDir("converted") ?: return
        file.copyTo(File(directory, name), overwrite = true)
    }
}
