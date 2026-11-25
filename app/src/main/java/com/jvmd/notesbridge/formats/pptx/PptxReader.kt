package com.jvmd.notesbridge.formats.pptx

import com.jvmd.notesbridge.convert.ConversionContext
import com.jvmd.notesbridge.convert.ConversionException
import com.jvmd.notesbridge.convert.ConversionWarning
import com.jvmd.notesbridge.convert.DocumentReader
import com.jvmd.notesbridge.convert.ReaderInput
import com.jvmd.notesbridge.formats.common.ListNumbering
import com.jvmd.notesbridge.formats.common.NumberStyle
import com.jvmd.notesbridge.formats.common.OoxmlPackage
import com.jvmd.notesbridge.formats.common.Relationship
import com.jvmd.notesbridge.formats.common.Units
import com.jvmd.notesbridge.model.Alignment
import com.jvmd.notesbridge.model.Baseline
import com.jvmd.notesbridge.model.Colors
import com.jvmd.notesbridge.model.Crop
import com.jvmd.notesbridge.model.Frame
import com.jvmd.notesbridge.model.ImageSource
import com.jvmd.notesbridge.model.Insets
import com.jvmd.notesbridge.model.PathCommand
import com.jvmd.notesbridge.model.PictureElement
import com.jvmd.notesbridge.model.ShapeElement
import com.jvmd.notesbridge.model.ShapeOutline
import com.jvmd.notesbridge.model.Slide
import com.jvmd.notesbridge.model.SlideBackground
import com.jvmd.notesbridge.model.SlideDeck
import com.jvmd.notesbridge.model.SlideElement
import com.jvmd.notesbridge.model.SlideParagraph
import com.jvmd.notesbridge.model.SlideTableCell
import com.jvmd.notesbridge.model.SlideTableRow
import com.jvmd.notesbridge.model.TableElement
import com.jvmd.notesbridge.model.TextBox
import com.jvmd.notesbridge.model.TextRun
import com.jvmd.notesbridge.model.TextStyle
import com.jvmd.notesbridge.model.VerticalAnchor
import com.jvmd.notesbridge.xml.Namespaces.A
import com.jvmd.notesbridge.xml.Namespaces.MARKUP_COMPATIBILITY
import com.jvmd.notesbridge.xml.Namespaces.P
import com.jvmd.notesbridge.xml.Namespaces.R
import com.jvmd.notesbridge.xml.XmlElement

class PptxReader : DocumentReader<SlideDeck> {
    override fun read(
        input: ReaderInput,
        context: ConversionContext,
    ): SlideDeck {
        val pkg = OoxmlPackage(input.archive, context)
        val presentationPath = pkg.mainPart("officeDocument", "ppt/presentation.xml")
        val presentation = pkg.xml(presentationPath) ?: throw ConversionException.Corrupted("missing $presentationPath")
        val size = presentation.child(P, "sldSz")
        val widthPt = Units.emuToPt(size?.attr("cx"))?.takeIf { it > 0 } ?: 720f
        val heightPt = Units.emuToPt(size?.attr("cy"))?.takeIf { it > 0 } ?: 540f
        val relationships = pkg.relationships(presentationPath)
        val builder = DeckBuilder(pkg, context, presentation.child(P, "defaultTextStyle"))
        val slides = ArrayList<Slide>()
        for (slideId in presentation.child(P, "sldIdLst")?.children(P, "sldId").orEmpty()) {
            context.checkCancelled()
            val relationship = slideId.attr(R, "id")?.let { relationships[it] } ?: continue
            if (relationship.external) continue
            builder.slide(relationship.target)?.let { slides.add(it) }
        }
        return SlideDeck(widthPt, heightPt, slides)
    }

    private class Theme(
        val colors: Map<String, Int>,
        val majorFont: String?,
        val minorFont: String?,
    )

    private class Master(
        val path: String,
        val root: XmlElement,
        val theme: Theme,
        val colorMap: Map<String, String>,
        val placeholders: List<XmlElement>,
    ) {
        val textStyles: XmlElement? = root.child(P, "txStyles")
    }

    private class Layout(
        val path: String,
        val root: XmlElement,
        val master: Master,
        val placeholders: List<XmlElement>,
    )

    private class PartContext(
        val path: String,
        val relationships: Map<String, Relationship>,
        val colors: DrawingColors,
        val theme: Theme,
        val master: Master,
        val layout: Layout?,
        val resolvePlaceholders: Boolean,
    )

    private data class Transform(
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
    ) {
        fun then(inner: Transform): Transform =
            Transform(
                scaleX = scaleX * inner.scaleX,
                scaleY = scaleY * inner.scaleY,
                offsetX = scaleX * inner.offsetX + offsetX,
                offsetY = scaleY * inner.offsetY + offsetY,
            )
    }

