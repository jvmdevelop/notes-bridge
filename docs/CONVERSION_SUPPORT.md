# Conversion support

This page lists, for each format, which engine handles it, what is converted and what is not. It describes version 0.1.0. A format is listed as supported only if it has a reader in the code and tests in `app/src/test` and `app/src/androidTest`.

All readers run locally. "Re-flowed" means the content is laid out again by NotesBridge on pages of the document's own size (or A4), so line and page breaks can differ from the original application.

## Word processing

### DOCX (also DOCM, DOTX)

Engine: `formats/docx/DocxReader`, a streaming-free XML reader over the ZIP package. Macros in DOCM files are never read or run.

| Feature | Status |
| --- | --- |
| Paragraph text, line breaks, tabs | Yes |
| Bold, italic, underline, strikethrough, superscript/subscript, all caps | Yes |
| Font size and colour, serif/sans/mono family | Yes, mapped to Android system fonts |
| Paragraph styles (`styles.xml`, `basedOn` chains, document defaults) | Yes |
| Headings (built-in "heading N", Title, Subtitle, outline levels) | Yes |
| Alignment, left indent, spacing before/after, line spacing | Yes |
| Numbered and bulleted lists (`numbering.xml`, multi-level, restarts) | Yes |
| Tables, column spans, header rows repeated on new pages | Yes. Vertical merges become empty cells. Cell shading and borders are not reproduced. |
| Inline and anchored pictures (PNG, JPEG, GIF, BMP, WebP) | Yes, at the size given in the document. Floating pictures are placed inline. |
| Text boxes (DrawingML and VML) | Text is placed after the paragraph that anchors it |
| Page breaks, page-break-before, section breaks | Yes |
| Page size and margins | Yes, from the last section |
| Hidden text | Skipped (reported) |
| Tracked changes | Insertions shown, deletions hidden |
| Equations (OMML) | Plain text approximation |
| Headers, footers, footnotes, endnotes, comments | Not converted |
| Charts, SmartArt, OLE objects | Not converted (reported). An OLE object's preview image is shown if it is PNG/JPEG. |
| EMF/WMF pictures | Not converted (reported) |
| Columns, floating positions, wrap modes | Not reproduced |
| Strict OOXML namespaces | Accepted |

### ODT (also OTT)

Engine: `formats/odf/OdtReader` with `OdfStyles` (style inheritance across `styles.xml` and `content.xml`).

Same feature set as DOCX: text styles, headings, lists with bullet characters and numbering formats, tables with header rows, images in frames, text boxes, page breaks from paragraph styles, page size from the default master page. Notes, annotations and embedded objects (charts, formulas) are not converted.

### RTF

Engine: `formats/rtf/RtfParser`, a byte-level tokenizer.

| Feature | Status |
| --- | --- |
| Text in any ANSI code page (`\ansicpg`, per-font `\fcharset`), Unicode (`\u`) | Yes |
| Bold, italic, underline, strikethrough, sizes, colours, super/subscript | Yes |
| Alignment, left indent | Yes |
| Lists (`\listtext`, `\pntext`) | Yes, the rendered marker is kept |
| Tables (`\cell`, `\row`) | Yes, plain grid |
| Pictures | PNG and JPEG (`\pngblip`, `\jpegblip`, hex or `\bin`). WMF/EMF/PICT are skipped and reported |
| Page breaks (`\page`, `\pagebb`), paper size | Yes |
| Headers, footers, footnotes, fields' instructions, style sheet | Skipped |

## Spreadsheets

Spreadsheets are converted as data: each visible sheet becomes a heading and a table. Pages switch to landscape when a sheet has more than six columns, and the font shrinks down to 5 pt to fit wide sheets.

### XLSX (also XLSM, XLTX)

Engine: `formats/xlsx/XlsxReader`. Worksheets and shared strings are read with a streaming pull parser, so large sheets do not build a full XML tree in memory.

| Feature | Status |
| --- | --- |
| Shared strings, inline strings, formulas' cached values, booleans, errors | Yes |
| Number formats: general, fixed decimals, thousands separators, percent | Yes |
| Dates and times (built-in and custom formats, 1900 and 1904 date systems) | Yes, as ISO `yyyy-MM-dd` / `HH:mm` |
| Bold cells | Yes |
| Numeric alignment | Right-aligned |
| Hidden sheets | Skipped (reported). Hidden rows and columns are included. |
| Merged cells, cell colours, borders, column widths, print areas, charts, images, comments | Not converted |
| Limit | 100,000 cells and 100 columns in total. Content beyond that is cut off (reported). |

### ODS (also OTS)

Engine: `formats/odf/OdsReader`. Uses the formatted display text stored in the file, so dates and currencies look as they did in the spreadsheet. Repeated empty rows and columns (`number-rows-repeated` up to the full sheet size) are skipped without expanding them. Hidden sheets are skipped. Same limitations as XLSX.

### CSV / TSV

Engine: `formats/csv/CsvParser`. RFC 4180 quoting, embedded newlines, delimiter detection among `,` `;` tab and `|`, encoding detection. The first row is treated as a header and repeated on every page.

## Presentations

Each slide becomes one PDF page with the slide's own size (16:9, 4:3 or custom).

### PPTX (also PPTM, PPSX, POTX)

