package com.jvmd.notesbridge.io

import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionLimits
import com.jvmd.notesbridge.model.ImageSource
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

class DocumentArchive(
    file: File,
    private val limits: ConversionLimits,
) : Closeable {
    private val zip: ZipFile =
        try {
            ZipFile(file)
        } catch (error: ZipException) {
            throw ConversionException.Corrupted("invalid ZIP container", error)
        } catch (error: IOException) {
            throw ConversionException.ReadFailed(error)
        }

    private val entriesByName: Map<String, ZipEntry>
    private var totalBytesRead = 0L

    init {
        if (zip.size() > limits.maxArchiveEntries) {
            zip.close()
            throw ConversionException.TooComplex("archive has more than ${limits.maxArchiveEntries} entries")
        }
        entriesByName =
            try {
                zip
                    .entries()
                    .asSequence()
                    .filterNot { it.isDirectory }
                    .associateBy { it.name }
            } catch (error: IllegalArgumentException) {
                zip.close()
                throw ConversionException.Corrupted("invalid entry names", error)
            }
    }

    val entryNames: Set<String> get() = entriesByName.keys

    fun contains(path: String): Boolean = find(path) != null

    fun open(path: String): InputStream? {
        val entry = find(path) ?: return null
        val declaredSize = entry.size
        val compressedSize = entry.compressedSize
        if (declaredSize > limits.maxEntryBytes) throw ConversionException.TooLarge(limits.maxEntryBytes)
        if (compressedSize > 0 && declaredSize > 1024 * 1024 && declaredSize / compressedSize > limits.maxCompressionRatio) {
            throw ConversionException.TooComplex("suspicious compression ratio in $path")
        }
        val raw =
            try {
                zip.getInputStream(entry)
            } catch (error: IOException) {
                throw ConversionException.Corrupted("cannot read $path", error)
            }
        return LimitedInputStream(raw, limits.maxEntryBytes) { count -> accountTotal(count) }
    }

    fun readBytes(path: String): ByteArray? = open(path)?.use { it.readBytes() }

    fun readText(path: String): String? = readBytes(path)?.toString(Charsets.UTF_8)

    fun imageSource(path: String): ImageSource? {
        val resolved = find(path)?.name ?: return null
        return ImageSource { open(resolved) ?: throw ConversionException.Corrupted("missing $resolved") }
    }

    private fun find(path: String): ZipEntry? {
        entriesByName[path]?.let { return it }
        val decoded = PartPaths.decode(path)
        entriesByName[decoded]?.let { return it }
        return entriesByName.entries.firstOrNull { it.key.equals(decoded, ignoreCase = true) }?.value
    }

    @Synchronized
    private fun accountTotal(count: Long) {
        totalBytesRead += count
        if (totalBytesRead > limits.maxArchiveTotalBytes) throw ConversionException.TooLarge(limits.maxArchiveTotalBytes)
    }

    override fun close() {
        zip.close()
    }
}
