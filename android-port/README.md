# VideoTransform — Android port

Native Android (Kotlin + Jetpack Compose) port of the VideoTransform pipeline.
Builds a debug APK automatically on every push to `main` via GitHub Actions.

## What's bundled

- **FFmpegKit full-GPL 6.0-2.LTS AAR** at `app/libs/ffmpeg-kit-full-gpl-6.0-2.LTS.aar` (≈71 MB) — contains native `.so` for `armeabi-v7a` (NEON) and `arm64-v8a`.
- **yt-dlp ARMv7 binary** at `app/src/main/assets/bin/armeabi-v7a/yt-dlp` (≈18 MB, ELF 32-bit).
- **yt-dlp ARM64 binary** at `app/src/main/assets/bin/arm64-v8a/yt-dlp` (≈35 MB, ELF aarch64).

The yt-dlp binaries live in `assets/` (not `jniLibs/`) so we can copy them to the app's
`filesDir` and `chmod 0755` them at first run — that's the only filesystem path
where Android's `noexec` mount won't block execution.

## Build it (GitHub Actions)

```
unzip VideoTransform-Android-ready-for-actions.zip
cd android-port
git init
git add .
git commit -m "VideoTransform Android"
git branch -M main
git remote add origin https://github.com/<you>/<repo>.git
git push -u origin main
```

Wait ≈3 minutes for the workflow `Build debug APK` to finish, then download the
artifact `VideoTransform-debug-apk` from the workflow run page. The artifact zip
contains the installable `app-debug.apk`.

## First run (in the app)

1. Open the **Settings** ⚙ sheet.
2. Paste your **YouTube OAuth2 Client ID + Secret** (Google Cloud Console → OAuth 2.0 client → "Android" type with package `com.genspark.videotransform`).
3. Paste your **Facebook Page ID + Page Access Token** if you want Reels publishing.
4. Hit **Save**. Settings are stored in `EncryptedSharedPreferences`.

## Runtime architecture

| Module | What it does |
|---|---|
| `media/YtDlpInstaller.kt` | Copies the right ABI's yt-dlp from `assets/` to `filesDir/yt-dlp/yt-dlp`, `chmod 0755`. |
| `media/Downloader.kt` | Spawns yt-dlp with cookies / OAuth bearer; falls back to OkHttp for direct/m3u8. |
| `media/FfmpegExecutor.kt` | Loads `FFmpegKit` and runs commands. |
| `media/FfmpegProcessor.kt` | Trims, joins (`xfade` transitions), 9:16 with `scale=1080:1920`, `boxblur=20` background, audio sidechain ducking, Phonk freeze. |
| `media/Caption.kt` | 5 caption styles drawn via `Canvas`/`Paint`. |
| `media/Publishers.kt` | YouTube resumable upload + Facebook Reels phased upload. |
| `auth/YouTubeAuthActivity.kt` | AppAuth OAuth2 flow with `videotransform://oauth2redirect`. |
| `data/SecureSettingsStore.kt` | `EncryptedSharedPreferences` for tokens/secrets. |
| `ui/Theme.kt` + `AppScreen.kt` | Dark Material3 theme with explicit `darkColorScheme` so all text is **readable white** on the `#0B0B0F` background. |

## Why Compose Material3 and not AppCompat
AppCompat's `Theme.Material3.*` styles aren't always present on the runtime — we apply the dark scheme
in Compose itself with `MaterialTheme(colorScheme = darkColorScheme(...))`, and use a plain
`android:Theme.Material.NoActionBar` as the activity theme. This is the fix for the "everything
is dark, no text visible" report from the previous build.

## Known requirements
- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`.
- Kotlin 2.0.21, AGP 8.5.2, Gradle 8.7.
- ABIs: `armeabi-v7a` and `arm64-v8a` only (x86/x86_64 not packaged — yt-dlp + FFmpegKit binaries are ARM-only here).
