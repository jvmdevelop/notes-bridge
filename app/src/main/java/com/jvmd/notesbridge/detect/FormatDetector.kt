package com.jvmd.notesbridge.detect

import com.jvmd.notesbridge.io.readUpTo
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

class FormatDetector {
    fun detect(
        file: File,
        fileName: String?,
        declaredMimeType: String?,
    ): DetectionResult {
        val header = readHeader(file)
        val hint = DocumentFormat.byExtension(extensionOf(fileName)) ?: DocumentFormat.byMimeType(declaredMimeType)
        signatureFormat(header)?.let { return it }
        return when {
            header.startsWith(ZIP_SIGNATURE) -> {
                detectZip(file, hint)
            }

            header.startsWith(OLE2_SIGNATURE) -> {
                unsupported(
                    if (hint == DocumentFormat.DOCX || hint == DocumentFormat.XLSX ||
                        hint == DocumentFormat.PPTX
                    ) {
                        UnsupportedReason.PASSWORD_PROTECTED
                    } else {
                        UnsupportedReason.LEGACY_OFFICE
                    },
                )
            }

            header.isEmpty() -> {
                unsupported(UnsupportedReason.CORRUPTED)
            }

            looksBinary(header) -> {
                unsupported(if (hint != null) UnsupportedReason.CONTENT_MISMATCH else UnsupportedReason.UNKNOWN)
            }

            else -> {
                detectText(header, hint, declaredMimeType)
            }
        }
    }

    private fun signatureFormat(header: ByteArray): DetectionResult? =
        when {
            header.startsWith(PNG_SIGNATURE) -> DetectionResult.Supported(DocumentFormat.PNG)

            header.startsWith(JPEG_SIGNATURE) -> DetectionResult.Supported(DocumentFormat.JPEG)

            header.startsWith(GIF87_SIGNATURE) || header.startsWith(GIF89_SIGNATURE) -> DetectionResult.Supported(DocumentFormat.GIF)

            header.startsWith(
                RIFF_SIGNATURE,
            ) && header.startsWith(WEBP_SIGNATURE, offset = 8) -> DetectionResult.Supported(DocumentFormat.WEBP)

            isBmp(header) -> DetectionResult.Supported(DocumentFormat.BMP)

            header.startsWith(PDF_SIGNATURE) -> unsupported(UnsupportedReason.ALREADY_PDF)

            header.startsWith(TIFF_LE_SIGNATURE) || header.startsWith(TIFF_BE_SIGNATURE) -> unsupported(UnsupportedReason.TIFF)

            header.startsWith(RTF_SIGNATURE) -> DetectionResult.Supported(DocumentFormat.RTF)

            header.startsWith(FTYP_SIGNATURE, offset = 4) -> isoMediaFormat(header)

            else -> null
        }

    private fun isBmp(header: ByteArray): Boolean {
        if (!header.startsWith(BMP_SIGNATURE) || header.size < 18) return false
        val dibHeaderSize = (header[14].toInt() and 0xFF) or ((header[15].toInt() and 0xFF) shl 8)
        return dibHeaderSize in BMP_DIB_HEADER_SIZES && header[16].toInt() == 0 && header[17].toInt() == 0
    }

    private fun isoMediaFormat(header: ByteArray): DetectionResult? {
        val brands = String(header, 8, minOf(header.size - 8, 32).coerceAtLeast(0), Charsets.ISO_8859_1)
        return when {
            brands.contains("avif") || brands.contains("avis") -> DetectionResult.Supported(DocumentFormat.AVIF)
            HEIF_BRANDS.any { brands.contains(it) } -> DetectionResult.Supported(DocumentFormat.HEIF)
            else -> null
        }
    }

