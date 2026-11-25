package com.jvmd.notesbridge.convert

import com.jvmd.notesbridge.detect.UnsupportedReason

sealed class ConversionException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Unsupported(
        val fileName: String,
        val reason: UnsupportedReason,
    ) : ConversionException("unsupported/${reason.name.lowercase()}", "Unsupported document '$fileName': $reason")

    class TooLarge(
        val limitBytes: Long,
    ) : ConversionException("too_large", "Document exceeds the size limit of $limitBytes bytes")

    class TooComplex(
        detail: String,
    ) : ConversionException("too_complex", "Document is too complex: $detail")

    class Corrupted(
        detail: String,
        cause: Throwable? = null,
    ) : ConversionException("corrupted", "Document is damaged or malformed: $detail", cause)

    class DrmProtected : ConversionException("drm", "Document is protected by DRM")

    class Empty : ConversionException("empty", "Document has no content to convert")

    class ReadFailed(
        cause: Throwable,
    ) : ConversionException("read_failed", "Document could not be read: ${cause.message}", cause)

    class NoInput : ConversionException("no_input", "No document was received")

    class TooManyFiles(
        val limit: Int,
    ) : ConversionException("too_many_files", "More than $limit documents were shared at once")
}
