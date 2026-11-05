<div align="center">

<img src="docs/icon.png" width="96" alt="AnyGrab icon">

# AnyGrab

**Paste any link. Download it in any format. Straight to your phone.**

A real, working Android app — built and signed with the Android SDK, no cloud, no account, no tracking.

[**⬇ Download the APK**](https://github.com/technicalnihilist-lgtm/anygrab/releases/latest) · [Report an issue](https://github.com/technicalnihilist-lgtm/anygrab/issues)

</div>

---

## Screenshots

| Grab | Formats | Browse |
|:---:|:---:|:---:|
| <img src="docs/screenshots/1-grab.png" width="240"> | <img src="docs/screenshots/2-formats.png" width="240"> | <img src="docs/screenshots/3-browse.png" width="240"> |
| Paste a link, hit download | Pick Original / JPG / PNG / WEBP | Tap any file link on a page |

## What it does

- **Any URL in.** `https://…/file.pdf`, an image CDN link, a zip, an APK, a podcast mp3, a video file — paste it and it downloads.
- **Smarter than a browser.** The app does a lightweight ranged request to the server, follows redirects, reads `Content-Disposition` and `Content-Type`, and works out the correct file name and extension even when the URL has none (e.g. `…/download?id=42`).
- **Format control.** Keep the file exactly as served (**Original**), or re-encode images to **JPG**, **PNG** or **WEBP** after download — done fully on-device with Android's own codecs.
- **Browse mode.** A built-in WebView with a download listener: any file link you tap on a page is grabbed and saved.
- **Share-to-grab.** Share a link from Chrome, YouTube, WhatsApp — AnyGrab appears in the share sheet and prefills the URL.
- **Real downloads.** Uses the system `DownloadManager`: background downloads, progress, resume, notification on completion, and files land in `Downloads/AnyGrab/`.
- **Open, share, retry.** Tap a finished item to open it, long-press to share, retry failed ones.

Everything is offline-first: no analytics, no server of ours, no login.

## Install

1. Download `AnyGrab-1.0.0.apk` from the [releases page](https://github.com/technicalnihilist-lgtm/anygrab/releases/latest).
2. On the phone, allow *Install unknown apps* for your browser or file manager when prompted.
3. Install and open. Files are saved to `Downloads/AnyGrab/`.

**Requires Android 5.0 (API 21) or newer.** Targets Android 15 (API 35).

## How it works

| Piece | Job |
|---|---|
| `MainActivity` | Tabs, URL field, format chips, download list, browse WebView |
| `Net` | Ranged GET probe → follows redirects, parses `Content-Disposition` / `Content-Type`, guesses name, extension and size |
| `DownloadManager` | The actual transfer — system-managed, resumable, with notifications |
| `ImageConvert` + `Api29` | On-device re-encode to JPG/PNG/WEBP, published into `Downloads/AnyGrab` via MediaStore on Android 10+ |
| `DownloadsAdapter` | Live list with progress, status and error reasons |

The app is **framework-only** — no AndroidX, no Gradle, no third-party libraries. It compiles to a ~66 KB APK.

## Build it yourself

You need JDK 17, Android SDK platform 35 and build-tools 34.0.0.

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
./build.sh
```

`build.sh` runs the real toolchain directly: `aapt2 compile` → `aapt2 link` → `javac` → `d8` → `zipalign` → `apksigner`. It generates a local `keystore.jks` on first run and writes `AnyGrab-1.0.0.apk`.

## Known limits (honest ones)

- It downloads what a server actually serves. Pages that stream through DRM, or that only expose a player (not a file), are not supported — and won't be; that is deliberate.
- Format conversion is for images. Audio/video transcoding would need ffmpeg-class code and is on the roadmap, not in 1.0.0.
- Very large files are fine, but a single download is capped by free space on the device.

## License

MIT — see [LICENSE](LICENSE).
