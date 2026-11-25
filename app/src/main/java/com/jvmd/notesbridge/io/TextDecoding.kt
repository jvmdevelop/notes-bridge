package com.jvmd.notesbridge.io

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TextDecoding {
    private val WINDOWS_1251: Charset = Charset.forName("windows-1251")
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    fun decode(bytes: ByteArray): String {
        detectBom(bytes)?.let { (charset, length) -> return String(bytes, length, bytes.size - length, charset) }
        detectUtf16WithoutBom(bytes)?.let { return String(bytes, it) }
        decodeStrictUtf8(bytes)?.let { return it }
        return String(bytes, guessSingleByteCharset(bytes))
    }

    private fun detectBom(bytes: ByteArray): Pair<Charset, Int>? =
        when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> null
        }

    private fun detectUtf16WithoutBom(bytes: ByteArray): Charset? {
        val sample = minOf(bytes.size, 4096) and 0x7FFFFFFE
        if (sample < 4) return null
        var evenZeros = 0
        var oddZeros = 0
        for (index in 0 until sample) {
            if (bytes[index].toInt() == 0) {
                if (index % 2 == 0) evenZeros++ else oddZeros++
            }
        }
        val half = sample / 2
        return when {
            oddZeros > half * 0.4 && evenZeros < half * 0.05 -> Charsets.UTF_16LE
            evenZeros > half * 0.4 && oddZeros < half * 0.05 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun guessSingleByteCharset(bytes: ByteArray): Charset {
        var cyrillicRange = 0
        var asciiLetters = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            when {
                value >= 0xC0 -> cyrillicRange++
                value in 'A'.code..'Z'.code || value in 'a'.code..'z'.code -> asciiLetters++
            }
        }
        return if (cyrillicRange > asciiLetters) WINDOWS_1251 else WINDOWS_1252
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }
}