Engine: `formats/pptx/PptxReader`.

| Feature | Status |
| --- | --- |
| Slide order, hidden slides | Yes. Hidden slides are skipped (reported). |
| Placeholders inherited from slide layout and slide master (position, text styles, body properties) | Yes |
| Master and layout decorations (logos, bars), `showMasterSp` | Yes |
| Theme colours, colour map, `lumMod`/`lumOff`/`tint`/`shade` | Yes |
| Theme fonts (`+mj-lt`, `+mn-lt`) | Mapped to serif/sans/mono system fonts |
| Text: sizes, bold, italic, underline, strike, colour, super/subscript, caps | Yes |
| Bullets (characters, Wingdings mapping, picture bullets as •) and auto-numbering | Yes |
| Indents, alignment, line spacing, spacing before/after | Yes |
| Vertical anchoring, insets, no-wrap | Yes |
| Autofit (`normAutofit` font scale and shrink-to-fit) | Yes |
| Preset shapes | More than 60 presets (rectangles, rounded rectangles, ellipses, triangles, arrows, stars, polygons, connectors); unknown presets fall back to a rectangle |
| Custom geometry | Yes (move, line, quadratic and cubic Bézier, arc, close) |
| Solid fills, line colour and width, style references (`fillRef`, `lnRef`, `fontRef`) | Yes |
| Gradient fills | First stop colour only |
| Picture fills, pictures with cropping and flips, rotation | Yes |
| Groups (nested transforms) | Yes. Group rotation is not applied. |
| Tables | Yes, with the default table style approximated (header row and banded rows in accent colour) |
| Slide backgrounds (solid, gradient first colour, picture, theme reference) | Yes |
| Charts, SmartArt, video, audio, OLE objects | Not converted (reported). Poster images of videos and OLE preview images are shown. |
| Shadows, glow, reflections, 3D, transitions, animations, speaker notes | Not converted |

### ODP (also OTP)

Engine: `formats/odf/OdpReader`.

Supported: page size from the master page, master page decorations, page backgrounds (solid, gradient first colour, bitmap), text frames with presentation styles, lists with bullets and numbering, custom shapes with common geometries, rectangles, ellipses, lines and connectors, images, simple tables, `draw:transform` rotation and position. Not supported: charts, embedded objects, 3D scenes, speaker notes, animations.

## Text and markup

| Format | Engine | Notes |
| --- | --- | --- |
| TXT | `PlainTextReader` | UTF-8, UTF-16 (with or without BOM), Windows-1251 and Windows-1252 detection. One paragraph per line. |
| Markdown | commonmark-java with GFM tables and strikethrough, then `HtmlFlowBuilder` | Headings, emphasis, lists, nested lists, tables, quotes, code blocks, horizontal rules. Images referencing files or URLs are not loaded (reported). |
| HTML | jsoup, then `HtmlFlowBuilder` | Headings, paragraphs, inline styles (`font-weight`, `font-style`, `text-decoration`, `color`, `text-align`, `display:none`), lists, tables with colspan and header rows, `pre`, quotes, `data:` images, inline SVG. `<script>`, `<style>`, forms, iframes and media are dropped. Remote images are never fetched. |
| EPUB 2 / 3 | Package reader + jsoup + `HtmlFlowBuilder` | Spine order (linear items), one page break per chapter, images resolved inside the book. Books with `META-INF/encryption.xml` entries other than font obfuscation are treated as DRM-protected and rejected. Book CSS is ignored. |

## Images

| Format | Engine | Notes |
| --- | --- | --- |
| PNG, JPEG, WebP, GIF, BMP | `BitmapFactory` | One page per image, page sized to the image's aspect ratio (long side 842 pt). EXIF orientation applied. Large images are downsampled to 2,400 px on the long side. GIF: first frame. |
| HEIF/HEIC | `BitmapFactory` | Requires Android 9+. |
| AVIF | `BitmapFactory` | Requires Android 12+. |
| SVG | AndroidSVG | Drawn as vectors. External files and URLs are not loaded. |

## Not supported

| Format | Reason | What the user sees |
| --- | --- | --- |
| DOC, XLS, PPT (binary OLE2) | Would require Apache POI's HWPF/HSSF/HSLF plus custom renderers | "…is a legacy Office file. Save it as .docx, .xlsx or .pptx and try again." |
| Password-protected OOXML | Encrypted packages are OLE2 containers; decrypting them would require the password | "…is password-protected." |
| TIFF | No decoder on Android | "TIFF is not supported yet. Convert it to PNG or JPEG first." |
| PDF | Already the target format | "…is already a PDF. Share it to Samsung Notes directly." |
| Files whose content does not match their extension | Safety check | "The content of … does not match its file type." |

## Limits

| Limit | Value |
| --- | --- |
| Input file size | 100 MB per file |
| Text-based formats (TXT, CSV, HTML, Markdown, RTF, SVG) | 20 MB |
| Files shared at once | 50 |
| Decompressed size of one ZIP entry / whole archive | 150 MB / 600 MB |
| Suspicious compression ratio | Rejected above 200:1 for entries larger than 1 MB |
| ZIP entries | 20,000 |
| XML elements per part | 600,000 |
| Pages per PDF | 2,000 |
| Table cells / columns | 100,000 / 100 |
