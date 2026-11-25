# Privacy

NotesBridge converts documents on your device. This page describes exactly what happens to a document from the moment you share it.

## Summary

- Documents are never uploaded. The app does not have the `INTERNET` permission, so Android does not let it open network connections.
- The app requests no runtime permissions and no storage access.
- Copies of your documents exist only in the app's private cache, and only for as long as needed.
- There is no analytics, crash reporting, advertising or account.

## What happens to a shared document

1. **Receiving.** The sending app grants NotesBridge temporary read access to one `content://` URI per file. NotesBridge reads the file through `ContentResolver`, and only that file.
2. **Copying.** The file is copied into the app's private cache (`cache/input/`), with a size limit of 100 MB. Other apps cannot read this directory.
3. **Converting.** The copy is parsed and rendered to PDF in memory. Only document data is read: macros, scripts, embedded programs and OLE objects are never executed. HTML, Markdown, SVG and EPUB content is never allowed to load remote resources, so a document cannot use NotesBridge to contact a server.
4. **Deleting the input.** The copy in `cache/input/` is deleted as soon as the conversion finishes, fails or is cancelled.
5. **Storing the PDF.** The PDF is written to `cache/output/`. It has to stay there for a while, because the app you send it to (for example Samsung Notes) reads it after NotesBridge has closed.
6. **Sharing the PDF.** The PDF is offered through Android's `FileProvider`. The receiving app gets read access to that one file only, and only through the intent you choose to send.
7. **Deleting the PDF.** PDFs older than one hour are deleted the next time NotesBridge runs, and by a background `JobScheduler` task scheduled after each conversion. Android may also clear the cache earlier when storage runs low.

If you tap **Save**, the PDF is written to the location you pick with the system file picker. That copy is yours and NotesBridge does not delete it.

## Data NotesBridge does not collect

- No document content, file names or metadata leave the device.
- No usage statistics, identifiers or logs are sent anywhere.
- Backups are disabled (`android:allowBackup="false"`, and the data extraction rules exclude everything), so cached files are not copied to cloud backups or to a new phone.

## Permissions

The release APK declares no dangerous or normal permissions of its own. The only entry in its permission list is `com.jvmd.notesbridge.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which AndroidX adds automatically to protect the app's internal broadcast receivers. It is not visible to users and grants nothing to other apps.

The manifest also contains a `<queries>` element. It lets the app check whether Samsung Notes and a PDF viewer are installed, so it can show or hide the corresponding buttons. It does not give access to other apps' data.

## Third parties

The app includes these open-source libraries, all of which run locally: AndroidX, Material Components, Kotlin coroutines, jsoup, commonmark-java and AndroidSVG. None of them is configured to make network requests, and the app could not make them anyway without the internet permission.

Once you send the PDF to another app, that app's privacy policy applies to the PDF.

## Questions

Open a [discussion](https://github.com/jvmdevelop/NotesBridge/discussions) or an issue. Security problems should be reported privately as described in [SECURITY.md](../SECURITY.md).
