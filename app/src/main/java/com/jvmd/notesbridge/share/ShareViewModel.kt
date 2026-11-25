package com.jvmd.notesbridge.share

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jvmd.notesbridge.NotesBridgeApplication
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionProgress
import com.jvmd.notesbridge.convert.ConversionResult
import com.jvmd.notesbridge.convert.IncomingDocument
import com.jvmd.notesbridge.io.CleanupScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ShareUiState {
    data object Idle : ShareUiState

    data class Working(
        val progress: ConversionProgress?,
    ) : ShareUiState

    data class Success(
        val result: ConversionResult,
        val samsungNotesAvailable: Boolean,
        val viewerAvailable: Boolean,
    ) : ShareUiState

    data class Failure(
        val error: Throwable,
    ) : ShareUiState

    data object Cancelled : ShareUiState
}

sealed interface SaveState {
    data object None : SaveState

    data object Saved : SaveState

    data class Failed(
        val error: Throwable,
    ) : SaveState
}

class ShareViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application as NotesBridgeApplication
    private val mutableState = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val state: StateFlow<ShareUiState> = mutableState.asStateFlow()
    private val mutableSaveState = MutableStateFlow<SaveState>(SaveState.None)
    val saveState: StateFlow<SaveState> = mutableSaveState.asStateFlow()
    private var job: Job? = null

    fun start(documents: List<IncomingDocument>) {
        if (mutableState.value != ShareUiState.Idle) return
        mutableState.value = ShareUiState.Working(null)
        job =
            viewModelScope.launch {
                withContext(Dispatchers.IO) { app.fileManager.cleanUp() }
                try {
                    val result =
                        app.conversionManager.convert(documents) { progress ->
                            mutableState.value = ShareUiState.Working(progress)
                        }
                    CleanupScheduler.schedule(app)
                    mutableState.value =
                        ShareUiState.Success(
                            result = result,
                            samsungNotesAvailable = app.launcher.isSamsungNotesAvailable(result.file),
                            viewerAvailable = app.launcher.canOpenWithViewer(result.file),
                        )
                } catch (error: CancellationException) {
                    mutableState.value = ShareUiState.Cancelled
                    throw error
                } catch (error: ConversionException) {
                    mutableState.value = ShareUiState.Failure(error)
                } catch (error: OutOfMemoryError) {
                    mutableState.value = ShareUiState.Failure(ConversionException.TooComplex("not enough memory"))
                } catch (error: RuntimeException) {
                    mutableState.value = ShareUiState.Failure(error)
                }
            }
    }

    fun cancel() {
        job?.cancel()
        if (mutableState.value is ShareUiState.Working ||
            mutableState.value == ShareUiState.Idle
        ) {
            mutableState.value = ShareUiState.Cancelled
        }
    }

    fun saveTo(target: Uri) {
        val success = mutableState.value as? ShareUiState.Success ?: return
        viewModelScope.launch {
            mutableSaveState.value =
                withContext(Dispatchers.IO) {
                    try {
                        val output = app.contentResolver.openOutputStream(target, "wt") ?: throw IOException("Cannot open $target")
                        output.use { stream ->
                            success.result.file
                                .inputStream()
                                .use { it.copyTo(stream) }
                        }
                        SaveState.Saved
                    } catch (error: IOException) {
                        SaveState.Failed(error)
                    } catch (error: SecurityException) {
                        SaveState.Failed(error)
                    }
                }
        }
    }
}
