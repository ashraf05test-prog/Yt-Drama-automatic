package com.genspark.videotransform.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.min

class Downloader(
    private val context: Context,
    private val tempFileManager: TempFileManager,
) {
    private val client = OkHttpClient()

    suspend fun resolveVideo(url: String, referer: String, progress: (Int, String) -> Unit): File {
        val lower = url.lowercase(Locale.US)
        return when {
            looksLikeYtDlp(lower) -> downloadWithYtDlp(url, isAudio = false, progress = progress)
            lower.contains(".m3u8") -> downloadM3u8(url, referer, progress)
            else -> downloadDirect(url, referer, suffix = "mp4", progress = progress)
        }
    }

    suspend fun resolveAudio(url: String, progress: (Int, String) -> Unit): File {
        val lower = url.lowercase(Locale.US)
        return when {
            looksLikeYtDlp(lower) -> downloadWithYtDlp(url, isAudio = true, progress = progress)
            lower.contains(".m3u8") -> {
                val mp4 = downloadM3u8(url, "", progress)
                extractAudio(mp4, progress)
            }
            else -> {
                val direct = downloadDirect(url, "", suffix = guessSuffix(url, "mp3"), progress = progress)
                if (direct.extension.lowercase(Locale.US) in listOf("mp3", "wav", "m4a", "aac", "ogg")) direct
                else extractAudio(direct, progress)
            }
        }
    }

    suspend fun extractAudio(file: File, progress: (Int, String) -> Unit): File {
        val out = tempFileManager.file("audio_${System.currentTimeMillis()}.mp3")
        progress(10, "Extracting audio...")
        FfmpegExecutor.exec(
            "-y -i ${FfmpegExecutor.ff(file)} -vn -acodec mp3 -q:a 2 ${FfmpegExecutor.ff(out)}"
        ) { _, _ -> }
        progress(100, "Audio ready")
        return out
    }

    private fun looksLikeYtDlp(lower: String): Boolean = listOf(
        "youtube.com", "youtu.be", "facebook.com", "fb.watch",
        "x.com", "twitter.com", "tiktok.com", "instagram.com"
    ).any { lower.contains(it) }

    /**
     * yt-dlp is shipped inside the APK as `libytdlp.so` per ABI
     * (armeabi-v7a + arm64-v8a). PackageManager extracts the binary to
     * `applicationInfo.nativeLibraryDir/libytdlp.so` with executable
     * permissions automatically — no chmod, no /data/data writability issues.
     */
    private fun ytDlpBinary(): File {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val candidate = File(nativeDir, "libytdlp.so")
        if (!candidate.exists()) {
            throw IOException(
                "yt-dlp binary missing at ${candidate.absolutePath}. " +
                        "Device ABI ${android.os.Build.SUPPORTED_ABIS.firstOrNull()} may not be packaged."
            )
        }
        return candidate
    }

    private suspend fun downloadWithYtDlp(
        url: String,
        isAudio: Boolean,
        progress: (Int, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val bin = ytDlpBinary()
        val out = tempFileManager.file(
            if (isAudio) "dl_${System.currentTimeMillis()}.mp3"
            else "dl_${System.currentTimeMillis()}.mp4"
        )
        val cookies = File(context.filesDir, "yt-dlp/cookies.txt")
            .takeIf { it.exists() && it.length() > 0 }
        val args = mutableListOf(bin.absolutePath, "--no-playlist", "--newline")
        if (isAudio) {
            args += listOf("-x", "--audio-format", "mp3")
        } else {
            args += listOf(
                "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best[ext=webm]/best",
                "--merge-output-format", "mp4",
                "--recode-video", "mp4",
            )
        }
        if (cookies != null) args += listOf("--cookies", cookies.absolutePath)
        args += listOf("-o", out.absolutePath, url)

        val pb = ProcessBuilder(args)
        pb.redirectErrorStream(true)
        pb.environment()["HOME"] = context.filesDir.absolutePath
        pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
        val proc = pb.start()
        proc.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                Regex("(\\d+(?:\\.\\d+)?)%").find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { pct ->
                    progress(min(95, pct.toInt()), "Downloading: ${pct.toInt()}%")
                }
            }
        }
        val code = proc.waitFor()
        if (code != 0 || !out.exists() || out.length() == 0L) {
            throw IOException("yt-dlp failed (exit $code). Try the cookie login button first.")
        }
        progress(100, "Download complete")
        out
    }

    private suspend fun downloadM3u8(url: String, referer: String, progress: (Int, String) -> Unit): File {
        val out = tempFileManager.file("stream_${System.currentTimeMillis()}.mp4")
        progress(10, "Downloading stream...")
        val ref = if (referer.isBlank()) "" else "-referer ${FfmpegExecutor.shellEscape(referer)} "
        FfmpegExecutor.exec(
            "-y ${ref}-i ${FfmpegExecutor.shellEscape(url)} -c copy ${FfmpegExecutor.ff(out)}"
        ) { _, _ -> progress(60, "Downloading stream...") }
        progress(100, "Download complete")
        return out
    }

    private suspend fun downloadDirect(
        url: String,
        referer: String,
        suffix: String,
        progress: (Int, String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply {
            if (referer.isNotBlank()) header("Referer", referer)
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Direct download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response body")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            val out = tempFileManager.file("direct_${System.currentTimeMillis()}.$suffix")
            body.byteStream().use { input ->
                FileOutputStream(out).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var copied = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val pct = ((copied * 100) / total).toInt().coerceIn(0, 95)
                            progress(pct, "Downloading: $pct%")
                        }
                    }
                }
            }
            progress(100, "Download complete")
            out
        }
    }

    private fun guessSuffix(url: String, fallback: String): String {
        val ext = url.substringAfterLast('.', fallback).substringBefore('?').lowercase(Locale.US)
        return if (ext.length in 2..4) ext else fallback
    }
}
