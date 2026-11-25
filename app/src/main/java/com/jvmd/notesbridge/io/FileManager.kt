package com.jvmd.notesbridge.io

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jvmd.notesbridge.convert.ConversionException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class FileManager(
    context: Context,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val cacheRoot: File = context.cacheDir
    val inputDirectory: File get() = File(cacheRoot, INPUT_DIRECTORY).apply { mkdirs() }
    val outputDirectory: File get() = File(cacheRoot, OUTPUT_DIRECTORY).apply { mkdirs() }

    data class ImportedFile(
        val file: File,
        val displayName: String,
        val mimeType: String?,
    )

    fun import(
        uri: Uri,
        declaredMimeType: String?,
        maxBytes: Long,
    ): ImportedFile {
        val (queriedName, size) = queryMetadata(uri)
        if (size != null && size > maxBytes) throw ConversionException.TooLarge(maxBytes)
        val displayName = sanitizeName(queriedName ?: uri.lastPathSegment ?: DEFAULT_NAME)
        val mimeType = declaredMimeType ?: runCatching { resolver.getType(uri) }.getOrNull()
        val target = File(inputDirectory, "${UUID.randomUUID()}.bin")
        try {
            val input = resolver.openInputStream(uri) ?: throw ConversionException.ReadFailed(FileNotFoundException(uri.toString()))
            input.use { source ->
                LimitedInputStream(source, maxBytes).use { limited ->
                    target.outputStream().use { output -> limited.copyTo(output) }
                }
            }
        } catch (error: ConversionException) {
            target.delete()
            throw error
        } catch (error: SecurityException) {
            target.delete()
            throw ConversionException.ReadFailed(error)
        } catch (error: IOException) {
            target.delete()
            throw ConversionException.ReadFailed(error)
        }
        return ImportedFile(target, displayName, mimeType)
    }

    fun importText(
        text: String,
        title: String?,
    ): ImportedFile {
        val target = File(inputDirectory, "${UUID.randomUUID()}.txt")
        target.writeText(text, Charsets.UTF_8)
        val name = sanitizeName(title?.takeIf { it.isNotBlank() }?.let { "$it.txt" } ?: "Shared text.txt")
        return ImportedFile(target, name, "text/plain")
    }

    fun newOutputFile(baseName: String): File {
        val directory = File(outputDirectory, UUID.randomUUID().toString()).apply { mkdirs() }
        return File(directory, "${sanitizeName(baseName).substringBeforeLast('.').ifBlank { DEFAULT_NAME }}.pdf")
    }

    fun deleteInputs(files: Collection<File>) {
        files.forEach { it.delete() }
    }

    fun cleanUp(maxAgeMillis: Long = OUTPUT_RETENTION_MILLIS) {
        val now = System.currentTimeMillis()
        inputDirectory.listFiles()?.forEach { file ->
            if (now - file.lastModified() > INPUT_RETENTION_MILLIS) file.deleteRecursively()
        }
        outputDirectory.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMillis) file.deleteRecursively()
        }
    }

    private fun queryMetadata(uri: Uri): Pair<String?, Long?> {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = uri.path?.let { File(it) }
            return file?.name to file?.length()
        }
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null to null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                name to size
            } ?: (null to null)
        } catch (_: SecurityException) {
            null to null
        } catch (_: IllegalArgumentException) {
            null to null
        } catch (_: UnsupportedOperationException) {
            null to null
        }
    }

    companion object {
        private const val INPUT_DIRECTORY = "input"
        private const val OUTPUT_DIRECTORY = "output"
        private const val DEFAULT_NAME = "document"
        private val INPUT_RETENTION_MILLIS = TimeUnit.MINUTES.toMillis(30)
        val OUTPUT_RETENTION_MILLIS: Long = TimeUnit.HOURS.toMillis(1)
        private val UNSAFE_CHARACTERS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        fun sanitizeName(name: String): String {
            val cleaned = name.replace(UNSAFE_CHARACTERS, "_").trim().trimStart('.')
            return cleaned.take(120).ifBlank { DEFAULT_NAME }
        }
    }
}
