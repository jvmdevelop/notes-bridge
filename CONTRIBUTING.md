# Contributing to NotesBridge

Thanks for helping. This project is small, so a few clear conventions keep it maintainable.

## Reporting a conversion problem

Conversion bugs are the most valuable reports. Please include:

- The file format and the app that created the file (for example "PPTX saved by PowerPoint 365").
- What is wrong in the PDF: missing text, wrong layout, missing images, crash, error message.
- The detail line from the error dialog (it starts with `NotesBridge 0.1.0 ·`), if there was one.
- If possible, a sample file that reproduces the problem. **Do not upload confidential documents.** A minimal file that shows the same problem is ideal.

Use the **Conversion problem** issue template.

## Proposing a change

1. For anything larger than a small fix, open an issue or discussion first so we can agree on the approach.
2. Fork the repository and create a branch from `main`.
3. Make the change with tests (see below).
4. Run the checks locally:

   ```bash
   ./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug
   ./gradlew connectedDebugAndroidTest   # needs a device or emulator
   ```

5. Add an entry under `## [Unreleased]` in `CHANGELOG.md` if users will notice the change.
6. Open a pull request and fill in the template.

## Code conventions

- Kotlin, formatted by ktlint through Spotless (`./gradlew spotlessApply`).
- **No comments in source code.** Use clear names and small functions instead. Explanations of design decisions belong in `docs/ARCHITECTURE.md`.
- No `TODO` placeholders and no stubs presented as working features. If a feature is partial, say so in `docs/CONVERSION_SUPPORT.md` and report skipped content through `ConversionWarning`.
- Readers must not depend on Android classes, so they stay testable on the JVM. Android-specific code belongs in `render/`, `io/FileManager`, `launch/` and the UI.
- Never swallow conversion errors. Throw a `ConversionException` subclass or record a `ConversionWarning`.
- Call `context.checkCancelled()` in loops over document content.
- Do not add permissions, network access or analytics. Pull requests that need the `INTERNET` permission will not be accepted.
- New dependencies must be open source under a license compatible with Apache-2.0, actively maintained, and worth their size. Explain the choice in the pull request and add them to `legal/THIRD_PARTY_NOTICES.md`.

## Tests

- Format readers: add a fixture to `app/src/sharedTest/fixtures` and a JVM test in `app/src/test`. Fixtures must be small (a few kilobytes is typical) and free of personal or copyrighted content.
- Rendering and Android integration: add a case to `app/src/androidTest`.
- A bug fix should come with a test that fails without the fix.

## Changelog and releases

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/). Contributors add entries under `Unreleased`; maintainers move them into a version section when releasing. The release workflow refuses to publish a version that has no changelog section. The process is described in [docs/BUILD.md](docs/BUILD.md#release-process-maintainers).

## Code of Conduct

By participating you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0.
