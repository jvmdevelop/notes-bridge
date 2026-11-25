package com.jvmd.notesbridge.formats.rtf

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.common.Units
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.Baseline
import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.model.FlowBlock
import com.jvmd.notesbridge.model.FlowDocument
import com.jvmd.notesbridge.model.FontFamily
import com.jvmd.notesbridge.model.ImageBlock
import com.jvmd.notesbridge.model.ImageSource
import com.jvmd.notesbridge.model.PageBreak
import com.jvmd.notesbridge.model.PageSetup
import com.jvmd.notesbridge.model.Paragraph
import com.jvmd.notesbridge.model.Table
import com.jvmd.notesbridge.model.TableCell
import com.jvmd.notesbridge.model.TableRow
import com.jvmd.notesbridge.model.TextRun
import com.jvmd.notesbridge.model.TextStyle
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class RtfReader : DocumentReader<FlowDocument> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): FlowDocument = RtfParser(input.readTextBytes(), context).parse()
}

class RtfParser(
    private val data: ByteArray,
    private val context: ConversionContext?,
) {
    private enum class Destination { NORMAL, SKIP, FONT_TABLE, COLOR_TABLE, PICTURE, LIST_TEXT }

    private data class CharState(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val halfPoints: Int = 24,
        val colorIndex: Int = 0,
        val baseline: Baseline = Baseline.NORMAL,
        val hidden: Boolean = false,
        val fontIndex: Int = -1,
    )

    private data class ParagraphState(
        val alignment: Alignment = Alignment.START,
        val leftIndentTwips: Int = 0,
        val inTable: Boolean = false,
        val pageBreakBefore: Boolean = false,
    )

    private data class GroupState(
        val destination: Destination,
        val chars: CharState,
        val paragraph: ParagraphState,
        val unicodeSkip: Int,
    )

    private class FontInfo(
        var charset: Int = 0,
        var name: String = "",
    )

    private class PictureState {
        val hex = StringBuilder()
        var binary: ByteArray? = null
        var format: String? = null
        var widthGoalTwips = 0
        var heightGoalTwips = 0
        var scaleX = 100
        var scaleY = 100
    }

    private var position = 0
    private var destination = Destination.NORMAL
    private var chars = CharState()
    private var paragraph = ParagraphState()
    private var unicodeSkip = 1
    private var pendingSkip = 0
    private var ignorableDestination = false
    private val listText = StringBuilder()
    private var pendingListMarker: String? = null
    private var defaultCodepage: Charset = Charset.forName("windows-1252")
    private var defaultFont = 0
    private val stack = ArrayDeque<GroupState>()
    private val fonts = HashMap<Int, FontInfo>()
    private var currentFontEntry: FontInfo? = null
    private var currentFontEntryIndex = -1
    private val colors = ArrayList<Int?>()
    private var colorRed = 0
    private var colorGreen = 0
    private var colorBlue = 0
    private var colorDefined = false
    private var picture: PictureState? = null
    private val pendingBytes = ByteArrayOutputStream()

    private val blocks = ArrayList<FlowBlock>()
    private val runs = ArrayList<TextRun>()
    private val text = StringBuilder()
    private var textStyle: TextStyle? = null
    private val cellParagraphs = ArrayList<Paragraph>()
    private val rowCells = ArrayList<TableCell>()
    private val tableRows = ArrayList<TableRow>()
    private var paperWidthTwips = 0
    private var paperHeightTwips = 0
    private var groupDepth = 0
    private var steps = 0

    fun parse(): FlowDocument {
        if (!startsWithRtf()) throw ConversionException.Corrupted("missing RTF header")
        while (position < data.size) {
            steps++
            if (steps % 20_000 == 0) context?.checkCancelled()
            val byte = data[position].toInt() and 0xFF
            when (byte) {
                '{'.code -> {
                    flushBytes()
                    ignorableDestination = false
                    position++
                    stack.addLast(GroupState(destination, chars, paragraph, unicodeSkip))
                    groupDepth++
                    if (groupDepth > MAX_DEPTH) throw ConversionException.TooComplex("RTF nesting too deep")
                    if (destination == Destination.PICTURE) destination = Destination.SKIP
                }

                '}'.code -> {
                    flushBytes()
                    position++
                    endGroup()
                }

                '\\'.code -> {
                    readControl()
                }

                '\r'.code, '\n'.code -> {
                    position++
                }

                else -> {
                    position++
                    plainByte(byte)
                }
            }
        }
        flushBytes()
        endParagraph()
        flushTable()
        val setup =
            if (paperWidthTwips > 0 && paperHeightTwips > 0) {
                PageSetup.A4_PORTRAIT.copy(widthPt = paperWidthTwips / 20f, heightPt = paperHeightTwips / 20f)
            } else {
                PageSetup.A4_PORTRAIT
            }
        val trimmed = blocks.dropLastWhile { it is Paragraph && it.runs.isEmpty() }
        return FlowDocument(trimmed, setup)
    }

    private fun startsWithRtf(): Boolean = data.size >= 5 && String(data, 0, 5, Charsets.US_ASCII) == "{\\rtf"

    private fun endGroup() {
        if (stack.size == 1 && destination == Destination.NORMAL) endParagraph(keepEmpty = false)
        if (destination == Destination.FONT_TABLE) finishFontEntry()
        if (destination == Destination.PICTURE) finishPicture()
        if (destination == Destination.LIST_TEXT) {
            flushBytes()
            pendingListMarker = listText.toString().trim().ifEmpty { null }
            listText.setLength(0)
        }
        val previous = stack.removeLastOrNull() ?: return
        groupDepth--
        destination = previous.destination
        chars = previous.chars
        paragraph = previous.paragraph
        unicodeSkip = previous.unicodeSkip
    }

    private fun plainByte(byte: Int) {
        when (destination) {
            Destination.NORMAL, Destination.LIST_TEXT -> {
                if (pendingSkip > 0) {
                    pendingSkip--
                    return
                }
                pendingBytes.write(byte)
            }

            Destination.FONT_TABLE -> {
                if (byte == ';'.code) {
                    finishFontEntry()
                } else {
                    currentFontEntry?.let { it.name += byte.toChar() }
                }
            }

            Destination.COLOR_TABLE -> {
                if (byte == ';'.code) {
                    colors.add(if (colorDefined) Colors.rgb(colorRed, colorGreen, colorBlue) else null)
                    colorRed = 0
                    colorGreen = 0
                    colorBlue = 0
                    colorDefined = false
                }
            }

            Destination.PICTURE -> {
                val char = byte.toChar()
                if (char.isLetterOrDigit()) picture?.hex?.append(char)
            }

            Destination.SKIP -> {
                Unit
            }
        }
    }

    private fun readControl() {
        position++
        if (position >= data.size) return
        val first = data[position].toInt() and 0xFF
        if (!first.toChar().isLetter()) {
            position++
            when (first.toChar()) {
                '\'' -> {
                    val hex = String(data, position, minOf(2, data.size - position), Charsets.US_ASCII)
                    position += hex.length
                    val value = hex.toIntOrNull(16) ?: return
                    if (destination == Destination.NORMAL || destination == Destination.LIST_TEXT) {
                        if (pendingSkip > 0) pendingSkip-- else pendingBytes.write(value)
                    } else if (destination == Destination.FONT_TABLE) {
                        currentFontEntry?.let { it.name += value.toChar() }
                    }
                }

                '\\', '{', '}' -> {
                    plainByte(first)
                }

                '~' -> {
                    emitText(" ")
                }

                '_' -> {
                    emitText("‑")
                }

                '-' -> {
                    Unit
                }

                '*' -> {
                    ignorableDestination = true
                }

                '\r', '\n' -> {
                    if (destination == Destination.NORMAL) endParagraph()
                }

                '\t' -> {
                    emitText("\t")
                }

                else -> {
                    Unit
                }
            }
            return
        }
        val start = position
        while (position < data.size && (data[position].toInt() and 0xFF).toChar().isLetter()) position++
        val word = String(data, start, position - start, Charsets.US_ASCII)
        var parameter: Int? = null
        if (position < data.size &&
            ((data[position].toInt() and 0xFF).toChar() == '-' || (data[position].toInt() and 0xFF).toChar().isDigit())
        ) {
            val numberStart = position
            position++
            while (position < data.size && (data[position].toInt() and 0xFF).toChar().isDigit()) position++
            parameter = String(data, numberStart, position - numberStart, Charsets.US_ASCII).toIntOrNull()
        }
        if (position < data.size && data[position].toInt() == ' '.code) position++
        if (word == "bin") {
            readBinary(parameter ?: 0)
            return
        }
        flushBytes()
        if (ignorableDestination) {
            ignorableDestination = false
            if (destination == Destination.FONT_TABLE || word !in KEPT_IGNORABLE_DESTINATIONS) {
                destination = Destination.SKIP
                return
            }
        }
        handleWord(word, parameter)
    }

    private fun readBinary(length: Int) {
        val safeLength = length.coerceIn(0, data.size - position)
        if (destination == Destination.PICTURE) picture?.binary = data.copyOfRange(position, position + safeLength)
        position += safeLength
    }

    private fun handleWord(
        word: String,
        parameter: Int?,
    ) {
        if (destination == Destination.FONT_TABLE) {
            when (word) {
                "f" -> {
                    finishFontEntry()
                    currentFontEntryIndex = parameter ?: 0
                    currentFontEntry = FontInfo()
                }

                "fcharset" -> {
                    currentFontEntry?.charset = parameter ?: 0
                }
            }
            return
        }
        if (destination == Destination.COLOR_TABLE) {
            when (word) {
                "red" -> colorRed = parameter ?: 0
                "green" -> colorGreen = parameter ?: 0
                "blue" -> colorBlue = parameter ?: 0
            }
            if (word == "red" || word == "green" || word == "blue") colorDefined = true
            return
        }
        if (destination == Destination.PICTURE) {
            val current = picture ?: return
            when (word) {
                "pngblip" -> current.format = "png"
                "jpegblip" -> current.format = "jpeg"
                "emfblip", "wmetafile", "macpict", "pmmetafile", "dibitmap", "wbitmap" -> current.format = current.format ?: "unsupported"
                "picwgoal" -> current.widthGoalTwips = parameter ?: 0
                "pichgoal" -> current.heightGoalTwips = parameter ?: 0
                "picscalex" -> current.scaleX = parameter ?: 100
                "picscaley" -> current.scaleY = parameter ?: 100
            }
            return
        }
        if (destination == Destination.SKIP) return
        when (word) {
            "ansicpg" -> {
                parameter?.let { codepage -> charsetFor(codepage)?.let { defaultCodepage = it } }
            }

            "deff" -> {
                defaultFont = parameter ?: 0
            }

            "fonttbl" -> {
                destination = Destination.FONT_TABLE
            }

            "colortbl" -> {
                destination = Destination.COLOR_TABLE
                colors.clear()
            }

            "listtext", "pntext" -> {
                destination = Destination.LIST_TEXT
                listText.setLength(0)
            }

            "pict" -> {
                destination = Destination.PICTURE
                picture = PictureState()
            }

            "paperw" -> {
                paperWidthTwips = parameter ?: 0
            }

            "paperh" -> {
                paperHeightTwips = parameter ?: 0
            }

            in SKIPPED_DESTINATIONS -> {
                destination = Destination.SKIP
            }

            "par" -> {
                endParagraph()
            }

            "sect" -> {
                endParagraph()
            }

            "line" -> {
                emitText("\n")
            }

            "tab" -> {
                emitText("\t")
            }

            "page" -> {
                endParagraph()
                flushTable()
                blocks.add(PageBreak)
            }

            "pard" -> {
                paragraph = ParagraphState()
            }

            "plain" -> {
                chars = CharState(fontIndex = defaultFont)
            }

            "b" -> {
                chars = chars.copy(bold = parameter != 0)
            }

            "i" -> {
                chars = chars.copy(italic = parameter != 0)
            }

            "ul", "uld", "uldash", "uldb", "ulth", "ulw", "ulwave" -> {
                chars = chars.copy(underline = parameter != 0)
            }

            "ulnone" -> {
                chars = chars.copy(underline = false)
            }

            "strike", "striked" -> {
                chars = chars.copy(strike = parameter != 0)
            }

            "fs" -> {
                chars = chars.copy(halfPoints = (parameter ?: 24).coerceIn(2, 3276))
            }

            "cf" -> {
                chars = chars.copy(colorIndex = parameter ?: 0)
            }

            "f" -> {
                chars = chars.copy(fontIndex = parameter ?: 0)
            }

            "super" -> {
                chars = chars.copy(baseline = Baseline.SUPERSCRIPT)
            }

            "sub" -> {
                chars = chars.copy(baseline = Baseline.SUBSCRIPT)
            }

            "nosupersub" -> {
                chars = chars.copy(baseline = Baseline.NORMAL)
            }

            "v" -> {
                chars = chars.copy(hidden = parameter != 0)
            }

            "qc" -> {
                paragraph = paragraph.copy(alignment = Alignment.CENTER)
            }

            "qr" -> {
                paragraph = paragraph.copy(alignment = Alignment.END)
            }

            "qj" -> {
                paragraph = paragraph.copy(alignment = Alignment.JUSTIFY)
            }

            "ql" -> {
                paragraph = paragraph.copy(alignment = Alignment.START)
            }

            "li" -> {
                paragraph = paragraph.copy(leftIndentTwips = parameter ?: 0)
            }

            "intbl" -> {
                paragraph = paragraph.copy(inTable = true)
            }

            "pagebb" -> {
                paragraph = paragraph.copy(pageBreakBefore = parameter != 0)
            }

            "cell", "nestcell" -> {
                endCell()
            }

            "row", "nestrow" -> {
                endRow()
            }

            "uc" -> {
                unicodeSkip = (parameter ?: 1).coerceAtLeast(0)
            }

            "u" -> {
                flushBytes()
                val code = parameter ?: return
                val value = if (code < 0) code + 65536 else code
                emitText(String(Character.toChars(value)))
                pendingSkip = unicodeSkip
            }

            "emdash" -> {
                emitText("—")
            }

            "endash" -> {
                emitText("–")
            }

            "bullet" -> {
                emitText("•")
            }

            "lquote" -> {
                emitText("‘")
            }

            "rquote" -> {
                emitText("’")
            }

            "ldblquote" -> {
                emitText("“")
            }

            "rdblquote" -> {
                emitText("”")
            }

            "emspace", "enspace", "qmspace" -> {
                emitText(" ")
            }
        }
    }

    private fun finishFontEntry() {
        val entry = currentFontEntry ?: return
        entry.name = entry.name.trim().trimEnd(';')
        fonts[currentFontEntryIndex] = entry
        currentFontEntry = null
    }

    private fun finishPicture() {
        val current = picture ?: return
        picture = null
        val bytes = current.binary ?: hexToBytes(current.hex)
        if ((current.format != "png" && current.format != "jpeg") || bytes.isEmpty()) {
            context?.warn(ConversionWarning.IMAGES_SKIPPED)
            return
        }
        val width = if (current.widthGoalTwips > 0) current.widthGoalTwips / 20f * current.scaleX / 100f else null
        val height = if (current.heightGoalTwips > 0) current.heightGoalTwips / 20f * current.scaleY / 100f else null
        endParagraph(keepEmpty = false)
        blocks.add(ImageBlock(ImageSource { bytes.inputStream() }, width, height))
    }

    private fun hexToBytes(hex: StringBuilder): ByteArray {
        val clean = hex.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        val output = ByteArray(clean.length / 2)
        for (index in output.indices) {
            output[index] = ((Character.digit(clean[index * 2], 16) shl 4) or Character.digit(clean[index * 2 + 1], 16)).toByte()
        }
        return output
    }

    private fun flushBytes() {
        if (pendingBytes.size() == 0) return
        val bytes = pendingBytes.toByteArray()
        pendingBytes.reset()
        val font = fonts[chars.fontIndex] ?: fonts[defaultFont]
        val decoded =
            if (font?.name?.contains("symbol", ignoreCase = true) == true) {
                bytes.joinToString("") { byte -> SYMBOL_MAP[byte.toInt() and 0xFF] ?: String(byteArrayOf(byte), defaultCodepage) }
            } else {
                val charset = font?.let { charsetForFontCharset(it.charset) } ?: defaultCodepage
                String(bytes, charset)
            }
        emitText(decoded)
    }

    private fun emitText(value: String) {
        if (destination == Destination.LIST_TEXT) {
            listText.append(value)
            return
        }
        if (destination != Destination.NORMAL || chars.hidden) return
        if (pendingBytes.size() > 0) flushBytes()
        val font = fonts[chars.fontIndex]
        val style =
            TextStyle(
                bold = chars.bold,
                italic = chars.italic,
                underline = chars.underline,
                strikethrough = chars.strike,
                fontSizePt = chars.halfPoints / 2f,
                color = colors.getOrNull(chars.colorIndex),
                baseline = chars.baseline,
                family = font?.let { Units.fontFamilyFor(it.name) } ?: FontFamily.SANS,
            )
        if (style != textStyle) {
            flushRun()
            textStyle = style
        }
        text.append(value)
    }

    private fun flushRun() {
        val style = textStyle
        if (text.isNotEmpty() && style != null) runs.add(TextRun(text.toString(), style))
        text.setLength(0)
    }

    private fun takeParagraph(): Paragraph {
        flushBytes()
        flushRun()
        val paragraphRuns = ArrayList(runs)
        runs.clear()
        val marker = pendingListMarker
        pendingListMarker = null
        return Paragraph(
            runs = paragraphRuns,
            alignment = paragraph.alignment,
            indentPt = (paragraph.leftIndentTwips / 20f).coerceAtLeast(if (marker != null) 18f else 0f),
            listMarker =
                marker?.let {
                    com.jvmd.notesbridge.formats.common.ListNumbering
                        .normalizeBulletGlyph(it)
                },
        )
    }

    private fun endParagraph(keepEmpty: Boolean = true) {
        val built = takeParagraph()
        if (paragraph.inTable) {
            cellParagraphs.add(built)
            return
        }
        flushTable()
        if (paragraph.pageBreakBefore && blocks.isNotEmpty() && blocks.last() !is PageBreak) blocks.add(PageBreak)
        if (built.runs.isNotEmpty() || keepEmpty) blocks.add(built)
    }

    private fun endCell() {
        val built = takeParagraph()
        if (built.runs.isNotEmpty() || cellParagraphs.isEmpty()) cellParagraphs.add(built)
        rowCells.add(TableCell(ArrayList(cellParagraphs)))
        cellParagraphs.clear()
    }

    private fun endRow() {
        if (runs.isNotEmpty() || text.isNotEmpty() || cellParagraphs.isNotEmpty()) endCell()
        if (rowCells.isNotEmpty()) tableRows.add(TableRow(ArrayList(rowCells)))
        rowCells.clear()
        paragraph = paragraph.copy(inTable = false)
    }

    private fun flushTable() {
        if (rowCells.isNotEmpty()) {
            tableRows.add(TableRow(ArrayList(rowCells)))
            rowCells.clear()
        }
        if (tableRows.isNotEmpty()) {
            blocks.add(Table(ArrayList(tableRows)))
            tableRows.clear()
        }
    }

    private fun charsetFor(codepage: Int): Charset? =
        when (codepage) {
            65001 -> Charsets.UTF_8
            932 -> runCatching { Charset.forName("Shift_JIS") }.getOrNull()
            936 -> runCatching { Charset.forName("GBK") }.getOrNull()
            949 -> runCatching { Charset.forName("EUC-KR") }.getOrNull()
            950 -> runCatching { Charset.forName("Big5") }.getOrNull()
            in 1250..1258, 874 -> runCatching { Charset.forName("windows-$codepage") }.getOrNull()
            437, 850, 852, 866 -> runCatching { Charset.forName("IBM$codepage") }.getOrNull()
            10000 -> runCatching { Charset.forName("x-MacRoman") }.getOrNull()
            else -> null
        }

    private fun charsetForFontCharset(charset: Int): Charset? =
        when (charset) {
            0, 1 -> null
            2 -> null
            77 -> charsetFor(10000)
            128 -> charsetFor(932)
            129 -> charsetFor(949)
            134 -> charsetFor(936)
            136 -> charsetFor(950)
            161 -> charsetFor(1253)
            162 -> charsetFor(1254)
            163 -> charsetFor(1258)
            177 -> charsetFor(1255)
            178 -> charsetFor(1256)
            186 -> charsetFor(1257)
            204 -> charsetFor(1251)
            222 -> charsetFor(874)
            238 -> charsetFor(1250)
            else -> null
        }

    private companion object {
        const val MAX_DEPTH = 1_000
        val KEPT_IGNORABLE_DESTINATIONS = setOf("shppict")
        val SYMBOL_MAP = mapOf(0xB7 to "•", 0xA7 to "▪", 0xD8 to "➢", 0xFC to "✓", 0x6F to "◦")
        val SKIPPED_DESTINATIONS =
            setOf(
                "stylesheet",
                "info",
                "header",
                "headerl",
                "headerr",
                "headerf",
                "footer",
                "footerl",
                "footerr",
                "footerf",
                "footnote",
                "annotation",
                "atnid",
                "atnauthor",
                "fldinst",
                "listtable",
                "listoverridetable",
                "rsidtbl",
                "generator",
                "xmlnstbl",
                "mmathPr",
                "themedata",
                "colorschememapping",
                "latentstyles",
                "datastore",
                "pgdsctbl",
                "revtbl",
                "filetbl",
                "bkmkstart",
                "bkmkend",
                "nonshppict",
                "objdata",
                "objclass",
                "objname",
                "datafield",
                "fontemb",
                "fontfile",
                "private",
                "userprops",
                "docvar",
                "xform",
                "wgrffmtfilter",
                "pnseclvl",
                "listpicture",
                "falt",
                "panose",
                "keycode",
                "template",
                "comment",
                "company",
                "operator",
                "title",
                "subject",
                "author",
                "keywords",
                "doccomm",
                "category",
                "manager",
                "hlinkbase",
                "passwordhash",
                "protusertbl",
                "sp",
                "sn",
                "sv",
            )
    }
}
