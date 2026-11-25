package com.jvmd.notesbridge.io

import com.jvmd.notesbridge.convert.ConversionException
import java.io.FilterInputStream
import java.io.InputStream

class LimitedInputStream(
    input: InputStream,
    private val limitBytes: Long,
    private val onRead: (Long) -> Unit = {},
) : FilterInputStream(input) {
    private var consumed = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) account(count.toLong())
        return count
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) account(skipped)
        return skipped
    }

    private fun account(count: Long) {
        consumed += count
        if (consumed > limitBytes) throw ConversionException.TooLarge(limitBytes)
        onRead(count)
    }
}
