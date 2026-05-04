# Video Transform — Android port

Native Android (Kotlin + Jetpack Compose) port of the **Drama Shorts Pro** web
project. Every requirement from the original spec is preserved 1:1.

## How to get the APK (Option A — GitHub Actions)

1. Create a new GitHub repo (public or private).
2. Unzip this project and push it to that repo's `main` branch:
   ```bash
   git init
   git add .
   git commit -m "Initial Video Transform port"
   git branch -M main
   git remote add origin <YOUR_REPO_URL>
   git push -u origin main
   ```
3. Open the **Actions** tab → click the green workflow run → scroll to
   **Artifacts** → download **VideoTransform-debug-apk**.
4. Inside the artifact zip is `app-debug.apk` — install it on Android.

The GitHub runner has 7 GB RAM, so the build finishes in ~3 minutes.

## What's bundled (no extra setup needed)

| Asset                                                  | Size  | Purpose                                                               |
| ------------------------------------------------------ | ----- | --------------------------------------------------------------------- |
| `app/libs/ffmpeg-kit-full-gpl-6.0-2.LTS.aar`           | 71 MB | FFmpeg + libx264 + libass + libfribidi for ARMv7 NEON + ARM64         |
| `app/src/main/jniLibs/armeabi-v7a/libytdlp.so`         | 18 MB | yt-dlp 2025.08.27 (last linux_armv7l build)                           |
| `app/src/main/jniLibs/arm64-v8a/libytdlp.so`           | 35 MB | yt-dlp 2026.02.04 (linux_aarch64)                                     |

> **Why `.so`?** Android's `PackageManager` automatically extracts native
> libraries to `applicationInfo.nativeLibraryDir/<libname>.so` with the
> executable bit set on install — so yt-dlp runs without any `chmod`.

## Feature parity vs. the web project

| Spec requirement                        | Implementation                                                                            |
| --------------------------------------- | ----------------------------------------------------------------------------------------- |
| Video source: gallery + URL + m3u8      | `media/Downloader.kt` — yt-dlp / `ffmpeg -i m3u8` / OkHttp direct                         |
| Timestamp cutting + multi-clip join     | `media/FfmpegProcessor.kt` PASS 1 + 2                                                     |
| 9:16 conversion + exact-9:16 bug-fix    | `abs((srcW/srcH) - (9.0/16.0)) < 0.01` → straight `scale=1080:1920`, no padding           |
| Color grading presets (5 presets)       | `cinematic / cold_blue / vintage / high_contrast / custom` — same `eq + colorbalance` filter strings |
| Canvas background: blur or black        | `BackgroundType.BLUR` (boxblur=20) / `BLACK`                                              |
| Caption overlay (4 styles)              | `media/Caption.kt` (Canvas/Paint) + FFmpeg overlay — replaces ImageMagick                 |
| Transitions: white_flash/glitch/fade    | `xfade` filter (`fadewhite` / `pixelize` / `fade`)                                        |
| Background music + auto-ducking         | `sidechaincompress=threshold=0.05:ratio=10:attack=5:release=200:makeup=2`                 |
| Vocal isolation                         | `highpass=200,lowpass=3000`                                                               |
| Phonk Freeze: freeze + vignette + skull | `trim → setpts → tpad clone → geq vignette α → skull punch 120%→100%`                     |
| Export to gallery (preview + full)      | `MediaStore` (`Movies/VideoTransform/`) — 540×960 preview / 1080×1920 full                |
| YouTube upload (Data API v3)            | `media/Publishers.kt::YouTubePublisher` — resumable upload                                |
| Facebook Reels upload (Graph v19.0)     | `media/Publishers.kt::FacebookPublisher` — start / binary / finish phases                 |
| YouTube Cookie Login                    | `auth` button → WebView `accounts.google.com` → `CookieManager` → Netscape `cookies.txt`  |
| YouTube OAuth2                          | `auth/YouTubeAuthActivity.kt` — AppAuth Authorization Code flow                           |
| Settings (encrypted)                    | `EncryptedSharedPreferences` — Client ID/Secret + Page ID/Token                           |
| Temp cleanup                            | All temp files in `cacheDir`; cleaned on app start + after export                         |
| UI: dark, step-by-step InShot-style     | 7 numbered Compose cards in `ui/AppScreen.kt`                                             |

## Build settings

- `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`
- ABIs: `armeabi-v7a` + `arm64-v8a`
- Kotlin 2.0.21, AGP 8.5.2, Jetpack Compose 1.7.3, Material 3 1.3.0
- FFmpegKit Full GPL 6.0-2.LTS (loaded as local AAR — Maven Central removed it
  after the project was retired; we bundle it)
- AppAuth 0.11.1, OkHttp 4.12.0, Media3 ExoPlayer 1.4.1

## Project layout

```
app/
├── build.gradle.kts                  ← Compose + FFmpegKit + AppAuth + Media3
├── libs/
│   └── ffmpeg-kit-full-gpl-6.0-2.LTS.aar
└── src/main/
    ├── AndroidManifest.xml
    ├── jniLibs/
    │   ├── armeabi-v7a/libytdlp.so   ← yt-dlp ARMv7
    │   └── arm64-v8a/libytdlp.so     ← yt-dlp ARM64
    ├── res/values/{strings,themes}.xml
    └── java/com/genspark/videotransform/
        ├── MainActivity.kt
        ├── auth/YouTubeAuthActivity.kt
        ├── data/{Models,SecureSettingsStore}.kt
        ├── media/
        │   ├── TempFiles.kt
        │   ├── Cookies.kt
        │   ├── Downloader.kt
        │   ├── Caption.kt
        │   ├── FfmpegExecutor.kt
        │   ├── FfmpegProcessor.kt
        │   └── Publishers.kt
        └── ui/{VideoTransformViewModel,AppScreen}.kt
.github/workflows/build-apk.yml       ← builds APK on push
```

## After installing the APK

1. Open **Settings** (gear icon) → enter YouTube Client ID/Secret + Facebook
   Page ID/Token (encrypted on save).
2. **Optional** — click the cookie icon → log in to Google → save cookies.txt
   for yt-dlp downloads of age-restricted/region-locked videos.
3. Walk through Steps 1–7 in the UI.
