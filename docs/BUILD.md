# Building NotesBridge

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 25. The JetBrains Runtime bundled with Android Studio works. Gradle's daemon toolchain is pinned to 25 in `gradle/gradle-daemon-jvm.properties`. |
| Android SDK | Platform 37, build tools 36 or newer |
| Gradle | Provided by the wrapper (9.6) |
| Android Gradle Plugin | 9.5 (Kotlin support is built in) |

Android Studio picks all of this up automatically. On the command line, point `JAVA_HOME` at a JDK 25 and either set `ANDROID_HOME` or create `local.properties` with `sdk.dir=/path/to/Android/Sdk`.

## Common tasks

```bash
./gradlew assembleDebug                # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest            # JVM unit tests
./gradlew connectedDebugAndroidTest    # instrumented tests on a connected device or emulator
./gradlew lintDebug                    # Android lint, fails on any error
./gradlew spotlessCheck                # ktlint formatting check
./gradlew spotlessApply                # fix formatting
./gradlew assembleRelease              # release APK, minified with R8
```

CI runs `spotlessCheck lintDebug testDebugUnitTest assembleDebug assembleRelease`, plus the instrumented tests on an emulator. Run the same before opening a pull request.

## Release signing

The release build is signed only when all four environment variables are set:

| Variable | Meaning |
| --- | --- |
| `NOTESBRIDGE_KEYSTORE_PATH` | Path to the keystore file |
| `NOTESBRIDGE_KEYSTORE_PASSWORD` | Keystore password |
| `NOTESBRIDGE_KEY_ALIAS` | Key alias |
| `NOTESBRIDGE_KEY_PASSWORD` | Key password |

Without them, `assembleRelease` produces `app-release-unsigned.apk`. No keystore or password is ever stored in the repository, and `*.jks`/`*.keystore` are ignored by git.

To sign locally with your own key:

```bash
keytool -genkeypair -keystore my-release.jks -storetype PKCS12 -alias notesbridge \
  -keyalg RSA -keysize 4096 -validity 10000
export NOTESBRIDGE_KEYSTORE_PATH=$PWD/my-release.jks
export NOTESBRIDGE_KEYSTORE_PASSWORD=...
export NOTESBRIDGE_KEY_ALIAS=notesbridge
export NOTESBRIDGE_KEY_PASSWORD=...
./gradlew assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

An APK signed with a different key cannot be installed over the official release; uninstall the official build first.

## Release process (maintainers)

Releases are tagged `vX.Y.Z`. The release workflow (`.github/workflows/release.yml`) supports two signing modes and picks one automatically.

Before either mode:

1. Update `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Move the entries under `## [Unreleased]` in `CHANGELOG.md` to a new `## [X.Y.Z] - YYYY-MM-DD` section.
3. Commit, push and wait until CI on `main` is green.

### Locally signed releases (current process)

The signing key stays on the maintainer's machine and is not stored on GitHub.

```bash
set -a; . ~/.config/notesbridge/signing.env; set +a
./gradlew clean assembleRelease
cp app/build/outputs/apk/release/app-release.apk NotesBridge-X.Y.Z.apk
apksigner verify --print-certs NotesBridge-X.Y.Z.apk
sha256sum NotesBridge-X.Y.Z.apk > SHA256SUMS.txt
gh release create vX.Y.Z NotesBridge-X.Y.Z.apk SHA256SUMS.txt \
  --target "$(git rev-parse HEAD)" --title "NotesBridge X.Y.Z" --notes-file release-notes.md --generate-notes
```

Creating the release also creates the tag, which starts the release workflow. Without signing secrets it runs every check on the tagged commit (formatting, lint, unit tests, instrumented tests), then downloads the published APK and fails unless the checksum matches `SHA256SUMS.txt` and the APK is signed with the official certificate. A release can be re-verified at any time with **Actions → Release → Run workflow** and its tag.

### CI-signed releases (optional)

If the four repository secrets below are set, pushing a tag (`git tag vX.Y.Z && git push origin vX.Y.Z`) is enough: the workflow runs all checks, builds and signs the APK, verifies the signature, and publishes the release with `SHA256SUMS.txt`, the changelog section, the certificate fingerprint and generated notes. Nothing is published if any check fails.

| Secret | Content |
| --- | --- |
| `NOTESBRIDGE_KEYSTORE_BASE64` | `base64 -w0 notesbridge-release.jks` |
| `NOTESBRIDGE_KEYSTORE_PASSWORD` | Keystore password |
| `NOTESBRIDGE_KEY_ALIAS` | Key alias |
| `NOTESBRIDGE_KEY_PASSWORD` | Key password |

In both modes the workflow fails if the tag does not match `versionName` or `CHANGELOG.md` has no section for the version.

## Test fixtures

Test documents live in `app/src/sharedTest/fixtures` and are used by both unit and instrumented tests. The office files were generated with LibreOffice from the flat XML sources in the same directory (`sample.fodt`, `sample.fods`, `sample.fodp`):

```bash
cd app/src/sharedTest/fixtures
soffice --headless --convert-to docx sample.fodt
soffice --headless --convert-to odt sample.fodt
soffice --headless --convert-to rtf sample.fodt
soffice --headless --convert-to html sample.fodt
soffice --headless --convert-to xlsx sample.fods
soffice --headless --convert-to ods sample.fods
soffice --headless --convert-to csv sample.fods
soffice --headless --convert-to pptx sample.fodp
soffice --headless --convert-to odp sample.fodp
soffice --headless --convert-to doc sample.fodt
```

The EPUB fixtures were assembled by hand with Python's `zipfile` module (LibreOffice's EPUB export was not available on the machine used). Note that LibreOffice rewrites some styles when it re-saves a document, which is why some test expectations (for example the 44 pt slide title) differ from the flat XML sources.
