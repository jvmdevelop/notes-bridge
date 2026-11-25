package com.jvmd.notesbridge.io

import java.io.InputStream

fun InputStream.readUpTo(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val count = read(buffer, total, limit - total)
        if (count < 0) break
        total += count
    }
    return if (total == limit) buffer else buffer.copyOf(total)
}
