# CalibreBoxNew — Android Calibre Manager

This repository contains an Android application that reads and manages a Calibre e-book library hosted on Dropbox. The project includes a Makefile wrapper for common build, copy, install and run tasks and uses the Gradle wrapper to build the Android app.

## Functionality

- Browse and search books
- Download books to local storage
- Share books directly from the app
- Send books to Kobo (via the Dropbox integration)
- Add additional libraries

## Quick summary

- Module: `app/`
- Build system: Gradle (use the included `gradlew`)
- Convenience wrapper: `Makefile` (top-level)
- Typical output after a successful build: `unsigned-app.apk` at repository root (copied from the module APK path by the Makefile)

## Requirements

- Android SDK and a JDK compatible with the Android Gradle Plugin
- Android 6.0+ (API 23+) by default — change `minSdk` in the module `build.gradle.kts` if you need a different minimum
- Kotlin compatible with the project (follow `build.gradle.kts` versions)
- A Dropbox account and an App Key to access Dropbox files

## Configuration (Dropbox keys and secrets)

- Add Dropbox credentials to your local environment or Gradle properties. A common approach:
  - Put non-committed secrets in `local.properties` or in your CI environment variables.
  - Example key name used by the project: `DROPBOX_APP_KEY` (see project Gradle scripts for exact usage).
- Keep secrets out of source control. Use `local.properties` or encrypted CI variables.

## Makefile overview

The Makefile is a small convenience layer over the Gradle wrapper. Key variables defined in the Makefile:

- `BUILD_TYPE` — Gradle assemble task (defaults to `assembleRelease`)
- `APK_PATH` — expected module output APK path
- `OUTPUT_NAME` — filename copied to the repository root after a successful build (`unsigned-app.apk`)

You can review or update these variables within the top of `Makefile` to match custom package names, build types (debug vs release) or different output paths.

## Common workflows

All commands below are executed from the project root.

- Build (default Make target also copies the APK):
  - `make` — runs the Makefile default (build + copy-apk)
  - `make build` — runs the Gradle assemble task defined by `BUILD_TYPE`
  - `make copy-apk` — copies the built APK from `APK_PATH` to `unsigned-app.apk` in the repo root

- Install to a connected device:
  - `make install` — runs `build`, `copy-apk`, then `adb install -r unsigned-app.apk`

- Build and launch:
  - `make run` — installs and launches the app using the `PACKAGE_NAME`/`MAIN_ACTIVITY` defined in the Makefile

- Clean:
  - `make clean` — runs `./gradlew clean` to remove build artifacts

- Lint:
  - `make lint` — runs Gradle lint tasks

- View logs:
  - `make logcat` — tails the device log for the app's PID (Makefile resolves the PID for the package)

Notes:
- If you prefer a debug APK for faster iteration, edit the Makefile:
  - Set `BUILD_TYPE = assembleDebug`
  - Set `APK_PATH = app/build/outputs/apk/debug/app-debug.apk`
- Ensure `gradlew` is executable: `chmod +x gradlew` if needed.

## APK output

By default the Makefile copies the module APK to the repository root with the name `unsigned-app.apk`. If you change build variant or flavor, update `APK_PATH` in the Makefile to the correct output location.

## Gradle and direct commands

You can use Gradle directly if you want more detailed output or to troubleshoot build issues:

- Make a debug build:
  - `./gradlew assembleDebug`
- Make a release build:
  - `./gradlew assembleRelease`
- Print a stacktrace on failure:
  - `./gradlew assembleDebug --stacktrace`

This is a hobby project, and is not affiliated or endorsed by Calibre.
