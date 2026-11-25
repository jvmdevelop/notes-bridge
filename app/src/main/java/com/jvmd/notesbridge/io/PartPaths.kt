package com.jvmd.notesbridge.io

import java.net.URLDecoder

object PartPaths {
    fun directoryOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

    fun resolve(
        baseDirectory: String,
        target: String,
    ): String {
        val cleanTarget = target.substringBefore('#').substringBefore('?')
        val combined =
            if (cleanTarget.startsWith("/")) {
                cleanTarget.trimStart('/')
            } else if (baseDirectory.isEmpty()) {
                cleanTarget
            } else {
                "$baseDirectory/$cleanTarget"
            }
        val segments = ArrayDeque<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }

    fun decode(path: String): String = runCatching { URLDecoder.decode(path.replace("+", "%2B"), Charsets.UTF_8.name()) }.getOrDefault(path)
}
