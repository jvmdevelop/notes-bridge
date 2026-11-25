# Security policy

## Supported versions

Only the latest release receives security fixes.

| Version | Supported |
| --- | --- |
| 0.1.x | Yes |

## Reporting a vulnerability

Please do not open a public issue for security problems.

Report them privately through GitHub: go to the repository's **Security** tab and choose **Report a vulnerability** ([direct link](https://github.com/jvmdevelop/NotesBridge/security/advisories/new)). Include:

- the affected version,
- a description of the problem and its impact,
- steps or a sample file to reproduce it (a minimal crafted file is better than a real document).

You can expect an acknowledgement within 7 days. Once the problem is confirmed, a fix and an advisory will be published as soon as practical, and you will be credited unless you prefer otherwise.

## Scope

NotesBridge parses untrusted documents, so these are in scope:

- crashes, hangs or excessive memory use caused by a crafted file (for example ZIP bombs, deeply nested XML, huge images),
- reading files other than the shared one, or writing outside the app's storage,
- leaking a document to another app without the user choosing to share it,
- any way to make the app contact the network or execute content from a document.

Out of scope: problems that require a rooted or otherwise compromised device, and the behaviour of the app that receives the PDF.

## Hardening already in place

- No `INTERNET` permission and no storage permissions.
- Input size, ZIP entry count, decompressed size, compression ratio, XML element count, page and table cell limits (`ConversionLimits`).
- XML is parsed without DTD processing, so external entities are never resolved.
- Remote resources in HTML, Markdown, SVG and EPUB are never loaded.
- Macros, scripts and embedded objects are never executed.
- Files are shared through `FileProvider` with one-time read grants. The provider is not exported.