    private fun detectZip(
        file: File,
        hint: DocumentFormat?,
    ): DetectionResult =
        try {
            ZipFile(file).use { zip ->
                val mimetype =
                    zip.getEntry("mimetype")?.let { entry ->
                        zip.getInputStream(entry).use { String(it.readUpTo(256), Charsets.US_ASCII).trim() }
                    }
                val byMimetype =
                    when {
                        mimetype == null -> null
                        mimetype == "application/epub+zip" -> DocumentFormat.EPUB
                        mimetype.startsWith("application/vnd.oasis.opendocument.text") -> DocumentFormat.ODT
                        mimetype.startsWith("application/vnd.oasis.opendocument.spreadsheet") -> DocumentFormat.ODS
                        mimetype.startsWith("application/vnd.oasis.opendocument.presentation") -> DocumentFormat.ODP
                        else -> null
                    }
                if (byMimetype != null) return DetectionResult.Supported(byMimetype)
                val contentTypes =
                    zip.getEntry("[Content_Types].xml")?.let { entry ->
                        zip.getInputStream(entry).use { String(it.readUpTo(MAX_CONTENT_TYPES_BYTES), Charsets.UTF_8) }
                    }
                val byContentTypes =
                    when {
                        contentTypes == null -> null
                        OOXML_WORD_MARKERS.any { contentTypes.contains(it, ignoreCase = true) } -> DocumentFormat.DOCX
                        OOXML_SHEET_MARKERS.any { contentTypes.contains(it, ignoreCase = true) } -> DocumentFormat.XLSX
                        OOXML_SLIDE_MARKERS.any { contentTypes.contains(it, ignoreCase = true) } -> DocumentFormat.PPTX
                        else -> null
                    }
                if (byContentTypes != null) return DetectionResult.Supported(byContentTypes)
                unsupported(if (hint?.isZipContainer == true) UnsupportedReason.CORRUPTED else UnsupportedReason.UNKNOWN)
            }
        } catch (_: ZipException) {
            unsupported(UnsupportedReason.CORRUPTED)
        } catch (_: IOException) {
            unsupported(UnsupportedReason.CORRUPTED)
        } catch (_: IllegalArgumentException) {
            unsupported(UnsupportedReason.CORRUPTED)
        }

    private fun detectText(
        header: ByteArray,
        hint: DocumentFormat?,
        declaredMimeType: String?,
    ): DetectionResult {
        val prefix = decodePrefix(header).trimStart().lowercase()
        val isSvg = prefix.startsWith("<") && prefix.contains("<svg")
        val isHtml =
            prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || (prefix.startsWith("<?xml") && prefix.contains("<html"))
        return when {
            isSvg && !isHtml -> {
                DetectionResult.Supported(DocumentFormat.SVG)
            }

            hint == DocumentFormat.SVG -> {
                unsupported(UnsupportedReason.CONTENT_MISMATCH)
            }

            hint == DocumentFormat.RTF -> {
                unsupported(UnsupportedReason.CONTENT_MISMATCH)
            }

            hint != null && hint.isTextBased -> {
                DetectionResult.Supported(hint)
            }

            isHtml -> {
                DetectionResult.Supported(DocumentFormat.HTML)
            }

            hint != null -> {
                unsupported(UnsupportedReason.CONTENT_MISMATCH)
            }

            declaredMimeType == null || declaredMimeType.startsWith("text/") || declaredMimeType == "application/octet-stream" -> {
                DetectionResult.Supported(DocumentFormat.PLAIN_TEXT)
            }

            else -> {
                unsupported(UnsupportedReason.UNKNOWN)
            }
        }
    }

    private fun decodePrefix(header: ByteArray): String {
        val withoutBom = if (header.startsWith(UTF8_BOM)) header.copyOfRange(UTF8_BOM.size, header.size) else header
        return String(withoutBom, Charsets.UTF_8)
    }

