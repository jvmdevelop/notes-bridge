# Architecture

This document describes how NotesBridge is put together and why the main technical decisions were made.

## Goals that shaped the design

1. **Local only.** Documents never leave the device, so the app has no network permission at all.
2. **Small and dependency-light.** A share target should install quickly and start instantly. The release APK is about 1.8 MB.
3. **Honest output.** When something cannot be converted, the user is told what was skipped instead of getting a silently incomplete PDF.
4. **Extensible.** A new format should only need a reader and one line of registration.

## Flow

```
Intent (SEND / SEND_MULTIPLE / VIEW / EXTRA_TEXT)
        │
        ▼
ShareReceiverActivity ── IncomingShare.parse(intent) ──► List<IncomingDocument>
        │
        ▼
ShareViewModel (survives rotation, owns the coroutine, supports cancel)
        │
        ▼
ConversionManager.convert()
   1. FileManager.import()        copy each URI into cache/input with a size limit
   2. FormatDetector.detect()     decide the real format from content
   3. ConverterRegistry           pick the PdfExporter for that format
   4. PdfExporter.export()        append pages to a shared PdfSink
   5. PdfSink.writeTo()           write cache/output/<uuid>/<name>.pdf
   6. FileManager.deleteInputs()  always, also on error and cancel
        │
        ▼
ShareUiState.Success ──► NotesLauncher builds intents with a FileProvider URI
```

## Components

| Component | Responsibility |
| --- | --- |
| `share/ShareReceiverActivity` | Dialog-styled activity that receives shares. Shows progress (after a short delay) and the result, and launches the target app. |
| `share/IncomingShare` | Turns an incoming `Intent` into `IncomingDocument`s: stream URIs from `EXTRA_STREAM`, `ClipData` or `data`, or plain text. |
| `share/ShareViewModel` | Runs the conversion in `viewModelScope`, exposes `StateFlow<ShareUiState>`, handles cancel and "Save as". |
| `convert/ConversionManager` | Orchestrates import, detection, conversion and writing. Maps unexpected exceptions to `ConversionException`, deletes partial output on failure. |
| `detect/FormatDetector` | Detects the format from magic bytes, ZIP container contents (`mimetype`, `[Content_Types].xml`), then declared MIME type and extension. Content always wins over the file name. |
| `convert/ConverterRegistry` | Maps each `DocumentFormat` to exactly one `PdfExporter`. Duplicate registrations fail fast. |
| `convert/PdfExporter` | Common interface: `export(source, sink, context)`. |
| `io/FileManager` | Imports URIs into the cache with a byte limit, sanitises names, creates output files, removes old files. |
| `io/CleanupJobService` | `JobScheduler` job that deletes PDFs older than one hour, even if the app is not opened again. |
| `launch/NotesLauncher` | Builds `ACTION_SEND` / `ACTION_VIEW` intents for Samsung Notes and for the system chooser, with `FLAG_GRANT_READ_URI_PERMISSION` and `ClipData`. |

## Document models

Format readers do not draw anything. They produce one of two models defined in `model/`:

- **`FlowDocument`**: a list of blocks (`Paragraph`, `Table`, `ImageBlock`, `PageBreak`, `HorizontalRule`) plus a `PageSetup`. Used by DOCX, ODT, RTF, TXT, CSV, Markdown, HTML, EPUB, XLSX and ODS.
- **`SlideDeck`**: slides with absolutely positioned elements (`ShapeElement` with optional text, `PictureElement`, `TableElement`) and a background. Used by PPTX and ODP.

The models are plain Kotlin with no Android types. Readers can therefore be unit-tested on the JVM with real files, which is where most of the test suite lives.

Two renderers in `render/` turn the models into PDF pages:

- `FlowPdfRenderer` paginates paragraphs line by line (`StaticLayout`), keeps headings with the following text, splits tall tables across pages with a repeated header row, shrinks wide spreadsheet tables, and scales images to fit.
- `SlidePdfRenderer` creates one page per slide at the original slide size and draws shapes, pictures (with cropping and flips), tables and text boxes with anchoring, bullets and shrink-to-fit.

`PdfSink` wraps `android.graphics.pdf.PdfDocument`. It applies a 4× internal coordinate scale, because laying out text at 11 px with hinting produces uneven glyph spacing, while laying it out at 44 units and scaling the canvas down keeps vector output with accurate spacing.

