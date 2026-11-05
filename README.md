<div align="center">

<img src="docs/icon.png" width="96" alt="AnyGrab icon">

# AnyGrab

**Paste any link — a file, a page, a stream — and save it as a file, an image, audio, video or an HTML page.**

A real, working Android app, built and signed with the Android SDK. No cloud, no account, no tracking.

[**⬇ Download the APK**](https://github.com/technicalnihilist-lgtm/anygrab/releases/latest) · [Report an issue](https://github.com/technicalnihilist-lgtm/anygrab/issues)

</div>

---

## Screenshots

| Grab | Any format | Browse + save page |
|:---:|:---:|:---:|
| <img src="docs/screenshots/1-grab.png" width="240"> | <img src="docs/screenshots/2-formats.png" width="240"> | <img src="docs/screenshots/3-browse.png" width="240"> |
| Paste a link, hit download | Original / JPG / PNG / WEBP / Audio / Video / HTML | Tap any file link, or save the whole page |

## What's new in 1.2.0 — the "always an HTML file" bug

Two real bugs, both fixed:

1. **A page link used to save the page itself.** Paste a normal web page URL with *Video* or *Audio* selected and AnyGrab downloaded the page's HTML — the conversion then had nothing to work on, so you were left with an `.html` file. Now, when the link is a web page, the app reads the page, finds the real media behind it and downloads **that** instead. If the page hides no media, it says so instead of saving junk.
2. **The video remux was broken.** `MediaExtractor.selectTrack()` was being called inside the read loop, and it resets that track to its first sample. Video conversion could stall or produce a bad file. Both tracks are now selected once, and samples are routed with `getSampleTrackIndex()`.

Also new:

- **HLS support.** `.m3u8` streams are downloaded properly: master playlist → best variant (or the audio rendition when you pick Audio) → every segment stitched, then remuxed to `.mp4` / `.m4a`. Encrypted (DRM) playlists are refused with a clear message.
- **Media sniffing from pages** covers `og:video`, `og:audio`, `twitter:player:stream`, `<video>` / `<audio>` / `<source>` tags, JSON-LD `contentUrl`, and raw media URLs inside scripts — including slash-escaped ones (`https:\/\/…`).
- **Retry works.** A failed download now retries its own URL instead of opening the repo page.

## What it does

- **Any URL in.** Direct files, image CDN links, zips, APKs, podcasts, video files — and normal web pages, where it digs out the media.
- **Smarter than a browser.** A ranged request, redirect following, `Content-Disposition` / `Content-Type` parsing, and a sensible file name even when the URL has no extension (`…/download?id=42`).
- **Seven output formats.** Original, JPG, PNG, WEBP, Audio, Video, HTML — all on-device.
- **Browse mode.** Built-in WebView with a download listener; any file link you tap is grabbed. **Save this page as HTML** captures the current page.
- **Share-to-grab.** Share a link from any app and AnyGrab prefills it.
- **Real downloads.** System `DownloadManager`: background, resumable, notification on completion, saved to `Downloads/AnyGrab/`.

## Output formats

| Choice | What you get | How |
|---|---|---|
| **Original** | The file exactly as the server sends it | `DownloadManager` |
| **JPG / PNG / WEBP** | Re-encoded image | `BitmapFactory` + `Bitmap.compress` |
| **Audio** | `.m4a` (AAC) or `.webm` (Opus/Vorbis) | `MediaExtractor` → `MediaMuxer`, no re-encode |
| **Video** | `.mp4` or `.webm` | `MediaExtractor` → `MediaMuxer`, lossless remux |
| **HTML** | A viewer page next to the file | Generated locally, zero network |

Audio and Video work on **direct media links, page links and `.m3u8` streams**.

## Install

1. Download `AnyGrab-1.2.0.apk` from the [releases page](https://github.com/technicalnihilist-lgtm/anygrab/releases/latest).
2. Allow *Install unknown apps* when prompted.
3. Install and open. Files land in `Downloads/AnyGrab/`.

Upgrading? Install straight over the old version — same signing key, your downloads stay.

**Requires Android 5.0 (API 21) or newer.** Targets Android 15 (API 35).

## How it works

| Piece | Job |
|---|---|
| `MainActivity` | Tabs, URL field, format chips, download list, browse WebView, page capture |
| `Net` | Ranged GET probe → redirects, `Content-Disposition` / `Content-Type`, name and size |
| `PageGrab` | Finds the media URL inside a web page (Open Graph, media tags, JSON-LD, raw URLs) |
| `HlsDownloader` | Playlist parsing, variant choice, segment stitching |
| `MediaConvert` | Audio extraction and video remux via `MediaExtractor` + `MediaMuxer` |
| `ImageConvert` + `Api29` | Image re-encode, published through MediaStore on Android 10+ |
| `HtmlWrap` | Viewer pages and browsed-page capture |
| `DownloadsAdapter` | Live list with progress, status and error reasons |

Framework-only — no AndroidX, no Gradle, no third-party libraries. ~78 KB APK.

## Build it yourself

JDK 17, Android SDK platform 35, build-tools 34.0.0.

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
./build.sh
```

`build.sh` drives the real toolchain: `aapt2 compile` → `aapt2 link` → `javac` → `d8` → `zipalign` → `apksigner`.

## Tests

`test/TestAll.java` runs the Android-free parts on a desktop JVM against a local HTTP server — playlist parsing, variant selection, segment stitching, DRM refusal, and page media sniffing (10 checks):

```bash
cd test && mkdir -p out
javac -d out src/android/content/Context.java \
  ../app/src/main/java/com/anygrab/downloader/Net.java \
  ../app/src/main/java/com/anygrab/downloader/HlsDownloader.java \
  ../app/src/main/java/com/anygrab/downloader/PageGrab.java TestAll.java
java -cp out TestAll
```

## Known limits (honest ones)

- **Player-only and DRM pages still cannot be saved.** YouTube and similar sites do not hand out a file to anyone without their permission flow; that needs a server-side extractor, not an on-device app. The app now tells you clearly instead of leaving an HTML file behind.
- **Audio output is `.m4a` / `.webm`, not `.mp3`** — Android ships no MP3 encoder.
- **Remux, not transcode.** Video keeps its original codec (H.264/HEVC → MP4, VP8/VP9/AV1 → WebM). Incompatible pairs are reported.
- **Live HLS** is captured from the point you press download until you stop it; it is not a continuous recorder.
- Page capture saves the DOM as it stands, so heavily scripted pages may not look identical offline.

## License

MIT — see [LICENSE](LICENSE).
