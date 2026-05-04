package com.genspark.videotransform.media

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Bundled yt-dlp lives in assets/bin/<abi>/yt-dlp. On first run we copy it
 * to filesDir/yt-dlp/yt-dlp and chmod 0755 — filesDir is exec-allowed on
 * all Android versions, while /data/app/lib is W^X / noexec on Android 10+.
 *
 * This is the FIX for the "Cannot run program /data/app/.../libytdlp.so" crash.
 */
object YtDlpInstaller {
    private const val TAG = "YtDlpInstaller"

    fun install(context: Context): File {
        val targetDir = File(context.filesDir, "yt-dlp").apply { mkdirs() }
        val target = File(targetDir, "yt-dlp")

        // Pick the right ABI binary
        val abi = pickAbi()
        val assetPath = "bin/$abi/yt-dlp"

        // Re-extract if missing or different size (lets us redeliver new builds)
        val sourceSize = runCatching {
            context.assets.openFd(assetPath).use { it.length }
        }.getOrElse {
            // openFd fails on compressed assets; fallback to streaming once
            -1L
        }
        if (target.exists() && sourceSize > 0 && target.length() == sourceSize) {
            ensureExec(target)
            return target
        }

        Log.i(TAG, "Extracting yt-dlp ($abi) from assets to ${target.absolutePath}")
        context.assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        ensureExec(target)
        return target
    }

    private fun ensureExec(file: File) {
        // file.setExecutable(true, false) works on filesDir.
        if (!file.canExecute()) {
            file.setReadable(true, false)
            file.setExecutable(true, false)
        }
        // Belt-and-braces chmod (some OEMs ignore setExecutable)
        runCatching {
            Runtime.getRuntime().exec(arrayOf("chmod", "0755", file.absolutePath)).waitFor()
        }
    }

    private fun pickAbi(): String {
        val abis = Build.SUPPORTED_ABIS
        if (abis.contains("arm64-v8a")) return "arm64-v8a"
        if (abis.contains("armeabi-v7a")) return "armeabi-v7a"
        // Fallback: first ABI that has a binary in assets
        throw IOException("Unsupported ABI: ${abis.joinToString()}")
    }
}
