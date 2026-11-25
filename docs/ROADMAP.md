# Roadmap

This is a plan, not a promise. Priorities change with feedback, so open an issue or a discussion if something matters to you.

## Near term (0.2)

- **Test with Microsoft Office files.** The current fixtures come from LibreOffice. Collect a corpus of PPTX/DOCX/XLSX files saved by Microsoft Office and Google Docs, add them as fixtures, and fix what breaks.
- **Charts and SmartArt fallbacks.** Office stores fallback images or drawing parts for SmartArt (`drawingN.xml`) and sometimes for charts. Render those instead of skipping the object.
- **DOCX headers, footers and footnotes.** Render them at the top and bottom of each page, and footnotes at the end of the document.
- **Real-device testing with Samsung Notes** on several One UI versions, and documentation of how each version imports a shared PDF.
- **Progress for long documents** as a percentage instead of a page counter.

## Medium term

- **Spreadsheet formatting:** cell fills, borders, merged cells and column widths, plus fitting a sheet to the page width.
- **More DrawingML:** gradient fills, shadows, group rotation, more preset geometries, vertical text.
- **EMF/WMF pictures:** a small converter for the common subset, since older Office documents use them heavily.
- **TIFF:** a pure-Kotlin decoder for baseline TIFF (uncompressed, LZW, Deflate, PackBits), which covers most scanner output.
- **Page options:** choose paper size and orientation for re-flowed documents, and "fit to one page" for small spreadsheets.
- **Font coverage:** bundle or download-free fallback for documents that use fonts missing on the device.

## Long term

- **Higher-fidelity flavor** based on LibreOfficeKit for users who prefer exact rendering over app size. It would ship as a separate build.
- **Legacy binary formats** (DOC/XLS/PPT) if there is enough demand.
- **F-Droid release.** The app has no proprietary dependencies, so packaging for F-Droid is mostly a matter of reproducible builds and metadata.

## Not planned

- Cloud conversion or any feature that needs network access.
- Editing documents. NotesBridge converts; annotating happens in Samsung Notes or another PDF app.
