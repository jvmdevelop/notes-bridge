package com.jvmd.notesbridge.share

import android.content.Context
import android.text.format.Formatter
import com.jvmd.notesbridge.BuildConfig
import com.jvmd.notesbridge.R
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.detect.UnsupportedReason

object ErrorMessages {
    fun describe(
        context: Context,
        error: Throwable,
    ): String =
        when (error) {
            is ConversionException.Unsupported -> {
                when (error.reason) {
                    UnsupportedReason.LEGACY_OFFICE -> context.getString(R.string.error_legacy_office, error.fileName)
                    UnsupportedReason.PASSWORD_PROTECTED -> context.getString(R.string.error_password_protected, error.fileName)
                    UnsupportedReason.ALREADY_PDF -> context.getString(R.string.error_already_pdf, error.fileName)
                    UnsupportedReason.TIFF -> context.getString(R.string.error_tiff, error.fileName)
                    UnsupportedReason.CONTENT_MISMATCH -> context.getString(R.string.error_content_mismatch, error.fileName)
                    UnsupportedReason.CORRUPTED -> context.getString(R.string.error_corrupted_named, error.fileName)
                    UnsupportedReason.UNKNOWN -> context.getString(R.string.error_unknown_format, error.fileName)
                }
            }

            is ConversionException.TooLarge -> {
                context.getString(R.string.error_too_large, Formatter.formatShortFileSize(context, error.limitBytes))
            }

            is ConversionException.TooComplex -> {
                context.getString(R.string.error_too_complex)
            }

            is ConversionException.Corrupted -> {
                context.getString(R.string.error_corrupted)
            }

            is ConversionException.DrmProtected -> {
                context.getString(R.string.error_drm)
            }

            is ConversionException.Empty -> {
                context.getString(R.string.error_empty)
            }

            is ConversionException.ReadFailed -> {
                context.getString(R.string.error_read_failed)
            }

            is ConversionException.NoInput -> {
                context.getString(R.string.error_no_input)
            }

            is ConversionException.TooManyFiles -> {
                context.resources.getQuantityString(R.plurals.error_too_many_files, error.limit, error.limit)
            }

            else -> {
                context.getString(R.string.error_unexpected)
            }
        }

    fun detail(error: Throwable): String {
        val code = (error as? ConversionException)?.code ?: "unexpected"
        return "NotesBridge ${BuildConfig.VERSION_NAME} · $code: ${error.message.orEmpty()}"
    }

    fun warning(
        context: Context,
        warning: ConversionWarning,
    ): String =
        context.getString(
            when (warning) {
                ConversionWarning.EMBEDDED_OBJECTS_SKIPPED -> R.string.warning_embedded_objects
                ConversionWarning.EXTERNAL_RESOURCES_SKIPPED -> R.string.warning_external_resources
                ConversionWarning.IMAGES_SKIPPED -> R.string.warning_images
                ConversionWarning.CONTENT_TRUNCATED -> R.string.warning_truncated
                ConversionWarning.HIDDEN_CONTENT_SKIPPED -> R.string.warning_hidden
            },
        )
}
