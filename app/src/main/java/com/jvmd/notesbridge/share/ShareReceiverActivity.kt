package com.jvmd.notesbridge.share

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jvmd.notesbridge.NotesBridgeApplication
import com.jvmd.notesbridge.R
import com.jvmd.notesbridge.convert.ConversionProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ShareReceiverActivity : AppCompatActivity() {
    private val viewModel: ShareViewModel by viewModels()
    private lateinit var content: View
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var warnings: TextView
    private lateinit var detail: TextView
    private lateinit var primaryButton: MaterialButton
    private lateinit var openWithButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var dismissButton: MaterialButton

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME_TYPE)) { uri ->
            if (uri != null) viewModel.saveTo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)
        setFinishOnTouchOutside(false)
        content = findViewById(R.id.share_content)
        title = findViewById(R.id.share_title)
        subtitle = findViewById(R.id.share_subtitle)
        progress = findViewById(R.id.share_progress)
        warnings = findViewById(R.id.share_warnings)
        detail = findViewById(R.id.share_detail)
        primaryButton = findViewById(R.id.share_primary)
        openWithButton = findViewById(R.id.share_open_with)
        shareButton = findViewById(R.id.share_share)
        saveButton = findViewById(R.id.share_save)
        dismissButton = findViewById(R.id.share_dismiss)
        content.visibility = View.INVISIBLE
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.cancel()
                    finish()
                }
            },
        )
        if (savedInstanceState == null) viewModel.start(IncomingShare.parse(intent))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch {
                    viewModel.saveState.collect { state ->
                        when (state) {
                            SaveState.Saved -> {
                                Toast.makeText(this@ShareReceiverActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                                finish()
                            }

                            is SaveState.Failed -> {
                                Toast.makeText(this@ShareReceiverActivity, R.string.save_failed, Toast.LENGTH_LONG).show()
                            }

                            SaveState.None -> {
                                Unit
                            }
                        }
                    }
                }
                launch {
                    delay(PROGRESS_REVEAL_DELAY_MS)
                    content.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun render(state: ShareUiState) {
        val app = application as NotesBridgeApplication
        when (state) {
            ShareUiState.Idle, is ShareUiState.Working -> {
                title.setText(R.string.converting)
                subtitle.text = progressText(state as? ShareUiState.Working)
                progress.visibility = View.VISIBLE
                warnings.visibility = View.GONE
                detail.visibility = View.GONE
                listOf(primaryButton, openWithButton, shareButton, saveButton).forEach { it.visibility = View.GONE }
                dismissButton.setText(R.string.cancel)
                dismissButton.setOnClickListener {
                    viewModel.cancel()
                    finish()
                }
            }

            is ShareUiState.Success -> {
                content.visibility = View.VISIBLE
                val result = state.result
                title.setText(R.string.pdf_ready)
                subtitle.text = resources.getQuantityString(R.plurals.pdf_summary, result.pageCount, result.file.name, result.pageCount)
                progress.visibility = View.GONE
                detail.visibility = View.GONE
                if (result.warnings.isEmpty()) {
                    warnings.visibility = View.GONE
                } else {
                    warnings.visibility = View.VISIBLE
                    warnings.text = result.warnings.joinToString("\n") { "• " + ErrorMessages.warning(this, it) }
                }
                primaryButton.visibility = if (state.samsungNotesAvailable) View.VISIBLE else View.GONE
                primaryButton.setOnClickListener { launch { app.launcher.samsungNotesIntent(result.file) } }
                openWithButton.visibility = if (state.viewerAvailable) View.VISIBLE else View.GONE
                openWithButton.setOnClickListener { launch { app.launcher.openWithChooser(result.file, getString(R.string.open_with)) } }
                shareButton.visibility = View.VISIBLE
                shareButton.setOnClickListener { launch { app.launcher.shareChooser(result.file, getString(R.string.share_pdf)) } }
                saveButton.visibility = View.VISIBLE
                saveButton.setOnClickListener { createDocument.launch(result.file.name) }
                dismissButton.setText(R.string.close)
                dismissButton.setOnClickListener { finish() }
            }

            is ShareUiState.Failure -> {
                content.visibility = View.VISIBLE
                title.setText(R.string.conversion_failed)
                subtitle.text = ErrorMessages.describe(this, state.error)
                progress.visibility = View.GONE
                warnings.visibility = View.GONE
                detail.visibility = View.VISIBLE
                detail.text = ErrorMessages.detail(state.error)
                listOf(primaryButton, openWithButton, shareButton, saveButton).forEach { it.visibility = View.GONE }
                dismissButton.setText(R.string.close)
                dismissButton.setOnClickListener { finish() }
            }

            ShareUiState.Cancelled -> {
                finish()
            }
        }
    }

    private fun progressText(state: ShareUiState.Working?): String =
        when (val progress = state?.progress) {
            null -> {
                getString(R.string.preparing)
            }

            is ConversionProgress.Importing -> {
                getString(R.string.progress_importing, progress.index, progress.total)
            }

            is ConversionProgress.Converting -> {
                if (progress.total > 1) {
                    getString(R.string.progress_converting_multiple, progress.index, progress.total, progress.fileName, progress.pages)
                } else {
                    getString(R.string.progress_converting, progress.fileName, progress.pages)
                }
            }

            ConversionProgress.Writing -> {
                getString(R.string.progress_writing)
            }
        }

    private fun launch(intentFactory: () -> Intent?) {
        val intent = intentFactory()
        if (intent == null) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(intent)
            finish()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        const val PROGRESS_REVEAL_DELAY_MS = 350L
    }
}
