# Promotion plan

This is a working plan for introducing NotesBridge to people who would actually use it. **Nothing in this file has been published.** Every post below is a draft and needs the maintainer's explicit go-ahead before it goes out.

## Positioning

- **Who it is for:** Galaxy phone and tablet owners who annotate with the S Pen (students with lecture slides, people reviewing contracts or reports), and Android users who want an offline document-to-PDF converter.
- **The one-line pitch:** Share a PPTX, DOCX or XLSX, get a PDF, open it in Samsung Notes. On your device, no upload.
- **What makes it different:** offline (no internet permission at all), open source, tiny (under 2 MB), and it lives in the share sheet instead of being yet another app to open.
- **What to be upfront about:** layouts are approximate, charts and SmartArt are not rendered, legacy DOC/XLS/PPT are not supported, and the Samsung Notes hand-off has been tested only on an emulator so far. Saying this first earns trust and gets better bug reports.

## Before announcing anything

1. **Real-device check.** Test on at least one Galaxy device with Samsung Notes, record what happens after "Open in Samsung Notes" on that One UI version, and update the README. Announcing a Samsung Notes tool without having seen it work with Samsung Notes is the biggest credibility risk.
2. **Demo video (30–45 s, no voice-over needed).** One continuous screen recording: open a PPTX in a file manager → Share → Convert to PDF → Open in Samsung Notes → write on the slide with the S Pen. Record with the built-in screen recorder at 1080p, trim, and add short captions ("Share", "Convert", "Annotate"). Upload to the README (as a GIF, under 5 MB) and to YouTube as unlisted for embedding.
3. **GIF and screenshots.** A 6–8 second GIF of the share → PDF flow for the README header and social posts, plus three still screenshots (share sheet with NotesBridge, result dialog, annotated slide in Samsung Notes).
4. **Repository polish.** Topics set, social preview image (1280×640, app icon plus the one-line pitch), a pinned "Feedback wanted: send me files that convert badly" discussion, and "good first issue" labels on two or three small, well-described tasks.
5. **A sample file pack** (a short PPTX, DOCX and XLSX that show what converts well) linked from the README, so people can try the app in ten seconds.

## Channels and timing

Post in waves, a few days apart, so feedback from one wave improves the next.

| Wave | Channel | Notes |
| --- | --- | --- |
| 1 | r/samsung, r/GalaxyTab, r/galaxys24 (or the current flagship subreddit) | Closest to the target audience. Check each subreddit's self-promotion rules and use the weekly app or self-promo thread where one exists. Post as the developer, answer every comment. |
| 1 | r/androidapps | Allows developer posts with the correct flair. Follow the posting format in the sidebar. |
| 2 | r/opensource, r/fossdroid | Emphasise Apache-2.0, no trackers, no internet permission. r/fossdroid prefers apps on F-Droid, so mention F-Droid plans honestly. |
| 2 | X (Twitter) | Short thread with the GIF. Tag no one without a reason; reply to people who ask "how do I get PPTX into Samsung Notes". |
| 3 | Hacker News (Show HN) | Only after the app has had real-device testing and the first round of fixes. Weekday morning US Eastern time. Be present to answer questions for the first few hours. |
| 3 | Product Hunt | Optional. Needs a gallery (GIF, three screenshots), a maker comment and a launch day where the maintainer is available. |
| Ongoing | XDA Forums (Samsung device sections) | A thread in the relevant device's "Apps & Themes" or "Themes, Apps, and Mods" section, kept up to date with each release. |

Rules for every channel: read the community rules first, post once (no cross-posting the same text on the same day), disclose that you are the developer, never ask for upvotes, and do not use multiple accounts.

## Draft: Reddit (r/samsung / r/GalaxyTab)

**Title:** I made a small open-source app that converts PPTX/DOCX/XLSX to PDF on the phone so you can annotate them in Samsung Notes

**Body:**

> Samsung Notes is great for writing on slides with the S Pen, but it only imports PDFs and images. My lecture slides always arrived as PPTX, so every time I had to find a computer or upload the file to some converter website.
>
> So I wrote NotesBridge. It adds "Convert to PDF" to the share menu. Share a document, wait a second, tap "Open in Samsung Notes".
>
> - Works offline. The app has no internet permission, so your files can't leave the phone.
> - PPTX, DOCX, XLSX, ODT/ODS/ODP, RTF, EPUB, Markdown, HTML, TXT, CSV, images, SVG.
> - Several files at once become one PDF.
> - Text stays searchable in the PDF.
> - Under 2 MB, Apache-2.0, no ads, no tracking.
>
> To be upfront: it renders documents itself instead of using Office, so layouts are close but not identical, and charts/SmartArt are skipped (the app tells you when it skips something). Old .doc/.ppt files aren't supported.
>
> APK and source: https://github.com/jvmdevelop/notes-bridge
>
> I'd really like files that convert badly. If you can share a non-confidential example, open an issue and I'll fix what I can.

## Draft: Reddit (r/androidapps)

**Title:** [DEV] NotesBridge – offline document-to-PDF converter in the share sheet (open source, no internet permission)

**Body:**

