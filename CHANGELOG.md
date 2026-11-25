# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- "Open in Samsung Notes" now opens the PDF the same way as "Open with… → Samsung Notes" (`ACTION_VIEW`) instead of attaching it to a new note. Sending (`ACTION_SEND`) is used only if Samsung Notes cannot open PDFs directly.

## [0.1.0] - 2026-11-25

First public release. The app is functional but experimental: office documents are re-rendered by NotesBridge's own engine, so layouts are approximate.

### Added

- Share target "Convert to PDF" for single and multiple files, `Open with` for office formats, and conversion of shared text.
- Local conversion to PDF with searchable text and embedded fonts.
- Readers for PPTX, DOCX, XLSX, ODP, ODT, ODS, RTF, TXT, CSV/TSV, Markdown, HTML, EPUB, PNG, JPEG, WebP, GIF, BMP, HEIF, AVIF and SVG.
- Content-based format detection, with clear messages for legacy DOC/XLS/PPT, password-protected Office files, TIFF, PDF input and mismatched content.
- Result dialog with "Open in Samsung Notes" (when installed), "Open with…", "Share" and "Save".
- Merging of several shared files into one PDF.
- Cancellation, progress reporting and warnings for skipped content (charts, embedded objects, external images, unsupported image formats, truncated sheets, hidden slides or sheets).
- Safety limits for file size, ZIP bombs, XML size, pages and table cells.
- Automatic deletion of temporary files: inputs right after conversion, PDFs after one hour.
- English and Russian translations.

[Unreleased]: https://github.com/jvmdevelop/notes-bridge/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/jvmdevelop/notes-bridge/releases/tag/v0.1.0
