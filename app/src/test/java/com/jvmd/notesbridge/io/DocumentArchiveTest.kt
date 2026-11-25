package com.jvmd.notesbridge.io

import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentArchiveTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun zip(entries: Map<String, ByteArray>): File {
        val file = temporary.newFile()
        ZipOutputStream(file.outputStream()).use { output ->
            for ((name, bytes) in entries) {
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }

    @Test
    fun readsEntriesIncludingEncodedNames() {
        val file = zip(mapOf("Pictures/my image.png" to byteArrayOf(1, 2, 3)))
        DocumentArchive(file, ConversionLimits()).use { archive ->
            assertArrayEquals(byteArrayOf(1, 2, 3), archive.readBytes("Pictures/my%20image.png"))
            assertNull(archive.readBytes("missing.xml"))
        }
    }

    @Test
    fun rejectsHighlyCompressedEntries() {
        val file = zip(mapOf("bomb.xml" to ByteArray(20 * 1024 * 1024)))
        DocumentArchive(file, ConversionLimits()).use { archive ->
            assertThrows(ConversionException.TooComplex::class.java) { archive.readBytes("bomb.xml") }
        }
    }

    @Test
    fun enforcesEntrySizeLimit() {
        val file = zip(mapOf("big.bin" to ByteArray(4096) { it.toByte() }))
        DocumentArchive(file, ConversionLimits(maxEntryBytes = 1024)).use { archive ->
            assertThrows(ConversionException.TooLarge::class.java) { archive.readBytes("big.bin") }
        }
    }

    @Test
    fun rejectsArchivesWithTooManyEntries() {
        val file = zip((1..20).associate { "f$it.txt" to byteArrayOf(1) })
        assertThrows(ConversionException.TooComplex::class.java) { DocumentArchive(file, ConversionLimits(maxArchiveEntries = 10)) }
    }

    @Test
    fun rejectsNonZipInput() {
        val file = temporary.newFile().apply { writeText("plain") }
        assertThrows(ConversionException.Corrupted::class.java) { DocumentArchive(file, ConversionLimits()) }
    }
}
