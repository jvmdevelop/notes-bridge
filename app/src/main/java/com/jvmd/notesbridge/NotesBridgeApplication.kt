package com.jvmd.notesbridge

import android.app.Application
import android.util.Xml
import com.jvmd.notesbridge.convert.ConversionLimits
import com.jvmd.notesbridge.convert.ConversionManager
import com.jvmd.notesbridge.convert.ConverterRegistry
import com.jvmd.notesbridge.convert.DefaultConverters
import com.jvmd.notesbridge.detect.FormatDetector
import com.jvmd.notesbridge.io.FileManager
import com.jvmd.notesbridge.launch.NotesLauncher
import com.jvmd.notesbridge.xml.XmlTreeReader

class NotesBridgeApplication : Application() {
    val limits: ConversionLimits by lazy { ConversionLimits() }
    val registry: ConverterRegistry by lazy { DefaultConverters.registry() }
    val fileManager: FileManager by lazy { FileManager(this) }
    val launcher: NotesLauncher by lazy { NotesLauncher(this) }
    val conversionManager: ConversionManager by lazy {
        ConversionManager(
            fileManager = fileManager,
            detector = FormatDetector(),
            registry = registry,
            limits = limits,
            xmlReader = XmlTreeReader({ Xml.newPullParser() }, limits.maxXmlElements),
        )
    }
}
