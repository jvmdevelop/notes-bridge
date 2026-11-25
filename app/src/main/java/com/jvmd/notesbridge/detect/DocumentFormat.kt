package com.jvmd.notesbridge.detect

enum class DocumentFormat(
    val displayName: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
) {
    PNG("PNG", setOf("png"), setOf("image/png")),
    JPEG("JPEG", setOf("jpg", "jpeg", "jpe", "jfif"), setOf("image/jpeg", "image/pjpeg", "image/jpg")),
    WEBP("WebP", setOf("webp"), setOf("image/webp")),
    GIF("GIF", setOf("gif"), setOf("image/gif")),
    BMP("BMP", setOf("bmp", "dib"), setOf("image/bmp", "image/x-ms-bmp", "image/x-bmp")),
    HEIF("HEIF", setOf("heic", "heif"), setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence")),
    AVIF("AVIF", setOf("avif"), setOf("image/avif")),
    SVG("SVG", setOf("svg"), setOf("image/svg+xml")),
    PLAIN_TEXT("Plain text", setOf("txt", "text", "log"), setOf("text/plain")),
    CSV(
        "CSV / TSV",
        setOf("csv", "tsv", "tab"),
        setOf("text/csv", "text/comma-separated-values", "text/tab-separated-values", "application/csv", "text/x-csv"),
    ),
    MARKDOWN("Markdown", setOf("md", "markdown", "mdown", "mkd"), setOf("text/markdown", "text/x-markdown")),
    HTML("HTML", setOf("html", "htm", "xhtml"), setOf("text/html", "application/xhtml+xml")),
    RTF("RTF", setOf("rtf"), setOf("application/rtf", "text/rtf", "application/x-rtf", "text/richtext")),
    DOCX(
        "Word (DOCX)",
        setOf("docx", "docm", "dotx"),
        setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "application/vnd.ms-word.document.macroenabled.12",
        ),
    ),
    XLSX(
        "Excel (XLSX)",
        setOf("xlsx", "xlsm", "xltx"),
        setOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "application/vnd.ms-excel.sheet.macroenabled.12",
        ),
    ),
    PPTX(
        "PowerPoint (PPTX)",
        setOf("pptx", "pptm", "ppsx", "potx"),
        setOf(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "application/vnd.ms-powerpoint.presentation.macroenabled.12",
        ),
    ),
    ODT("OpenDocument Text (ODT)", setOf("odt", "ott"), setOf("application/vnd.oasis.opendocument.text")),
    ODS("OpenDocument Spreadsheet (ODS)", setOf("ods", "ots"), setOf("application/vnd.oasis.opendocument.spreadsheet")),
    ODP("OpenDocument Presentation (ODP)", setOf("odp", "otp"), setOf("application/vnd.oasis.opendocument.presentation")),
    EPUB("EPUB", setOf("epub"), setOf("application/epub+zip")),
    ;

    val isZipContainer: Boolean get() = this in ZIP_CONTAINERS

    val isTextBased: Boolean get() = this in TEXT_BASED

    companion object {
        private val ZIP_CONTAINERS = setOf(DOCX, XLSX, PPTX, ODT, ODS, ODP, EPUB)
        private val TEXT_BASED = setOf(SVG, PLAIN_TEXT, CSV, MARKDOWN, HTML, RTF)

        fun byExtension(extension: String?): DocumentFormat? {
            val normalized = extension?.lowercase() ?: return null
            return entries.firstOrNull { normalized in it.extensions }
        }

        fun byMimeType(mimeType: String?): DocumentFormat? {
            val normalized = mimeType?.lowercase()?.substringBefore(';')?.trim() ?: return null
            return entries.firstOrNull { normalized in it.mimeTypes }
        }
    }
}

enum class UnsupportedReason {
    LEGACY_OFFICE,
    PASSWORD_PROTECTED,
    ALREADY_PDF,
    TIFF,
    CONTENT_MISMATCH,
    CORRUPTED,
    UNKNOWN,
}

sealed interface DetectionResult {
    data class Supported(
        val format: DocumentFormat,
    ) : DetectionResult

    data class Unsupported(
        val reason: UnsupportedReason,
    ) : DetectionResult
}
