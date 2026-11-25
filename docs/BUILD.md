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

Releases are built by GitHub Actions (`.github/workflows/release.yml`) when a tag `vX.Y.Z` is pushed.

1. Update `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Move the entries under `## [Unreleased]` in `CHANGELOG.md` to a new `## [X.Y.Z] - YYYY-MM-DD` section.
3. Commit, then tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.

The workflow refuses to publish if the tag does not match `versionName`, if `CHANGELOG.md` has no section for the version, if the signing secrets are missing, or if any check fails (formatting, lint, unit tests, instrumented tests on an emulator). On success it attaches the signed APK and `SHA256SUMS.txt` to a GitHub Release whose notes contain the changelog section, the checksum, the signing certificate fingerprint and generated notes.

Repository secrets used by the workflow:

| Secret | Content |
| --- | --- |
| `NOTESBRIDGE_KEYSTORE_BASE64` | `base64 -w0 notesbridge-release.jks` |
| `NOTESBRIDGE_KEYSTORE_PASSWORD` | Keystore password |
| `NOTESBRIDGE_KEY_ALIAS` | Key alias |
| `NOTESBRIDGE_KEY_PASSWORD` | Key password |

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
