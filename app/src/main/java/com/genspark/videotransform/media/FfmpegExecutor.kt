package com.genspark.videotransform.media

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

object FfmpegExecutor {
    private const val TAG = "FfmpegExecutor"

    /**
     * Force-load FFmpegKit native libs early so static init doesn't crash on
     * a UI thread (matches the user's "FFmpegKitConfig" toast crash).
     */
    fun preload() {
        try {
            // Touch the class so JNI bindings load on a worker thread.
            Class.forName("com.arthenica.ffmpegkit.FFmpegKitConfig")
            Log.i(TAG, "FFmpegKit native libs loaded OK")
        } catch (t: Throwable) {
            Log.e(TAG, "FFmpegKit preload failed", t)
        }
    }

    suspend fun exec(command: String, progress: (Int, String) -> Unit): String =
        suspendCancellableCoroutine { cont ->
            val logs = StringBuilder()
            val session = FFmpegKit.executeAsync(
                command,
                { completed ->
                    val rc = completed.returnCode
                    if (ReturnCode.isSuccess(rc)) cont.resume(logs.toString())
                    else cont.resumeWithException(
                        IOException(
                            completed.failStackTrace
                                ?: completed.allLogsAsString
                                ?: "FFmpeg failed (rc=$rc)"
                        )
                    )
                },
                { log -> logs.appendLine(log.message) },
                { stats ->
                    val pct = min(95, (stats.time / 1000L).toInt())
                    progress(pct, "Encoding: ${pct}%")
                }
            )
            cont.invokeOnCancellation { runCatching { FFmpegKit.cancel(session.sessionId) } }
        }

    suspend fun probeDuration(file: File): Double {
        val session = FFprobeKit.execute(
            "-v quiet -print_format json -show_format ${shellEscape(file.absolutePath)}"
        )
        val text = session.allLogsAsString.orEmpty()
        val match = Regex("\"duration\"\\s*:\\s*\"([0-9.]+)\"").find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    suspend fun probeSize(file: File): Pair<Int, Int> {
        val session = FFprobeKit.execute(
            "-v quiet -print_format json -show_streams ${shellEscape(file.absolutePath)}"
        )
        val text = session.allLogsAsString.orEmpty()
        val w = Regex("\"width\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val h = Regex("\"height\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return w to h
    }

    fun shellEscape(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    fun ff(file: File): String = shellEscape(file.absolutePath)
}
