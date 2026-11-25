package com.jvmd.notesbridge.convert

import com.jvmd.notesbridge.detect.DocumentFormat
import com.jvmd.notesbridge.io.DocumentArchive
import java.io.Closeable
import java.io.File

data class DocumentSource(
    val file: File,
    val displayName: String,
    val format: DocumentFormat,
)

class ReaderInput(
    val source: DocumentSource,
    private val limits: ConversionLimits,
) : Closeable {
    private var openedArchive: DocumentArchive? = null

    val archive: DocumentArchive
        get() = openedArchive ?: DocumentArchive(source.file, limits).also { openedArchive = it }

    fun readTextBytes(): ByteArray {
        val length = source.file.length()
        if (length > limits.maxTextBytes) throw ConversionException.TooLarge(limits.maxTextBytes)
        return source.file.readBytes()
    }

    override fun close() {
        openedArchive?.close()
        openedArchive = null
    }
}

fun interface DocumentReader<T> {
    fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): T
}
