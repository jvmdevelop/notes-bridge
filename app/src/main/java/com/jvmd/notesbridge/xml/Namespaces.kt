package com.jvmd.notesbridge.xml

object Namespaces {
    const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    const val S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
    const val PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture"
    const val MATH = "http://schemas.openxmlformats.org/officeDocument/2006/math"
    const val PACKAGE_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val MARKUP_COMPATIBILITY = "http://schemas.openxmlformats.org/markup-compatibility/2006"
    const val VML = "urn:schemas-microsoft-com:vml"
    const val WPS = "http://schemas.microsoft.com/office/word/2010/wordprocessingShape"

    const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
    const val XLINK = "http://www.w3.org/1999/xlink"
    const val PRESENTATION = "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"

    const val OPF = "http://www.idpf.org/2007/opf"
    const val OCF_CONTAINER = "urn:oasis:names:tc:opendocument:xmlns:container"
    const val DUBLIN_CORE = "http://purl.org/dc/elements/1.1/"
    const val XML_ENCRYPTION = "http://www.w3.org/2001/04/xmlenc#"

    private val STRICT_TO_TRANSITIONAL =
        mapOf(
            "http://purl.oclc.org/ooxml/wordprocessingml/main" to W,
            "http://purl.oclc.org/ooxml/officeDocument/relationships" to R,
            "http://purl.oclc.org/ooxml/drawingml/main" to A,
            "http://purl.oclc.org/ooxml/presentationml/main" to P,
            "http://purl.oclc.org/ooxml/spreadsheetml/main" to S,
            "http://purl.oclc.org/ooxml/drawingml/wordprocessingDrawing" to WP,
            "http://purl.oclc.org/ooxml/drawingml/picture" to PIC,
            "http://purl.oclc.org/ooxml/officeDocument/math" to MATH,
        )

    fun normalize(namespace: String): String = STRICT_TO_TRANSITIONAL[namespace] ?: namespace
}
