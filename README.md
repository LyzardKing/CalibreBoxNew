# CalibreBoxNew

CalibreBoxNew is an Android application (Android Studio / Gradle) that lets users sign in with Dropbox, select a Calibre library, and browse or download books to their mobile device. The repository contains the Android app and an example SQL schema used by the project.

## Prerequisites

- Android Studio (recommended) or JDK + Gradle
- Android SDK (matching project requirements)

## Build

From the project root you can build using the provided `Makefile` (the default `make` target runs a build and copies the APK to the repository root):

```bash
# build APK (default target runs build + copy-apk)
make
# or explicitly
make build
```

To run from Android Studio: open the project and use the usual Run configuration.

## Install on device

Use the `Makefile` targets to install or run the app on a connected device:

```bash
# install built APK to a connected device
make install
# install and launch the app
make run
```

Additional useful targets:

```bash
# show available Makefile targets
make help
# clean project
make clean
```

Core features:

- **Dropbox sign-in:** Authenticate via Dropbox to access remote libraries.
- **Calibre library selection:** Pick a Calibre library stored in your Dropbox.
- **Browse & download:** View book metadata and download books for offline use on mobile.

## Notes for contributors

- Use Android Studio for editing and running the app.
- Keep Gradle and plugin versions aligned with the root `build.gradle.kts` and `gradle.properties`.