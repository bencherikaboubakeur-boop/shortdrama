# Touki Short Drama

Native Android MVP (Kotlin) with a public GitHub movie catalog and a GitHub-based admin uploader.

## What is included
- Public movie catalog loaded from `catalog.json`
- Admin connection using a GitHub fine-grained personal access token
- Upload a video into `vids/` and publish its title/description into `catalog.json`
- Media3 player with play/pause and 10-second rewind/forward controls
- Local Watch Later list
- GitHub Actions workflow to build a debug APK

## Requirements
- Android Studio with JDK 17 support
- Android SDK Platform 36
- A public GitHub repository for this project

## Configure the repository
1. Create a **public** GitHub repository (for example `touki-short-drama`).
2. Upload the project files.
3. Keep `catalog.json` at the repository root and the `vids/` directory.
4. In the app, tap **ADMIN**.
5. Enter your GitHub owner, repository, branch (`main`) and a fine-grained personal access token.
6. Give the token access to this repository only, with **Contents: Read and write**.
7. Connect. Choose **Add & publish movie**, select a video, enter its title and description.

The app validates the token against GitHub. The token is held in app memory only and is not saved to preferences. Disconnect or close the app to clear it.

## Video file limitations
This starter uses GitHub's Contents API. The uploader intentionally rejects files larger than 90 MiB to leave room for Base64/JSON overhead; GitHub's Contents API has a 100 MB per-file limit. GitHub is not a video CDN, and raw file delivery can be slow or rate-limited. For a public production streaming service, move video files to a dedicated video host/CDN and keep only metadata/URLs in GitHub.

## Build locally
Open the folder in Android Studio, let Gradle sync, then choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Or from a terminal with Gradle 8.13 installed:
```powershell
gradle assembleDebug
```
APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
Push to `main`. The workflow builds the debug APK and attaches it to the workflow run as an artifact. This is a debug/test APK, not a Play Store release.

## Security notes
- Never put a GitHub token in source code, `gradle.properties`, GitHub Pages, or a public repository.
- Do not share your admin token.
- Only use videos you own or are licensed to distribute.
- The public repository means anyone can read the movie files and catalog.