> NotesBridge is a share target: share a PPTX, DOCX, XLSX, ODF, RTF, EPUB, Markdown, HTML, CSV or image file and it turns into a PDF on the device. Then you can open it in Samsung Notes, any PDF app, share it or save it.
>
> Why I built it: I wanted to annotate slides in Samsung Notes without uploading them to an online converter.
>
> Details for the curious:
> - No INTERNET permission, no storage permission, nothing runs in the background except a job that deletes temporary files after an hour.
> - Own lightweight readers for OOXML/ODF (Apache POI's renderer needs java.awt, which Android doesn't have), output via Android's PdfDocument, so text is real text with embedded fonts.
> - Multiple files → one PDF.
>
> Limitations: approximate layouts, no charts/SmartArt, no legacy .doc/.xls/.ppt.
>
> GitHub (APK in Releases): https://github.com/jvmdevelop/notes-bridge
>
> Feedback and sample files that break it are very welcome.

## Draft: X thread

1/ I wanted to write on PowerPoint slides with the S Pen in Samsung Notes. Samsung Notes only takes PDFs, so I built a tiny open-source converter that lives in Android's share sheet. [GIF]

2/ Share → Convert to PDF → Open in Samsung Notes. Works with PPTX, DOCX, XLSX, ODF, RTF, EPUB, Markdown, HTML, CSV and images. Several files become one PDF.

3/ It's fully offline. Not "we promise not to upload": the app has no internet permission at all. Under 2 MB, Apache-2.0.

4/ Honest caveats: layouts are approximate and charts are skipped. If a file converts badly, I'd love an example → https://github.com/jvmdevelop/notes-bridge

## Draft: Show HN

**Title:** Show HN: NotesBridge – Offline PPTX/DOCX/XLSX to PDF converter for Android's share sheet

**Text:**

> NotesBridge is an Android share target that converts office documents and a few other formats to PDF on the device, so they can be annotated in Samsung Notes (or any PDF app).
>
> The interesting constraint was doing it without a server and without a 200 MB native office suite. LibreOfficeKit for Android would give the best fidelity but adds roughly 150–200 MB per ABI. Apache POI parses OOXML but renders through java.awt, which Android lacks. So the app has its own small readers for OOXML and ODF that produce two intermediate models, a flowing document and a positioned slide deck, and two renderers that draw them with StaticLayout onto android.graphics.pdf.PdfDocument. Text stays vector and searchable, and fonts are embedded.
>
> Things that took more effort than expected: PowerPoint placeholder inheritance (slide → layout → master → theme), RTF code pages, Excel date formats, and keeping glyph spacing correct (text is laid out at 4× size and scaled down, because hinting at 11 px skews spacing).
>
> It has no INTERNET permission. Inputs are limited against ZIP bombs and huge XML, and DTD processing is off.
>
> Limitations: approximate layout, no charts/SmartArt, no legacy binary Office formats. The test corpus is LibreOffice-generated files so far, and I'm looking for real-world files that break it.
>
> Source and APK: https://github.com/jvmdevelop/notes-bridge

## Draft: Product Hunt

- **Name:** NotesBridge
- **Tagline (60 chars):** Office files to PDF in Android's share menu, fully offline
- **Description:** Share a PPTX, DOCX or XLSX on Android and get a PDF you can annotate in Samsung Notes. Conversion runs on the device; the app has no internet permission. Open source (Apache-2.0), under 2 MB, supports 20+ formats and merges several files into one PDF.
- **Gallery:** demo GIF, share sheet screenshot, result dialog, annotated slide in Samsung Notes, format table.
- **Maker comment:** the personal story (lecture slides + S Pen), what works well, what doesn't yet, and a request for sample files.
- **Topics:** Android, Productivity, Open Source, Privacy.

## Growing visibility on GitHub

- Keep the README's first screen focused: one sentence, the GIF, the install link.
- Topics: android, kotlin, samsung, samsung-notes, pdf, pptx, document-converter, open-source.
- Answer issues within a couple of days, and label them (`conversion`, `bug`, `enhancement`, `good first issue`, `help wanted`).
- Pin a discussion asking for files that convert badly, and thank people publicly when their file leads to a fix.
- Submit to curated lists once the app is stable: awesome-android-apps style lists and privacy-focused app directories. Check each list's inclusion criteria first.
- Publish on F-Droid (see the roadmap). It is the main discovery channel for privacy-minded Android users.

## Finding the first contributors

- Write two or three `good first issue` tickets with clear scope and pointers to the relevant files, for example "add the `star7` preset geometry", "map more Wingdings bullet characters", "add a fixture for a DOCX with footnotes".
- Label bigger, well-defined work as `help wanted`: EMF rendering, TIFF decoder, chart fallback images.
- Keep `CONTRIBUTING.md` short and accurate, review pull requests quickly, and explain decisions in `docs/ARCHITECTURE.md` so new contributors can follow them.

## Collecting feedback

- The error dialog shows a detail line with version and error code, and the issue templates ask for it.
- A "Conversion problem" issue template asks for the format, the producing app and a non-confidential sample.
- Keep a short list in a pinned discussion of the most common complaints and what is being done about them.
- Never ask users for confidential documents. Ask for minimal reproductions instead.

## Announcing new versions

For each release:

1. The GitHub release (generated by the release workflow) is the canonical announcement.
2. Update the XDA thread and reply in the original Reddit threads only if the release fixes something people asked about there. Don't repost.
3. Post on X with a one-line summary and a short GIF if there is something visual.
4. For larger releases (new formats, Samsung Notes improvements), consider one new Reddit post in the most relevant community, following its rules.

## Release announcement template

> **NotesBridge X.Y.Z**
>
> What's new: [2–3 user-visible changes].
> Fixed: [notable fixes, with thanks to reporters].
> Download: https://github.com/jvmdevelop/notes-bridge/releases/tag/vX.Y.Z
>
> Still missing: [the most requested thing that is not done yet]. Files that convert badly are always welcome as issues.
