package com.jvmd.notesbridge.convert

data class ConversionLimits(
    val maxInputBytes: Long = 100L * 1024 * 1024,
    val maxTextBytes: Long = 20L * 1024 * 1024,
    val maxArchiveEntries: Int = 20_000,
    val maxEntryBytes: Long = 150L * 1024 * 1024,
    val maxArchiveTotalBytes: Long = 600L * 1024 * 1024,
    val maxCompressionRatio: Long = 200,
    val maxXmlElements: Int = 600_000,
    val maxPages: Int = 2_000,
    val maxTableCells: Int = 100_000,
    val maxTableColumns: Int = 100,
    val maxSharedFiles: Int = 50,
    val maxStandaloneImagePx: Int = 2_400,
    val maxEmbeddedImagePx: Int = 1_600,
)
