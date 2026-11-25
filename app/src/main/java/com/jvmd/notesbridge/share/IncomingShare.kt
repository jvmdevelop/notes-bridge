package com.jvmd.notesbridge.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.jvmd.notesbridge.convert.IncomingDocument

object IncomingShare {
    fun parse(intent: Intent?): List<IncomingDocument> {
        intent ?: return emptyList()
        val type = intent.type?.takeUnless { it == "*/*" || it.endsWith("/*") }
        val uris = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> intent.streamExtra()?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> intent.streamListExtra()?.let { uris.addAll(it) }
            Intent.ACTION_VIEW -> intent.data?.let { uris.add(it) }
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let { uris.add(it) }
        }
        val streams =
            uris.filter { it.scheme == "content" || it.scheme == "file" }.map {
                IncomingDocument.Stream(
                    it,
                    if (uris.size ==
                        1
                    ) {
                        type
                    } else {
                        null
                    },
                )
            }
        if (streams.isNotEmpty()) return streams
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (intent.action == Intent.ACTION_SEND && !text.isNullOrBlank()) {
            return listOf(
                IncomingDocument.Text(
                    text,
                    intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE),
                ),
            )
        }
        return emptyList()
    }

    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.streamListExtra(): List<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
}