    private class DeckBuilder(
        private val pkg: OoxmlPackage,
        private val context: ConversionContext,
        private val defaultTextStyle: XmlElement?,
    ) {
        private val masters = HashMap<String, Master>()
        private val layouts = HashMap<String, Layout>()

        private fun theme(masterPath: String): Theme {
            val themePath =
                pkg
                    .relationships(masterPath)
                    .values
                    .firstOrNull { it.hasType("theme") && !it.external }
                    ?.target
            val root = themePath?.let { pkg.xml(it) }
            val fonts = root?.path(A, "themeElements", "fontScheme")
            return Theme(
                colors = DrawingColors.schemeFrom(root),
                majorFont = fonts?.path(A, "majorFont", "latin")?.attr("typeface"),
                minorFont = fonts?.path(A, "minorFont", "latin")?.attr("typeface"),
            )
        }

        private fun master(path: String): Master? =
            masters[path] ?: pkg.xml(path)?.let { root ->
                Master(path, root, theme(path), DrawingColors.mapFrom(root.child(P, "clrMap")), placeholdersOf(root)).also {
                    masters[path] =
                        it
                }
            }

        private fun layout(path: String): Layout? {
            layouts[path]?.let { return it }
            val root = pkg.xml(path) ?: return null
            val masterPath =
                pkg
                    .relationships(path)
                    .values
                    .firstOrNull { it.hasType("slideMaster") }
                    ?.target ?: return null
            val master = master(masterPath) ?: return null
            return Layout(path, root, master, placeholdersOf(root)).also { layouts[path] = it }
        }

        private fun placeholdersOf(root: XmlElement): List<XmlElement> =
            root
                .path(P, "cSld", "spTree")
                ?.descendants()
                ?.filter { (it.matches(P, "sp") || it.matches(P, "pic")) && placeholderOf(it) != null }
                ?.toList()
                .orEmpty()

        fun slide(path: String): Slide? {
            val root = pkg.xml(path) ?: return null
            if (root.attr("show") == "0") {
                context.warn(ConversionWarning.HIDDEN_CONTENT_SKIPPED)
                return null
            }
            val layoutPath =
                pkg
                    .relationships(path)
                    .values
                    .firstOrNull { it.hasType("slideLayout") }
                    ?.target
            val layout = layoutPath?.let { layout(it) }
            val master =
                layout?.master ?: return Slide(SlideBackground(Colors.WHITE), shapes(root, partFor(path, null, null, true) ?: return null))
            val masterContext = partFor(master.path, master, null, false)!!
            val layoutContext = partFor(layout.path, master, layout, false)!!
            val slideContext = partFor(path, master, layout, true)!!
            val elements = ArrayList<SlideElement>()
            val showMasterShapes = root.attr("showMasterSp") != "0"
            if (showMasterShapes && layout.root.attr("showMasterSp") != "0") elements.addAll(shapes(master.root, masterContext))
            if (showMasterShapes) elements.addAll(shapes(layout.root, layoutContext))
            elements.addAll(shapes(root, slideContext))
            val background =
                background(root, slideContext) ?: background(layout.root, layoutContext) ?: background(master.root, masterContext)
                    ?: SlideBackground(Colors.WHITE)
            return Slide(background, elements)
        }

        private fun partFor(
            path: String,
            master: Master?,
            layout: Layout?,
            resolvePlaceholders: Boolean,
        ): PartContext? {
            val effectiveMaster =
                master
                    ?: Master(
                        path,
                        XmlElement(P, "sldMaster", emptyMap(), emptyList()),
                        Theme(DrawingColors.schemeFrom(null), null, null),
                        DrawingColors.mapFrom(null),
                        emptyList(),
                    )
            return PartContext(
                path = path,
                relationships = pkg.relationships(path),
                colors = DrawingColors(effectiveMaster.theme.colors, effectiveMaster.colorMap),
                theme = effectiveMaster.theme,
                master = effectiveMaster,
                layout = layout,
                resolvePlaceholders = resolvePlaceholders,
            )
        }

        private fun background(
            root: XmlElement,
            part: PartContext,
        ): SlideBackground? {
            val background = root.path(P, "cSld", "bg") ?: return null
            background.child(P, "bgPr")?.let { properties ->
                for (fill in properties.elements) {
                    when (fill.name) {
                        "solidFill" -> return SlideBackground(part.colors.fromContainer(fill))
                        "gradFill" -> return SlideBackground(gradientColor(fill, part))
                        "blipFill" -> return SlideBackground(Colors.WHITE, blipSource(fill, part))
                        "noFill" -> return SlideBackground(Colors.WHITE)
                        "pattFill" -> return SlideBackground(part.colors.fromContainer(fill.child(A, "fgClr")))
                    }
                }
            }
            background.child(P, "bgRef")?.let { reference -> return SlideBackground(part.colors.fromContainer(reference) ?: Colors.WHITE) }
            return null
        }

        private fun gradientColor(
            fill: XmlElement,
            part: PartContext,
        ): Int? {
            val stops =
                fill
                    .child(A, "gsLst")
                    ?.children(A, "gs")
                    ?.toList()
                    .orEmpty()
            return stops.firstOrNull()?.let { part.colors.fromContainer(it) }
        }

        private fun blipSource(
            fill: XmlElement,
            part: PartContext,
        ): ImageSource? {
            val blip = fill.child(A, "blip") ?: return null
            val relationship = blip.attr(R, "embed")?.let { part.relationships[it] }
            if (relationship == null || relationship.external) {
                if (blip.attr(R, "link") != null ||
                    relationship?.external == true
                ) {
                    context.warn(ConversionWarning.EXTERNAL_RESOURCES_SKIPPED)
                }
                return null
            }
            return pkg.archive.imageSource(relationship.target) ?: run {
                context.warn(ConversionWarning.IMAGES_SKIPPED)
                null
            }
        }

        private fun shapes(
            root: XmlElement,
            part: PartContext,
        ): List<SlideElement> {
            val tree = root.path(P, "cSld", "spTree") ?: return emptyList()
            val result = ArrayList<SlideElement>()
            collectShapes(tree, part, Transform(), result)
            return result
        }

        private fun collectShapes(
            container: XmlElement,
            part: PartContext,
            transform: Transform,
            output: MutableList<SlideElement>,
        ) {
            for (element in container.elements) {
                context.checkCancelled()
                if (isHidden(element)) continue
                val isPlaceholder = placeholderOf(element) != null
                if (isPlaceholder && !part.resolvePlaceholders) continue
                when {
                    element.matches(P, "sp") || element.matches(P, "cxnSp") -> {
                        shape(element, part, transform)?.let { output.add(it) }
                    }

                    element.matches(P, "pic") -> {
                        picture(element, part, transform)?.let { output.add(it) }
                    }

                    element.matches(P, "grpSp") -> {
                        collectShapes(element, part, transform.then(groupTransform(element)), output)
                    }

                    element.matches(P, "graphicFrame") -> {
                        graphicFrame(element, part, transform, output)
                    }

                    element.matches(MARKUP_COMPATIBILITY, "AlternateContent") -> {
                        val choice = element.child(MARKUP_COMPATIBILITY, "Fallback") ?: element.child(MARKUP_COMPATIBILITY, "Choice")
                        choice?.let { collectShapes(it, part, transform, output) }
                    }
                }
            }
        }

        private fun isHidden(element: XmlElement): Boolean =
            element.elements.firstOrNull { it.name.startsWith("nv") }?.elements?.firstOrNull { it.name == "cNvPr" }?.attr("hidden").let {
                it ==
                    "1" ||
                    it == "true"
            }

        private fun groupTransform(group: XmlElement): Transform {
            val xfrm = group.child(P, "grpSpPr")?.child(A, "xfrm") ?: return Transform()
            val offX = xfrm.child(A, "off")?.attr("x")?.toFloatOrNull() ?: 0f
            val offY = xfrm.child(A, "off")?.attr("y")?.toFloatOrNull() ?: 0f
            val extX = xfrm.child(A, "ext")?.attr("cx")?.toFloatOrNull() ?: 0f
            val extY = xfrm.child(A, "ext")?.attr("cy")?.toFloatOrNull() ?: 0f
            val childOffX = xfrm.child(A, "chOff")?.attr("x")?.toFloatOrNull() ?: offX
            val childOffY = xfrm.child(A, "chOff")?.attr("y")?.toFloatOrNull() ?: offY
            val childExtX =
                xfrm
                    .child(A, "chExt")
                    ?.attr("cx")
                    ?.toFloatOrNull()
                    ?.takeIf { it > 0 } ?: extX.takeIf { it > 0 } ?: 1f
            val childExtY =
                xfrm
                    .child(A, "chExt")
                    ?.attr("cy")
                    ?.toFloatOrNull()
                    ?.takeIf { it > 0 } ?: extY.takeIf { it > 0 } ?: 1f
            val scaleX = if (extX > 0) extX / childExtX else 1f
            val scaleY = if (extY > 0) extY / childExtY else 1f
            return Transform(scaleX, scaleY, offX - childOffX * scaleX, offY - childOffY * scaleY)
        }

        private data class PlaceholderInfo(
            val type: String,
            val index: String?,
        )

        private fun placeholderOf(element: XmlElement): PlaceholderInfo? {
            val nonVisual = element.elements.firstOrNull { it.name.startsWith("nv") } ?: return null
            val placeholder = nonVisual.child(P, "nvPr")?.child(P, "ph") ?: return null
            return PlaceholderInfo(placeholder.attr("type") ?: "obj", placeholder.attr("idx"))
        }

        private fun normalizedType(type: String): String =
            when (type) {
                "ctrTitle" -> "title"
                "subTitle", "obj", "body" -> "body"
                else -> type
            }

        private fun findPlaceholder(
            candidates: List<XmlElement>,
            info: PlaceholderInfo,
            byIndex: Boolean,
        ): XmlElement? {
            if (byIndex && info.index != null) {
                candidates.firstOrNull { placeholderOf(it)?.index == info.index }?.let { return it }
            }
            candidates.firstOrNull { placeholderOf(it)?.type == info.type }?.let { return it }
            val normalized = normalizedType(info.type)
            return candidates.firstOrNull { placeholderOf(it)?.type?.let(::normalizedType) == normalized }
        }

        private class Inheritance(
            val layoutPlaceholder: XmlElement?,
            val masterPlaceholder: XmlElement?,
            val category: String,
        )

        private fun inheritance(
            element: XmlElement,
            part: PartContext,
        ): Inheritance {
            val info = placeholderOf(element) ?: return Inheritance(null, null, "other")
            val layoutPlaceholder = part.layout?.let { findPlaceholder(it.placeholders, info, true) }
            val masterInfo = layoutPlaceholder?.let { placeholderOf(it) } ?: info
            val masterPlaceholder = findPlaceholder(part.master.placeholders, masterInfo, false)
            val category =
                when (normalizedType(masterInfo.type)) {
                    "title" -> "title"
                    "body" -> "body"
                    "dt", "ftr", "sldNum", "hdr" -> "other"
                    else -> "body"
                }
            return Inheritance(layoutPlaceholder, masterPlaceholder, category)
        }

        private fun shapeProperties(element: XmlElement): XmlElement? = element.child(P, "spPr")

        private fun frameOf(
            xfrm: XmlElement?,
            transform: Transform,
        ): Frame? {
            xfrm ?: return null
            val offset = xfrm.child(A, "off") ?: return null
            val extent = xfrm.child(A, "ext") ?: return null
            val x = offset.attr("x")?.toFloatOrNull() ?: return null
            val y = offset.attr("y")?.toFloatOrNull() ?: return null
            val width = extent.attr("cx")?.toFloatOrNull() ?: return null
            val height = extent.attr("cy")?.toFloatOrNull() ?: return null
            val emu = Units.EMU_PER_POINT
            return Frame(
                x = (x * transform.scaleX + transform.offsetX) / emu,
                y = (y * transform.scaleY + transform.offsetY) / emu,
                width = width * transform.scaleX / emu,
                height = height * transform.scaleY / emu,
                rotationDeg = (xfrm.attr("rot")?.toFloatOrNull() ?: 0f) / 60_000f,
                flipHorizontal = xfrm.attr("flipH") == "1",
                flipVertical = xfrm.attr("flipV") == "1",
            )
        }

        private fun resolvedFrame(
            element: XmlElement,
            inheritance: Inheritance,
            transform: Transform,
        ): Frame? =
            frameOf(shapeProperties(element)?.child(A, "xfrm"), transform)
                ?: frameOf(inheritance.layoutPlaceholder?.let { shapeProperties(it) }?.child(A, "xfrm"), Transform())
                ?: frameOf(inheritance.masterPlaceholder?.let { shapeProperties(it) }?.child(A, "xfrm"), Transform())

        private fun shape(
            element: XmlElement,
            part: PartContext,
            transform: Transform,
        ): SlideElement? {
            val inheritance = inheritance(element, part)
            val frame = resolvedFrame(element, inheritance, transform) ?: return null
            val properties = shapeProperties(element)
            val style = element.child(P, "style")
            val geometry = geometry(properties)
            val (fillColor, fillImage) = fill(properties, style, part)
            val (strokeColor, strokeWidth) = line(properties, style, part, geometry is ShapeOutline.Line)
            val text = element.child(P, "txBody")?.let { textBox(it, element, inheritance, part, style) }
            if (fillColor == null && fillImage == null && strokeColor == null && (text == null || text.isEmpty)) return null
            return ShapeElement(frame, geometry, fillColor, fillImage, strokeColor, strokeWidth, text)
        }

        private fun geometry(properties: XmlElement?): ShapeOutline {
            properties?.child(A, "custGeom")?.let { custom -> customGeometry(custom)?.let { return it } }
            val preset = properties?.child(A, "prstGeom")
            val adjustment =
                preset
                    ?.child(A, "avLst")
                    ?.children(A, "gd")
                    ?.firstOrNull()
                    ?.attr("fmla")
                    ?.removePrefix("val ")
                    ?.trim()
                    ?.toFloatOrNull()
                    ?.let { (it / 100_000f).coerceIn(0f, 0.5f) }
            return PresetGeometry.outline(
                preset?.attr("prst") ?: if (properties?.child(A, "custGeom") !=
                    null
                ) {
                    "rect"
                } else {
                    null
                },
                adjustment,
            )
        }

        private fun customGeometry(custom: XmlElement): ShapeOutline? {
            val paths =
                custom
                    .child(A, "pathLst")
                    ?.children(A, "path")
                    ?.toList()
                    .orEmpty()
            if (paths.isEmpty()) return null
            val commands = ArrayList<PathCommand>()
            var filled = false
            for (path in paths) {
                val width = path.attr("w")?.toFloatOrNull()?.takeIf { it > 0 } ?: continue
                val height = path.attr("h")?.toFloatOrNull()?.takeIf { it > 0 } ?: continue
                if (path.attr("fill") != "none") filled = true

                fun point(element: XmlElement): Pair<Float, Float>? {
                    val x = element.attr("x")?.toFloatOrNull() ?: return null
                    val y = element.attr("y")?.toFloatOrNull() ?: return null
                    return x / width to y / height
                }
                for (command in path.elements) {
                    val points = command.children(A, "pt").mapNotNull { point(it) }.toList()
                    when (command.name) {
                        "moveTo" -> {
                            points.firstOrNull()?.let { commands.add(PathCommand.MoveTo(it.first, it.second)) }
                        }

                        "lnTo" -> {
                            points.firstOrNull()?.let { commands.add(PathCommand.LineTo(it.first, it.second)) }
                        }

                        "quadBezTo" -> {
                            if (points.size >=
                                2
                            ) {
                                commands.add(PathCommand.QuadTo(points[0].first, points[0].second, points[1].first, points[1].second))
                            }
                        }

                        "cubicBezTo" -> {
                            if (points.size >= 3) {
                                commands.add(
                                    PathCommand.CubicTo(
                                        points[0].first,
                                        points[0].second,
                                        points[1].first,
                                        points[1].second,
                                        points[2].first,
                                        points[2].second,
                                    ),
                                )
                            }
                        }

                        "arcTo" -> {
                            val radiusX = command.attr("wR")?.toFloatOrNull() ?: continue
                            val radiusY = command.attr("hR")?.toFloatOrNull() ?: continue
                            val start = (command.attr("stAng")?.toFloatOrNull() ?: 0f) / 60_000f
                            val sweep = (command.attr("swAng")?.toFloatOrNull() ?: 0f) / 60_000f
                            commands.add(PathCommand.ArcTo(radiusX / width, radiusY / height, start, sweep))
                        }

                        "close" -> {
                            commands.add(PathCommand.Close)
                        }
                    }
                }
            }
            return if (commands.isEmpty()) null else ShapeOutline.Path(commands, filled)
        }

        private fun fill(
            properties: XmlElement?,
            style: XmlElement?,
            part: PartContext,
        ): Pair<Int?, ImageSource?> {
            properties?.elements?.forEach { child ->
                when (child.name) {
                    "noFill" -> return null to null
                    "solidFill" -> return part.colors.fromContainer(child) to null
                    "gradFill" -> return gradientColor(child, part) to null
                    "blipFill" -> return null to blipSource(child, part)
                    "pattFill" -> return part.colors.fromContainer(child.child(A, "fgClr")) to null
                }
            }
            val reference = style?.child(A, "fillRef") ?: return null to null
            if ((reference.attr("idx")?.toIntOrNull() ?: 0) == 0) return null to null
            return part.colors.fromContainer(reference) to null
        }

        private fun line(
            properties: XmlElement?,
            style: XmlElement?,
            part: PartContext,
            isLine: Boolean,
        ): Pair<Int?, Float> {
            val line = properties?.child(A, "ln")
            val width = line?.attr("w")?.toFloatOrNull()?.let { it / Units.EMU_PER_POINT } ?: DEFAULT_LINE_WIDTH
            line?.elements?.forEach { child ->
                when (child.name) {
                    "noFill" -> return null to 0f
                    "solidFill" -> return part.colors.fromContainer(child) to width
                    "gradFill" -> return gradientColor(child, part) to width
                }
            }
            val reference = style?.child(A, "lnRef")
            if (reference != null && (reference.attr("idx")?.toIntOrNull() ?: 0) > 0) return part.colors.fromContainer(reference) to width
            return if (isLine && line != null) Colors.BLACK to width else null to 0f
        }

        private fun picture(
            element: XmlElement,
            part: PartContext,
            transform: Transform,
        ): SlideElement? {
            val inheritance = inheritance(element, part)
            val frame = resolvedFrame(element, inheritance, transform) ?: return null
            val blipFill = element.child(P, "blipFill") ?: return null
            val source = blipSource(blipFill, part) ?: return null
            val crop =
                blipFill.child(A, "srcRect")?.let { rect ->
                    fun value(name: String) = (rect.attr(name)?.toFloatOrNull() ?: 0f) / 100_000f
                    Crop(value("l"), value("t"), value("r"), value("b")).takeIf {
                        it.left != 0f || it.top != 0f || it.right != 0f ||
                            it.bottom != 0f
                    }
                }
            return PictureElement(frame, source, crop)
        }

        private fun graphicFrame(
            element: XmlElement,
            part: PartContext,
            transform: Transform,
            output: MutableList<SlideElement>,
        ) {
            val frame = frameOf(element.child(P, "xfrm"), transform) ?: return
            val data = element.path(A, "graphic", "graphicData") ?: return
            val table = data.child(A, "tbl")
            if (table != null) {
                output.add(table(table, frame, part))
                return
            }
            val oleObject = data.descendants(P, "oleObj").firstOrNull()
            val oleImage = oleObject?.child(P, "pic") ?: data.descendants(P, "pic").firstOrNull()
            if (oleImage != null) {
                val source = oleImage.child(P, "blipFill")?.let { blipSource(it, part) }
                if (source != null) {
                    output.add(PictureElement(frame, source))
                    return
                }
            }
            context.warn(ConversionWarning.EMBEDDED_OBJECTS_SKIPPED)
        }

        private fun table(
            table: XmlElement,
            frame: Frame,
            part: PartContext,
        ): TableElement {
            val columnWidths =
                table
                    .child(A, "tblGrid")
                    ?.children(A, "gridCol")
                    ?.map { (it.attr("w")?.toFloatOrNull() ?: 0f) / Units.EMU_PER_POINT }
                    ?.toList()
                    .orEmpty()
            val tableProperties = table.child(A, "tblPr")
            val styled = tableProperties?.child(A, "tableStyleId") != null || tableProperties?.attr("firstRow") == "1"
            val firstRow = tableProperties?.attr("firstRow") == "1"
            val bandRows = tableProperties?.attr("bandRow") == "1"
            val accent = part.colors.accent(1)
            val rows =
                table
                    .children(A, "tr")
                    .mapIndexed { rowIndex, row ->
                        val header = firstRow && rowIndex == 0
                        val cells =
                            row
                                .children(A, "tc")
                                .filter { it.attr("hMerge") != "1" }
                                .map { cell ->
                                    val cellProperties = cell.child(A, "tcPr")
                                    val explicitFill =
                                        cellProperties?.elements?.firstOrNull { it.name == "solidFill" }?.let {
                                            part.colors
                                                .fromContainer(
                                                    it,
                                                )
                                        }
                                    val noFill = cellProperties?.child(A, "noFill") != null
                                    val fill =
                                        when {
                                            explicitFill != null -> {
                                                explicitFill
                                            }

                                            noFill || !styled -> {
                                                null
                                            }

                                            header -> {
                                                accent
                                            }

                                            bandRows -> {
                                                if ((rowIndex - if (firstRow) 1 else 0) % 2 ==
                                                    0
                                                ) {
                                                    Colors.tint(accent, 0.4f)
                                                } else {
                                                    Colors.tint(accent, 0.2f)
                                                }
                                            }

                                            else -> {
                                                Colors.tint(accent, 0.2f)
                                            }
                                        }
                                    val textColor = if (header && styled && explicitFill == null) Colors.WHITE else null
                                    val body = cell.child(A, "txBody")
                                    val insets =
                                        Insets(
                                            emu(cellProperties?.attr("marL"), 91_440f),
                                            emu(cellProperties?.attr("marT"), 45_720f),
                                            emu(cellProperties?.attr("marR"), 91_440f),
                                            emu(cellProperties?.attr("marB"), 45_720f),
                                        )
                                    val paragraphs =
                                        if (body == null || cell.attr("vMerge") == "1") {
                                            emptyList()
                                        } else {
                                            paragraphs(body, listOf(body.child(A, "lstStyle")), part, header, textColor, null)
                                        }
                                    val anchor =
                                        when (cellProperties?.attr("anchor")) {
                                            "ctr" -> VerticalAnchor.MIDDLE
                                            "b" -> VerticalAnchor.BOTTOM
                                            else -> VerticalAnchor.TOP
                                        }
                                    SlideTableCell(
                                        TextBox(paragraphs, insets, anchor),
                                        fill,
                                        cell.attr("gridSpan")?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                    )
                                }.toList()
                        SlideTableRow((row.attr("h")?.toFloatOrNull() ?: 0f) / Units.EMU_PER_POINT, cells)
                    }.toList()
            val widths = if (columnWidths.isEmpty()) listOf(frame.width) else columnWidths
            return TableElement(frame, widths, rows)
        }

        private fun emu(
            value: String?,
            default: Float,
        ): Float = (value?.toFloatOrNull() ?: default) / Units.EMU_PER_POINT

        private fun textBox(
            body: XmlElement,
            element: XmlElement,
            inheritance: Inheritance,
            part: PartContext,
            style: XmlElement?,
        ): TextBox {
            val bodies =
                listOfNotNull(
                    body.child(A, "bodyPr"),
                    inheritance.layoutPlaceholder?.child(P, "txBody")?.child(A, "bodyPr"),
                    inheritance.masterPlaceholder?.child(P, "txBody")?.child(A, "bodyPr"),
                )

            fun bodyAttr(name: String): String? = bodies.firstNotNullOfOrNull { it.attr(name) }
            val autofit = bodies.firstNotNullOfOrNull { it.child(A, "normAutofit") ?: it.child(A, "noAutofit") ?: it.child(A, "spAutoFit") }
            val fontScale = if (autofit?.name == "normAutofit") (autofit.attr("fontScale")?.toFloatOrNull()?.div(100_000f) ?: 1f) else 1f
            val lineReduction =
                if (autofit?.name ==
                    "normAutofit"
                ) {
                    (autofit.attr("lnSpcReduction")?.toFloatOrNull()?.div(100_000f) ?: 0f)
                } else {
                    0f
                }
            val insets =
                Insets(
                    emu(bodyAttr("lIns"), 91_440f),
                    emu(bodyAttr("tIns"), 45_720f),
                    emu(bodyAttr("rIns"), 91_440f),
                    emu(bodyAttr("bIns"), 45_720f),
                )
            val anchor =
                when (bodyAttr("anchor")) {
                    "ctr" -> VerticalAnchor.MIDDLE
                    "b" -> VerticalAnchor.BOTTOM
                    else -> VerticalAnchor.TOP
                }
            val masterStyles = part.master.textStyles
            val categoryStyle =
                when (inheritance.category) {
                    "title" -> masterStyles?.child(P, "titleStyle")
                    "body" -> masterStyles?.child(P, "bodyStyle")
                    else -> masterStyles?.child(P, "otherStyle")
                }
            val listStyles =
                listOf(
                    body.child(A, "lstStyle"),
                    inheritance.layoutPlaceholder?.child(P, "txBody")?.child(A, "lstStyle"),
                    inheritance.masterPlaceholder?.child(P, "txBody")?.child(A, "lstStyle"),
                    categoryStyle,
                    defaultTextStyle,
                )
            val fontReferenceColor = style?.child(A, "fontRef")?.let { part.colors.fromContainer(it) }
            val paragraphs = paragraphs(body, listStyles, part, false, fontReferenceColor, lineReduction)
            return TextBox(
                paragraphs = paragraphs,
                insets = insets,
                anchor = anchor,
                fontScale = fontScale,
                shrinkToFit = autofit?.name == "normAutofit",
                wrap = bodyAttr("wrap") != "none",
            )
        }

        private fun paragraphs(
            body: XmlElement,
            listStyles: List<XmlElement?>,
            part: PartContext,
            forceBold: Boolean,
            fallbackColor: Int?,
            lineReduction: Float?,
        ): List<SlideParagraph> {
            val counters = IntArray(9)
            val schemes = arrayOfNulls<String>(9)
            return body
                .children(A, "p")
                .map { paragraph ->
                    val properties = paragraph.child(A, "pPr")
                    val level = (properties?.attr("lvl")?.toIntOrNull() ?: 0).coerceIn(0, 8)
                    val sources = listOfNotNull(properties) + listStyles.mapNotNull { levelProperties(it, level) }

                    fun attr(name: String): String? = sources.firstNotNullOfOrNull { it.attr(name) }

                    fun child(name: String): XmlElement? = sources.firstNotNullOfOrNull { it.child(A, name) }
                    val defaultRuns = sources.mapNotNull { it.child(A, "defRPr") }
                    val runs = ArrayList<TextRun>()
                    for (child in paragraph.elements) {
                        when (child.name) {
                            "r", "fld" -> {
                                val text = child.child(A, "t")?.textContent().orEmpty()
                                val runProperties = listOfNotNull(child.child(A, "rPr")) + defaultRuns
                                val capitalized =
                                    if (runProperties.firstNotNullOfOrNull { it.attr("cap") } ==
                                        "all"
                                    ) {
                                        text.uppercase()
                                    } else {
                                        text
                                    }
                                runs.add(TextRun(capitalized, runStyle(runProperties, part, forceBold, fallbackColor)))
                            }

                            "br" -> {
                                runs.add(
                                    TextRun(
                                        "\n",
                                        runStyle(listOfNotNull(child.child(A, "rPr")) + defaultRuns, part, forceBold, fallbackColor),
                                    ),
                                )
                            }
                        }
                    }
                    val emptySize = runSize(listOfNotNull(paragraph.child(A, "endParaRPr")) + defaultRuns)
                    val firstSize = runs.firstOrNull()?.style?.fontSizePt ?: emptySize
                    val hasText = runs.any { it.text.isNotBlank() }
                    val bullet = if (hasText) bulletText(sources, level, counters, schemes) else null
                    if (!hasText) clearLevels(schemes, level)
                    val marginLeft = emu(attr("marL"), 0f)
                    val indent = emu(attr("indent"), 0f)
                    val bulletColor = child("buClr")?.let { part.colors.fromContainer(it) }
                    val lineSpacing =
                        (
                            child("lnSpc")
                                ?.child(A, "spcPct")
                                ?.attr("val")
                                ?.toFloatOrNull()
                                ?.div(100_000f) ?: 1f
                        ) - (lineReduction ?: 0f)
                    SlideParagraph(
                        runs = runs,
                        alignment =
                            when (attr("algn")) {
                                "ctr" -> Alignment.CENTER
                                "r" -> Alignment.END
                                "just", "dist" -> Alignment.JUSTIFY
                                else -> Alignment.START
                            },
                        bullet = bullet,
                        bulletColor = bulletColor,
                        indentPt = marginLeft,
                        bulletIndentPt = (marginLeft + indent).coerceAtLeast(0f),
                        spaceBeforePt = spacing(child("spcBef"), firstSize),
                        spaceAfterPt = spacing(child("spcAft"), firstSize),
                        lineSpacing = lineSpacing.coerceIn(0.5f, 3f),
                        emptyLineSizePt = emptySize,
                    )
                }.toList()
        }

        private fun levelProperties(
            listStyle: XmlElement?,
            level: Int,
        ): XmlElement? = listStyle?.child(A, "lvl${level + 1}pPr") ?: if (level == 0) listStyle?.child(A, "defPPr") else null

        private fun spacing(
            element: XmlElement?,
            fontSize: Float,
        ): Float {
            element ?: return 0f
            element
                .child(A, "spcPts")
                ?.attr("val")
                ?.toFloatOrNull()
                ?.let { return it / 100f }
            element
                .child(A, "spcPct")
                ?.attr("val")
                ?.toFloatOrNull()
                ?.let { return fontSize * 1.2f * it / 100_000f }
            return 0f
        }

        private fun clearLevels(
            schemes: Array<String?>,
            fromLevel: Int,
        ) {
            for (index in fromLevel until schemes.size) schemes[index] = null
        }

        private fun bulletText(
            sources: List<XmlElement>,
            level: Int,
            counters: IntArray,
            schemes: Array<String?>,
        ): String? {
            val bulletDefinition =
                sources.firstNotNullOfOrNull { source ->
                    source.elements.firstOrNull {
                        it.name == "buNone" || it.name == "buChar" || it.name == "buAutoNum" ||
                            it.name == "buBlip"
                    }
                }
            for (deeper in level + 1 until schemes.size) schemes[deeper] = null
            when (bulletDefinition?.name) {
                "buChar" -> {
                    schemes[level] = null
                    val font = sources.firstNotNullOfOrNull { it.child(A, "buFont") }?.attr("typeface")
                    return symbolBullet(bulletDefinition.attr("char").orEmpty(), font)
                }

                "buBlip" -> {
                    schemes[level] = null
                    return "•"
                }

                "buAutoNum" -> {
                    val type = bulletDefinition.attr("type") ?: "arabicPeriod"
                    if (schemes[level] == type) {
                        counters[level]++
                    } else {
                        schemes[level] = type
                        counters[level] = bulletDefinition.attr("startAt")?.toIntOrNull() ?: 1
                    }
                    return autoNumber(type, counters[level])
                }

                else -> {
                    schemes[level] = null
                    return null
                }
            }
        }

        private fun symbolBullet(
            char: String,
            font: String?,
        ): String {
            if (font?.contains("wingdings", ignoreCase = true) == true) {
                return when (char) {
                    "§" -> "▪"
                    "Ø" -> "➢"
                    "ü" -> "✓"
                    "v" -> "❖"
                    "q" -> "❑"
                    "n" -> "■"
                    "l" -> "●"
                    "Ш" -> "▸"
                    else -> "•"
                }
            }
            return ListNumbering.normalizeBulletGlyph(char)
        }

        private fun autoNumber(
            type: String,
            value: Int,
        ): String {
            val style =
                when {
                    type.startsWith("alphaLc") -> NumberStyle.LOWER_ALPHA
                    type.startsWith("alphaUc") -> NumberStyle.UPPER_ALPHA
                    type.startsWith("romanLc") -> NumberStyle.LOWER_ROMAN
                    type.startsWith("romanUc") -> NumberStyle.UPPER_ROMAN
                    else -> NumberStyle.DECIMAL
                }
            val number = ListNumbering.format(value, style)
            return when {
                type.endsWith("ParenBoth") -> "($number)"
                type.endsWith("ParenR") -> "$number)"
                type.endsWith("Minus") -> "$number -"
                type.endsWith("Plain") -> number
                else -> "$number."
            }
        }

        private fun runSize(properties: List<XmlElement>): Float =
            properties.firstNotNullOfOrNull { it.attr("sz")?.toFloatOrNull() }?.div(100f) ?: DEFAULT_FONT_SIZE

        private fun runStyle(
            properties: List<XmlElement>,
            part: PartContext,
            forceBold: Boolean,
            fallbackColor: Int?,
        ): TextStyle {
            fun attr(name: String): String? = properties.firstNotNullOfOrNull { it.attr(name) }
            val color =
                properties.firstNotNullOfOrNull { element ->
                    element.elements.firstOrNull { it.name == "solidFill" || it.name == "noFill" || it.name == "gradFill" }
                }
            val resolvedColor =
                when (color?.name) {
                    "solidFill" -> part.colors.fromContainer(color)
                    "gradFill" -> gradientColor(color, part)
                    else -> null
                } ?: fallbackColor ?: Colors.BLACK
            val typeface =
                properties.firstNotNullOfOrNull { it.child(A, "latin")?.attr("typeface") }?.let { name ->
                    when {
                        name.startsWith("+mj") -> part.theme.majorFont
                        name.startsWith("+mn") -> part.theme.minorFont
                        else -> name
                    }
                } ?: part.theme.minorFont
            val baseline = attr("baseline")?.toIntOrNull() ?: 0
            return TextStyle(
                bold = forceBold || attr("b").let { it == "1" || it == "true" },
                italic = attr("i").let { it == "1" || it == "true" },
                underline = attr("u").let { it != null && it != "none" },
                strikethrough = attr("strike").let { it != null && it != "noStrike" },
                family = Units.fontFamilyFor(typeface),
                fontSizePt = runSize(properties),
                color = resolvedColor,
                baseline =
                    when {
                        baseline > 0 -> Baseline.SUPERSCRIPT
                        baseline < 0 -> Baseline.SUBSCRIPT
                        else -> Baseline.NORMAL
                    },
            )
        }
    }

    private companion object {
        const val DEFAULT_FONT_SIZE = 18f
        const val DEFAULT_LINE_WIDTH = 0.75f
    }
}
