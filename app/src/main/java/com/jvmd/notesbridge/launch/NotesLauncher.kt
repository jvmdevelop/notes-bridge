package com.jvmd.notesbridge.launch

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class NotesLauncher(
    private val context: Context,
) {
    fun contentUri(file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    fun samsungNotesIntent(file: File): Intent? {
        val uri = contentUri(file)
        val candidates =
            listOf(
                viewIntent(uri).setPackage(SAMSUNG_NOTES_PACKAGE),
                sendIntent(uri, file.name).setPackage(SAMSUNG_NOTES_PACKAGE),
            )
        return candidates.firstOrNull { resolves(it) }
    }

    fun isSamsungNotesAvailable(file: File): Boolean = samsungNotesIntent(file) != null

    fun shareChooser(
        file: File,
        title: CharSequence,
    ): Intent = Intent.createChooser(sendIntent(contentUri(file), file.name), title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun openWithChooser(
        file: File,
        title: CharSequence,
    ): Intent = Intent.createChooser(viewIntent(contentUri(file)), title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun canOpenWithViewer(file: File): Boolean = resolves(viewIntent(contentUri(file)))

    private fun sendIntent(
        uri: Uri,
        name: String,
    ): Intent =
        Intent(Intent.ACTION_SEND)
            .setType(PDF_MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newUri(context.contentResolver, name, uri) }

    private fun viewIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, PDF_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

    companion object {
        const val SAMSUNG_NOTES_PACKAGE = "com.samsung.android.app.notes"
        const val PDF_MIME_TYPE = "application/pdf"

        fun authority(context: Context): String = "${context.packageName}.files"
    }
}
