package com.jvmd.notesbridge.convert

import android.net.Uri
import com.jvmd.notesbridge.detect.DetectionResult
import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.detect.FormatDetector
import com.jvmd.notesbridge.detect.UnsupportedReason
import com.jvmd.notesbridge.io.FileManager
import com.jvmd.notesbridge.render.PdfSink
import com.jvmd.notesbridge.xml.XmlTreeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface IncomingDocument {
    data class Stream(
        val uri: Uri,
        val mimeType: String?,
    ) : IncomingDocument

    data class Text(
        val text: String,
        val title: String?,
    ) : IncomingDocument
}

sealed interface ConversionProgress {
    data class Importing(
        val index: Int,
        val total: Int,
    ) : ConversionProgress

    data class Converting(
        val index: Int,
        val total: Int,
        val fileName: String,
        val pages: Int,
    ) : ConversionProgress

    data object Writing : ConversionProgress
}

data class ConversionResult(
    val file: File,
    val pageCount: Int,
    val sourceNames: List<String>,
    val formats: List<DocumentFormat>,
    val warnings: Set<ConversionWarning>,
)

class ConversionManager(
    private val fileManager: FileManager,
    private val detector: FormatDetector,
    private val registry: ConverterRegistry,
    private val limits: ConversionLimits,
    private val xmlReader: XmlTreeReader,
) {
    suspend fun convert(
        documents: List<IncomingDocument>,
        onProgress: (ConversionProgress) -> Unit,
    ): ConversionResult =
        withContext(Dispatchers.Default) {
            if (documents.isEmpty()) throw ConversionException.NoInput()
            if (documents.size > limits.maxSharedFiles) throw ConversionException.TooManyFiles(limits.maxSharedFiles)
            val imported = ArrayList<FileManager.ImportedFile>()
            var output: File? = null
            try {
                val sources = ArrayList<DocumentSource>()
                for ((index, document) in documents.withIndex()) {
                    onProgress(ConversionProgress.Importing(index + 1, documents.size))
                    val file =
                        withContext(Dispatchers.IO) {
                            when (document) {
                                is IncomingDocument.Stream -> fileManager.import(document.uri, document.mimeType, limits.maxInputBytes)
                                is IncomingDocument.Text -> fileManager.importText(document.text, document.title)
                            }
                        }
                    imported.add(file)
                    sources.add(DocumentSource(file.file, file.displayName, detect(file)))
                }
                val job = currentCoroutineContext()[kotlinx.coroutines.Job]
                val context = ConversionContext(limits, xmlReader, job)
                val target =
                    fileManager.newOutputFile(
                        sources.first().displayName.let {
                            if (sources.size >
                                1
                            ) {
                                "${it.substringBeforeLast('.')} +${sources.size - 1}"
                            } else {
                                it
                            }
                        },
                    )
                output = target
                var currentIndex = 0
                PdfSink(limits.maxPages) { pages ->
                    onProgress(ConversionProgress.Converting(currentIndex + 1, sources.size, sources[currentIndex].displayName, pages))
                }.use { sink ->
                    for ((index, source) in sources.withIndex()) {
                        currentIndex = index
                        context.checkCancelled()
                        onProgress(ConversionProgress.Converting(index + 1, sources.size, source.displayName, sink.pageCount))
                        val exporter =
                            registry.exporterFor(source.format)
                                ?: throw ConversionException.Unsupported(source.displayName, UnsupportedReason.UNKNOWN)
                        runExporter(exporter, source, sink, context)
                    }
                    context.checkCancelled()
                    onProgress(ConversionProgress.Writing)
                    withContext(Dispatchers.IO) {
                        try {
                            target.outputStream().use { sink.writeTo(it) }
                        } catch (error: IOException) {
                            throw ConversionException.ReadFailed(error)
                        }
                    }
                    ConversionResult(target, sink.pageCount, sources.map { it.displayName }, sources.map { it.format }, context.warnings)
                }
            } catch (error: Throwable) {
                output?.parentFile?.deleteRecursively()
                throw error
            } finally {
                fileManager.deleteInputs(imported.map { it.file })
            }
        }

    private fun runExporter(
        exporter: PdfExporter,
        source: DocumentSource,
        sink: PdfSink,
        context: ConversionContext,
    ) {
        try {
            exporter.export(source, sink, context)
        } catch (error: ConversionException) {
            throw error
        } catch (error: OutOfMemoryError) {
            throw ConversionException.TooComplex("not enough memory to render ${source.displayName}")
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            throw ConversionException.Corrupted(
                "${source.format.displayName} could not be parsed: ${error.message ?: error.javaClass.simpleName}",
                error,
            )
        } catch (error: IOException) {
            throw ConversionException.ReadFailed(error)
        }
    }

    private fun detect(file: FileManager.ImportedFile): DocumentFormat =
        when (val result = detector.detect(file.file, file.displayName, file.mimeType)) {
            is DetectionResult.Supported -> {
                if (registry.exporterFor(result.format) ==
                    null
                ) {
                    throw ConversionException.Unsupported(file.displayName, UnsupportedReason.UNKNOWN)
                }
                result.format
            }

            is DetectionResult.Unsupported -> {
                throw ConversionException.Unsupported(file.displayName, result.reason)
            }
        }
}