    private fun looksBinary(header: ByteArray): Boolean {
        if (header.startsWith(UTF16LE_BOM) || header.startsWith(UTF16BE_BOM)) return false
        val sample = header.take(1024)
        val zeros = sample.count { it.toInt() == 0 }
        if (zeros > 0) {
            val oddZeros = sample.filterIndexed { index, byte -> index % 2 == 1 && byte.toInt() == 0 }.size
            val evenZeros = zeros - oddZeros
            val looksUtf16 = (oddZeros > sample.size * 0.3 && evenZeros == 0) || (evenZeros > sample.size * 0.3 && oddZeros == 0)
            return !looksUtf16
        }
        val controls =
            sample.count {
                val value = it.toInt() and 0xFF
                value < 0x09 || (value in 0x0E..0x1F && value != 0x1B)
            }
        return controls > sample.size / 20
    }

    private fun readHeader(file: File): ByteArray = file.inputStream().use { it.readUpTo(HEADER_BYTES) }

    private fun extensionOf(fileName: String?): String? =
        fileName
            ?.substringAfterLast('/')
            ?.takeIf { it.contains('.') }
            ?.substringAfterLast('.')

    private fun unsupported(reason: UnsupportedReason) = DetectionResult.Unsupported(reason)

    private fun ByteArray.startsWith(
        prefix: ByteArray,
        offset: Int = 0,
    ): Boolean = size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }

    private companion object {
        const val HEADER_BYTES = 4096
        const val MAX_CONTENT_TYPES_BYTES = 1024 * 1024

        val ZIP_SIGNATURE = bytes(0x50, 0x4B, 0x03, 0x04)
        val OLE2_SIGNATURE = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
        val PNG_SIGNATURE = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val JPEG_SIGNATURE = bytes(0xFF, 0xD8, 0xFF)
        val GIF87_SIGNATURE = "GIF87a".toByteArray(Charsets.US_ASCII)
        val GIF89_SIGNATURE = "GIF89a".toByteArray(Charsets.US_ASCII)
        val RIFF_SIGNATURE = "RIFF".toByteArray(Charsets.US_ASCII)
        val WEBP_SIGNATURE = "WEBP".toByteArray(Charsets.US_ASCII)
        val BMP_SIGNATURE = "BM".toByteArray(Charsets.US_ASCII)
        val PDF_SIGNATURE = "%PDF".toByteArray(Charsets.US_ASCII)
        val TIFF_LE_SIGNATURE = bytes(0x49, 0x49, 0x2A, 0x00)
        val TIFF_BE_SIGNATURE = bytes(0x4D, 0x4D, 0x00, 0x2A)
        val RTF_SIGNATURE = "{\\rtf".toByteArray(Charsets.US_ASCII)
        val FTYP_SIGNATURE = "ftyp".toByteArray(Charsets.US_ASCII)
        val UTF8_BOM = bytes(0xEF, 0xBB, 0xBF)
        val UTF16LE_BOM = bytes(0xFF, 0xFE)
        val UTF16BE_BOM = bytes(0xFE, 0xFF)
        val BMP_DIB_HEADER_SIZES = setOf(12, 40, 52, 56, 64, 108, 124)
        val HEIF_BRANDS = listOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
        val OOXML_WORD_MARKERS =
            listOf(
                "wordprocessingml.document.main",
                "wordprocessingml.template.main",
                "ms-word.document.macroEnabled.main",
                "ms-word.template.macroEnabledTemplate.main",
            )
        val OOXML_SHEET_MARKERS =
            listOf(
                "spreadsheetml.sheet.main",
                "spreadsheetml.template.main",
                "ms-excel.sheet.macroEnabled.main",
                "ms-excel.template.macroEnabled.main",
            )
        val OOXML_SLIDE_MARKERS =
            listOf(
                "presentationml.presentation.main",
                "presentationml.slideshow.main",
                "presentationml.template.main",
                "ms-powerpoint.presentation.macroEnabled.main",
                "ms-powerpoint.slideshow.macroEnabled.main",
            )

        fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
    }
}
