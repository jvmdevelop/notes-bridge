package com.jvmd.notesbridge

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jvmd.notesbridge.InstrumentedSupport.app
import com.jvmd.notesbridge.InstrumentedSupport.fixtureUri
import com.jvmd.notesbridge.convert.IncomingDocument
import com.jvmd.notesbridge.io.readUpTo
import com.jvmd.notesbridge.launch.NotesLauncher
import com.jvmd.notesbridge.share.ShareReceiverActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherAndShareFlowTest {
    @Test
    fun shareIntentGrantsReadAccessToFileProviderUri() {
        val result = runBlocking { app.conversionManager.convert(listOf(IncomingDocument.Stream(fixtureUri("sample.md"), null))) {} }
        val chooser = app.launcher.shareChooser(result.file, "Share")
        val target =
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                chooser.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        assertNotNull(target)
        assertEquals(Intent.ACTION_SEND, target!!.action)
        assertEquals(NotesLauncher.PDF_MIME_TYPE, target.type)
        assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri =
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                target.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                target.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        assertEquals("content", uri!!.scheme)
        assertEquals(NotesLauncher.authority(app), uri.authority)
        val header = app.contentResolver.openInputStream(uri)!!.use { String(it.readUpTo(4)) }
        assertEquals("%PDF", header)
        assertEquals(uri, target.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun samsungNotesButtonIsOnlyOfferedWhenInstalled() {
        val result = runBlocking { app.conversionManager.convert(listOf(IncomingDocument.Stream(fixtureUri("sample.txt"), null))) {} }
        val installed =
            runCatching { app.packageManager.getPackageInfo(NotesLauncher.SAMSUNG_NOTES_PACKAGE, 0) }.isSuccess
        if (installed) {
            assertNotNull(
                app.launcher.samsungNotesIntent(result.file),
            )
        } else {
            assertNull(app.launcher.samsungNotesIntent(result.file))
        }
    }

    private fun awaitText(
        scenario: ActivityScenario<ShareReceiverActivity>,
        expected: String,
    ) {
        val deadline = System.currentTimeMillis() + 30_000
        var title = ""
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { title = it.findViewById<TextView>(R.id.share_title).text.toString() }
            if (title == expected) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected title '$expected' but was '$title'")
    }

    @Test
    fun shareReceiverConvertsSharedDocument() {
        val context = InstrumentedSupport.targetContext
        val uri = InstrumentedSupport.sharedContentUri("sample.pptx")
        val intent =
            Intent(context, ShareReceiverActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<ShareReceiverActivity>(intent).use { scenario ->
            awaitText(scenario, context.getString(R.string.pdf_ready))
            scenario.onActivity { activity ->
                val subtitle = activity.findViewById<TextView>(R.id.share_subtitle).text.toString()
                assertTrue(subtitle, subtitle.contains("sample.pdf") && subtitle.contains("2"))
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.share_share).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.share_save).visibility)
            }
        }
    }

    @Test
    fun shareReceiverExplainsUnsupportedDocument() {
        val context = InstrumentedSupport.targetContext
        val uri = InstrumentedSupport.sharedContentUri("sample.doc")
        val intent =
            Intent(context, ShareReceiverActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("application/msword")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<ShareReceiverActivity>(intent).use { scenario ->
            awaitText(scenario, context.getString(R.string.conversion_failed))
            scenario.onActivity { activity ->
                val message = activity.findViewById<TextView>(R.id.share_subtitle).text.toString()
                assertTrue(message, message.contains(".docx"))
            }
        }
    }

    @Test
    fun shareReceiverAcceptsPlainText() {
        val context = InstrumentedSupport.targetContext
        val intent =
            Intent(context, ShareReceiverActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Text shared from another app")
        ActivityScenario.launch<ShareReceiverActivity>(intent).use { scenario ->
            awaitText(scenario, context.getString(R.string.pdf_ready))
        }
    }

    @Test
    fun cancelButtonClosesActivity() {
        val context = InstrumentedSupport.targetContext
        val intent =
            Intent(context, ShareReceiverActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "x".repeat(10))
        val scenario = ActivityScenario.launch<ShareReceiverActivity>(intent)
        scenario.onActivity { it.findViewById<View>(R.id.share_dismiss).performClick() }
        val deadline = System.currentTimeMillis() + 10_000
        while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }
}