## Adding a format

1. Add an entry to `DocumentFormat` with extensions and MIME types.
2. Teach `FormatDetector` to recognise it by content if it has a signature.
3. Write a `DocumentReader<FlowDocument>` or `DocumentReader<SlideDeck>`, or a custom `PdfExporter` if neither model fits (like `ImagePdfExporter` and `SvgPdfExporter`).
4. Register it in `DefaultConverters`.
5. Add the MIME type to the share intent filter in `AndroidManifest.xml`.
6. Add a fixture and tests in `app/src/test` and an end-to-end case in `app/src/androidTest`.

`ConverterRegistryTest.defaultRegistryCoversEveryDeclaredFormat` fails if step 4 is forgotten.

## Engine evaluation

The plan asked for a comparison of rendering engines. These are the findings that led to the current design.

| Option | License | Verdict |
| --- | --- | --- |
| LibreOffice / LibreOfficeKit for Android (Collabora Office) | MPL-2.0 | Best fidelity, but adds roughly 150–200 MB per ABI and a complex native build. Rejected for v0.1. It remains the long-term option for pixel-accurate rendering, possibly as a separate flavor. |
| Apache POI | Apache-2.0 | Parses OOXML well, but its rendering (`XSLFSlide.draw`, etc.) depends on `java.awt`, which does not exist on Android. Its OOXML stack pulls in XMLBeans and StAX, about 15 MB. Rejected in favour of small dedicated readers. |
| PDFBox (and the Android port) | Apache-2.0 | Useful for editing existing PDFs. For creating PDFs, `android.graphics.pdf.PdfDocument` already does vector text and font embedding with no dependency. Not needed. |
| Android `PdfDocument` | Platform | Chosen as the PDF engine. |
| WebView printing | Platform | `PrintDocumentAdapter` needs hidden callback classes to write a file without UI, and layout depends on WebView timing. Fragile, and it would load a full browser engine for every conversion. Rejected. |
| jsoup | MIT | Chosen for HTML, EPUB chapters and Markdown output. Pure Java, tolerant parser. |
| commonmark-java | BSD-2-Clause | Chosen for Markdown (with GFM tables and strikethrough). |
| AndroidSVG | Apache-2.0 | Chosen for SVG files and SVG images inside documents. Renders as vectors onto the PDF canvas. |

Legacy binary Office formats (DOC/XLS/PPT) would need POI's HWPF/HSSF/HSLF parsers plus our own renderers. That is a lot of code for formats that current Office versions no longer produce by default, so they are detected and declined with an explanation.

## Errors and limits

All failures are `ConversionException` subclasses with a stable `code` (`unsupported/legacy_office`, `too_large`, `corrupted`, `drm`, …). The dialog shows a localised message plus a detail line with the app version and code, so bug reports stay useful even though release builds are minified.

Non-fatal problems are collected as `ConversionWarning`s and shown under the result: skipped charts or embedded objects, external images that were not loaded, images in unsupported formats (for example EMF), truncated spreadsheets, and hidden slides or sheets.

Limits live in `ConversionLimits`: input size, decompressed ZIP entry and archive size, compression ratio, number of ZIP entries, XML element count, pages, table cells and columns, image resolution, and number of shared files.

## Threading and cancellation

The conversion runs on `Dispatchers.Default`, with I/O on `Dispatchers.IO`. Readers and renderers call `ConversionContext.checkCancelled()` in their loops, so cancelling the job stops work within one paragraph, row or slide. Inputs are deleted in a `finally` block, and partial output is deleted on any failure or cancellation (covered by an instrumented test).

## Testing strategy

- **JVM unit tests** (`app/src/test`) cover format detection, encoding detection, archive safety limits, CSV parsing, number formats, and every reader against real fixtures generated with LibreOffice plus hand-made EPUB files.
- **Instrumented tests** (`app/src/androidTest`) convert every fixture on a device or emulator, open the result with `PdfRenderer`, check page counts and that pages are not blank. They also cover merging, cancellation, temporary file removal, the FileProvider grant, and the real `ShareReceiverActivity` for success, failure and text shares.
- Fixtures live in `app/src/sharedTest/fixtures` and are shared by both test types.
