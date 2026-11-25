package com.jvmd.notesbridge.convert

import com.jvmd.notesbridge.xml.XmlTreeReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.util.Collections
import java.util.EnumSet

class ConversionContext(
    val limits: ConversionLimits,
    val xml: XmlTreeReader,
    private val job: Job? = null,
) {
    private val collectedWarnings: MutableSet<ConversionWarning> =
        Collections.synchronizedSet(EnumSet.noneOf(ConversionWarning::class.java))

    val warnings: Set<ConversionWarning> get() = synchronized(collectedWarnings) { collectedWarnings.toSet() }

    fun warn(warning: ConversionWarning) {
        collectedWarnings.add(warning)
    }

    fun checkCancelled() {
        job?.ensureActive()
    }
}
