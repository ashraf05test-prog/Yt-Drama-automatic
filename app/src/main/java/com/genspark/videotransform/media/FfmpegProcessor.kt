package com.genspark.videotransform.media

import android.content.Context
import com.genspark.videotransform.data.AudioOptions
import com.genspark.videotransform.data.BackgroundType
import com.genspark.videotransform.data.ColorGradingOptions
import com.genspark.videotransform.data.ColorPreset
import com.genspark.videotransform.data.ExportQuality
import com.genspark.videotransform.data.PhonkFreezeOptions
import com.genspark.videotransform.data.ProcessOptions
import com.genspark.videotransform.data.ProcessResult
import com.genspark.videotransform.data.TransitionOptions
import com.genspark.videotransform.data.TransitionType
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FfmpegProcessor(
    private val context: Context,
    private val tempFileManager: TempFileManager,
) {
    suspend fun process(
        options: ProcessOptions,
        quality: ExportQuality,
        progress: (Int, String) -> Unit,
    ): ProcessResult {
        tempFileManager.root.mkdirs()
        val input = File(options.sourcePath)
        require(input.exists()) { "Source video missing on disk: ${input.absolutePath}" }
        progress(2, "Cutting clips...")

        val clipFiles = mutableListOf<File>()
        try {
            options.timestamps.forEachIndexed { index, range ->
                val clip = tempFileManager.file("clip_${System.currentTimeMillis()}_$index.mp4")
                val mustReencode = options.transition.enabled || index == 0
                val cmd = if (mustReencode) {
                    "-y -ss ${range.start} -to ${range.end} -i ${FfmpegExecutor.ff(input)} " +
                            "-c:v libx264 -preset ultrafast -crf 20 -c:a aac ${FfmpegExecutor.ff(clip)}"
                } else {
                    "-y -ss ${range.start} -to ${range.end} -i ${FfmpegExecutor.ff(input)} " +
                            "-c copy ${FfmpegExecutor.ff(clip)}"
                }
                FfmpegExecutor.exec(cmd) { _, _ -> }
                clipFiles += clip
                val pct = 5 + ((index + 1) * 20 / options.timestamps.size)
                progress(pct, "Cut clip ${index + 1}/${options.timestamps.size}")
            }

            val joined = if (options.transition.enabled && clipFiles.size > 1) {
                buildTransitionJoin(clipFiles, options.transition, progress)
            } else {
                simpleConcat(clipFiles)
            }

            val (srcW, srcH) = FfmpegExecutor.probeSize(joined)
            val duration = FfmpegExecutor.probeDuration(joined)
            val frameW = if (quality == ExportQuality.PREVIEW) 540 else 1080
            val frameH = if (quality == ExportQuality.PREVIEW) 960 else 1920
            val inner = frameW
            val crf = if (quality == ExportQuality.PREVIEW) 28 else 18
            val preset = if (quality == ExportQuality.PREVIEW) "ultrafast" else "medium"

            val colorChain = colorFilter(options.colorGrading)
            val colorPrefix = if (colorChain.isBlank()) "" else "$colorChain,"
            val ratio = if (srcH > 0) srcW.toDouble() / srcH.toDouble() else (16.0 / 9.0)
            // BUG FIX: exact 9:16 input → skip padding step
            val exactNineSixteen = abs(ratio - (9.0 / 16.0)) < 0.01

            val captionFile = options.caption.takeIf { it.enabled && it.text.isNotBlank() }?.let {
                CaptionBitmapRenderer.render(context, it, quality, tempFileManager)
            }
            val audioFile = resolveOptionalAudio(options.audio, progress)
            val phonkFile = resolveOptionalPhonk(options.phonkFreeze, progress)
            val skullFile = options.phonkFreeze.skullImagePath?.let { File(it) }?.takeIf { it.exists() }

            val freezeDuration = 4.0
            val peakSec = if (options.phonkFreeze.enabled)
                min(tsToSeconds(options.phonkFreeze.timestamp), duration - 0.1)
            else 0.0
            val freezeActive = options.phonkFreeze.enabled && peakSec > 0.5
            val outputDur = if (freezeActive) peakSec + freezeDuration else duration

            val fc = StringBuilder()

            if (options.background == BackgroundType.BLUR) {
                fc.append("[0:v]${colorPrefix}split[_v1][_v2];")
                fc.append("[_v1]scale=$frameW:$frameH:force_original_aspect_ratio=increase,")
                fc.append("crop=$frameW:$frameH,boxblur=20[_bg];")
                fc.append("[_v2]scale=$frameW:$frameH:force_original_aspect_ratio=decrease,")
                fc.append("pad=$frameW:$frameH:(ow-iw)/2:(oh-ih)/2[_fg];")
                fc.append("[_bg][_fg]overlay=(W-w)/2:(H-h)/2:shortest=1[_vbase]")
            } else if (exactNineSixteen) {
                // 9:16 input → skip padding entirely (the bug-fix)
                fc.append("[0:v]${colorPrefix}scale=$frameW:$frameH[_vbase]")
            } else if (srcH > srcW) {
                fc.append("[0:v]${colorPrefix}scale=$frameW:$frameH:force_original_aspect_ratio=decrease,")
                fc.append("pad=$frameW:$frameH:(ow-iw)/2:(oh-ih)/2:color=black[_vbase]")
            } else {
                fc.append("[0:v]${colorPrefix}scale=$inner:$inner:force_original_aspect_ratio=decrease,")
                fc.append("pad=$inner:$inner:(ow-iw)/2:(oh-ih)/2,")
                fc.append("pad=$frameW:$frameH:(ow-iw)/2:(oh-ih)/2:color=black[_vbase]")
            }

            var nxt = 1
            val skullIndex = if (freezeActive && skullFile != null) nxt++ else -1
            val captionIndex = if (captionFile != null) nxt++ else -1
            val bgAudioIndex = if (audioFile != null) nxt++ else -1
            val phonkIndex = if (phonkFile != null) nxt++ else -1

            var videoLabel = "_vbase"

            // Phonk Freeze chain (matches ffmpeg.ts logic)
            if (freezeActive) {
                val pt = "%.3f".format(Locale.US, peakSec)
                val skullMaxPx = (frameW * 0.55).toInt()
                val cx = "%.1f".format(Locale.US, frameW / 2.0)
                val cy = "%.1f".format(Locale.US, frameH / 2.0)
                val maxR = "hypot($cx,$cy)"
                val falloff = "max(0,(hypot(X-$cx,Y-$cy)/$maxR-0.3)/0.7)"
                val timeFactor = "min(0.8,max(0,t-$pt)/$freezeDuration)"
                val vigAlpha = "$falloff*$timeFactor*255"

                fc.append(";[_vbase]trim=end=$pt,setpts=PTS-STARTPTS,")
                fc.append("tpad=stop_mode=clone:stop_duration=$freezeDuration[_frozen]")
                fc.append(";nullsrc=size=${frameW}x${frameH}:rate=25,format=rgba,")
                fc.append("geq=r='0':g='0':b='0':a='$vigAlpha'[_vig]")
                fc.append(";[_frozen][_vig]overlay=0:0:shortest=1[_vignetted]")
                videoLabel = "_vignetted"

                if (skullIndex >= 0) {
                    val punch = "w='$skullMaxPx*(1.2-0.2*min(1,max(0,t-$pt)/0.2))':h=-1:eval=frame"
                    fc.append(";[$skullIndex:v]scale=$punch[_skull]")
                    fc.append(";[_vignetted][_skull]overlay=x='(main_w-overlay_w)/2':")
                    fc.append("y='(main_h-overlay_h)/2':enable='gte(t,$pt)'[_vwithskull]")
                    videoLabel = "_vwithskull"
                }
            }

            if (captionIndex >= 0) {
                fc.append(";[$videoLabel][$captionIndex:v]overlay=0:0[vout]")
            } else {
                fc.append(";[$videoLabel]copy[vout]")
            }

            // Audio mixing: dialogue + bg music + auto-duck + vocal iso + phonk
            val audioMix = mutableListOf<String>()
            if (options.audio.dialogueEnabled) {
                var mainAudio = "[0:a]"
                if (options.audio.vocalIsolation) {
                    fc.append(";[0:a]highpass=f=200,lowpass=f=3000[_dia]")
                    mainAudio = "[_dia]"
                }
                if (bgAudioIndex >= 0) {
                    if (options.audio.autoDuck) {
                        fc.append(";[$bgAudioIndex:a][0:a]sidechaincompress=")
                        fc.append("threshold=0.05:ratio=10:attack=5:release=200:makeup=2[_ducked]")
                        audioMix += mainAudio
                        audioMix += "[_ducked]"
                    } else {
                        fc.append(";[$bgAudioIndex:a]volume=${options.audio.musicVolume / 100.0}[_bg]")
                        audioMix += mainAudio
                        audioMix += "[_bg]"
                    }
                } else {
                    audioMix += mainAudio
                }
            } else if (bgAudioIndex >= 0) {
                fc.append(";[$bgAudioIndex:a]volume=${options.audio.musicVolume / 100.0}[_bg]")
                audioMix += "[_bg]"
            }
            if (phonkIndex >= 0) {
                fc.append(";[$phonkIndex:a]volume=${options.phonkFreeze.musicVolume / 100.0}[_ph]")
                audioMix += "[_ph]"
            }

            val audioOut = when {
                audioMix.isEmpty() -> null
                audioMix.size == 1 -> {
                    fc.append(";${audioMix.first()}anull[_aout]")
                    "_aout"
                }
                else -> {
                    fc.append(";${audioMix.joinToString("")}amix=inputs=${audioMix.size}:")
                    fc.append("duration=first:normalize=0[_aout]")
                    "_aout"
                }
            }

            val out = tempFileManager.file(
                "export_${quality.name.lowercase(Locale.US)}_${System.currentTimeMillis()}.mp4"
            )
            val args = mutableListOf("-y", "-i", FfmpegExecutor.ff(joined))
            if (skullFile != null) args += listOf("-loop", "1", "-i", FfmpegExecutor.ff(skullFile))
            if (captionFile != null) args += listOf("-loop", "1", "-i", FfmpegExecutor.ff(captionFile))
            if (audioFile != null) args += listOf("-i", FfmpegExecutor.ff(audioFile))
            if (phonkFile != null) args += listOf("-i", FfmpegExecutor.ff(phonkFile))
            args += listOf("-filter_complex", FfmpegExecutor.shellEscape(fc.toString()),
                "-map", "[vout]")
            if (audioOut == null) args += listOf("-an") else args += listOf("-map", "[$audioOut]")
            args += listOf("-t", "%.3f".format(Locale.US, outputDur),
                "-c:v", "libx264", "-preset", preset, "-crf", crf.toString(),
                "-c:a", "aac", "-b:a", "128k", FfmpegExecutor.ff(out))

            progress(45, "Encoding ${quality.name.lowercase(Locale.US)}...")
            FfmpegExecutor.exec(args.joinToString(" ")) { p, _ ->
                progress(max(45, p), "Encoding ${quality.name.lowercase(Locale.US)}...")
            }
            progress(100, "Processing complete")
            return ProcessResult(out.absolutePath, quality)
        } finally {
            clipFiles.forEach { runCatching { it.delete() } }
        }
    }

    private suspend fun resolveOptionalAudio(
        options: AudioOptions,
        progress: (Int, String) -> Unit,
    ): File? {
        if (!options.enabled || options.sourcePath == null) return null
        val src = File(options.sourcePath)
        return if (src.extension.lowercase(Locale.US) in listOf("mp3", "wav", "m4a", "aac", "ogg")) src
        else Downloader(context, tempFileManager).extractAudio(src, progress)
    }

    private suspend fun resolveOptionalPhonk(
        options: PhonkFreezeOptions,
        progress: (Int, String) -> Unit,
    ): File? {
        if (!options.enabled || !options.musicEnabled || options.musicPath == null) return null
        val src = File(options.musicPath)
        return if (src.extension.lowercase(Locale.US) in listOf("mp3", "wav", "m4a", "aac", "ogg")) src
        else Downloader(context, tempFileManager).extractAudio(src, progress)
    }

    private suspend fun simpleConcat(clipFiles: List<File>): File {
        val listFile = tempFileManager.file("concat_${System.currentTimeMillis()}.txt")
        listFile.writeText(clipFiles.joinToString("\n") { "file '${it.absolutePath}'" })
        val out = tempFileManager.file("joined_${System.currentTimeMillis()}.mp4")
        FfmpegExecutor.exec(
            "-y -f concat -safe 0 -i ${FfmpegExecutor.ff(listFile)} " +
                    "-c copy ${FfmpegExecutor.ff(out)}"
        ) { _, _ -> }
        return out
    }

    private suspend fun buildTransitionJoin(
        clipFiles: List<File>,
        transition: TransitionOptions,
        progress: (Int, String) -> Unit,
    ): File {
        val out = tempFileManager.file("joined_xfade_${System.currentTimeMillis()}.mp4")
        val durations = clipFiles.map { FfmpegExecutor.probeDuration(it) }
        val transitionSec = max(0.04, transition.durationFrames / 25.0)
        val transitionName = when (transition.type) {
            TransitionType.WHITE_FLASH -> "fadewhite"
            TransitionType.GLITCH -> "pixelize"
            TransitionType.FADE -> "fade"
        }
        val inputs = clipFiles.joinToString(" ") { "-i ${FfmpegExecutor.ff(it)}" }
        val fc = StringBuilder()
        fc.append("[0:v]format=yuv420p,settb=AVTB[v0];[0:a]aresample=async=1[a0]")
        for (i in 1 until clipFiles.size) {
            fc.append(";[$i:v]format=yuv420p,settb=AVTB[v$i];[$i:a]aresample=async=1[a$i]")
        }
        var currentV = "v0"
        var currentA = "a0"
        var cumulative = durations.first()
        for (i in 1 until clipFiles.size) {
            val offset = max(0.0, cumulative - transitionSec)
            val nv = "vx$i"
            val na = "ax$i"
            fc.append(";[$currentV][v$i]xfade=transition=$transitionName:")
            fc.append("duration=$transitionSec:offset=$offset[$nv]")
            fc.append(";[$currentA][a$i]acrossfade=d=$transitionSec:c1=tri:c2=tri[$na]")
            currentV = nv
            currentA = na
            cumulative += durations[i] - transitionSec
        }
        fc.append(";[$currentV]copy[vout];[$currentA]anull[aout]")
        progress(28, "Joining with transitions...")
        FfmpegExecutor.exec(
            "-y $inputs -filter_complex ${FfmpegExecutor.shellEscape(fc.toString())} " +
                    "-map [vout] -map [aout] -c:v libx264 -preset ultrafast -crf 20 " +
                    "-c:a aac ${FfmpegExecutor.ff(out)}"
        ) { _, _ -> }
        return out
    }

    private fun colorFilter(options: ColorGradingOptions): String = when {
        !options.enabled || options.preset == ColorPreset.NONE -> ""
        options.preset == ColorPreset.CINEMATIC ->
            "eq=brightness=0.02:contrast=1.1:saturation=0.85,colorbalance=rs=0.1:gs=0:bs=-0.1"
        options.preset == ColorPreset.COLD_BLUE ->
            "eq=brightness=0:contrast=1.05:saturation=1.1,colorbalance=rs=-0.1:gs=0:bs=0.15"
        options.preset == ColorPreset.VINTAGE ->
            "eq=brightness=-0.02:contrast=1.2:saturation=0.7,vignette=PI/4"
        options.preset == ColorPreset.HIGH_CONTRAST ->
            "eq=brightness=0:contrast=1.4:saturation=1.2"
        else ->
            "eq=brightness=${options.brightness}:contrast=${options.contrast}:saturation=${options.saturation}"
    }
}

private fun tsToSeconds(ts: String): Double {
    val parts = ts.split(":").map { it.toDoubleOrNull() ?: 0.0 }
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        else -> parts.firstOrNull() ?: 0.0
    }
}
