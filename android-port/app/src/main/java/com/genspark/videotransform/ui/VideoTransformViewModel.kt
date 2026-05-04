package com.genspark.videotransform.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.genspark.videotransform.auth.YouTubeAuthActivity
import com.genspark.videotransform.data.AudioOptions
import com.genspark.videotransform.data.BackgroundType
import com.genspark.videotransform.data.CaptionOptions
import com.genspark.videotransform.data.CaptionPosition
import com.genspark.videotransform.data.CaptionStyle
import com.genspark.videotransform.data.ColorGradingOptions
import com.genspark.videotransform.data.ColorPreset
import com.genspark.videotransform.data.ExportQuality
import com.genspark.videotransform.data.PhonkFreezeOptions
import com.genspark.videotransform.data.ProcessOptions
import com.genspark.videotransform.data.PublishPayload
import com.genspark.videotransform.data.SecureSettings
import com.genspark.videotransform.data.SecureSettingsStore
import com.genspark.videotransform.data.SourceMode
import com.genspark.videotransform.data.TimestampRange
import com.genspark.videotransform.data.TransitionOptions
import com.genspark.videotransform.data.TransitionType
import com.genspark.videotransform.media.CookieExporter
import com.genspark.videotransform.media.Downloader
import com.genspark.videotransform.media.FacebookPublisher
import com.genspark.videotransform.media.FfmpegExecutor
import com.genspark.videotransform.media.FfmpegProcessor
import com.genspark.videotransform.media.GalleryExporter
import com.genspark.videotransform.media.TempFileManager
import com.genspark.videotransform.media.UriCopyHelper
import com.genspark.videotransform.media.YouTubePublisher
import com.genspark.videotransform.media.YtDlpInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoTransformViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val tempFileManager = TempFileManager(app)
    private val downloader = Downloader(app, tempFileManager)
    private val processor = FfmpegProcessor(app, tempFileManager)
    private val galleryExporter = GalleryExporter(app)
    private val settingsStore = SecureSettingsStore(app)
    private val youTubePublisher = YouTubePublisher()
    private val facebookPublisher = FacebookPublisher()

    var sourceMode by mutableStateOf(SourceMode.URL)
    var videoUrl by mutableStateOf("")
    var videoReferer by mutableStateOf("")
    var videoLocalPath by mutableStateOf<String?>(null)

    var timestampsText by mutableStateOf("00:00:00-00:00:10")
    var timestampsValid by mutableStateOf(true)

    var captionEnabled by mutableStateOf(false)
    var captionText by mutableStateOf("")
    var captionFontSize by mutableStateOf(56f)
    var captionColor by mutableStateOf("#FFFFFF")
    var captionPosition by mutableStateOf(CaptionPosition.BOTTOM)
    var captionStyle by mutableStateOf(CaptionStyle.NORMAL)

    var colorEnabled by mutableStateOf(false)
    var colorPreset by mutableStateOf(ColorPreset.CINEMATIC)
    var brightness by mutableStateOf(0f)
    var contrast by mutableStateOf(1f)
    var saturation by mutableStateOf(1f)

    var transitionEnabled by mutableStateOf(false)
    var transitionType by mutableStateOf(TransitionType.FADE)
    var transitionFrames by mutableStateOf(4f)

    var backgroundType by mutableStateOf(BackgroundType.BLUR)

    var audioEnabled by mutableStateOf(false)
    var audioSourceMode by mutableStateOf(SourceMode.URL)
    var audioUrl by mutableStateOf("")
    var audioLocalPath by mutableStateOf<String?>(null)
    var autoDuck by mutableStateOf(false)
    var duckLevel by mutableStateOf(30f)
    var vocalIsolation by mutableStateOf(false)
    var musicVolume by mutableStateOf(50f)
    var dialogueEnabled by mutableStateOf(true)

    var freezeEnabled by mutableStateOf(false)
    var freezeTimestamp by mutableStateOf("00:00:19")
    var phonkMusicEnabled by mutableStateOf(false)
    var phonkSourceMode by mutableStateOf(SourceMode.URL)
    var phonkUrl by mutableStateOf("")
    var phonkLocalPath by mutableStateOf<String?>(null)
    var phonkVolume by mutableStateOf(80f)
    var skullPath by mutableStateOf<String?>(null)

    var youtubeTitle by mutableStateOf("")
    var youtubeDescription by mutableStateOf("")
    var youtubePrivacy by mutableStateOf("private")
    var facebookTitle by mutableStateOf("")
    var facebookDescription by mutableStateOf("")

    var secureSettings by mutableStateOf(settingsStore.load())
    var progress by mutableStateOf(0)
    var progressMessage by mutableStateOf("Idle")
    var working by mutableStateOf(false)
    var previewPath by mutableStateOf<String?>(null)
    var fullExportPath by mutableStateOf<String?>(null)
    val logs = mutableStateListOf<String>()

    init {
        tempFileManager.cleanupLeftovers()
        refreshSettings()
        // CRITICAL: warm up the native libs OFF the UI thread, so first
        // FFmpeg/yt-dlp tap doesn't crash with FFmpegKitConfig static-init error.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { FfmpegExecutor.preload() }
            runCatching { YtDlpInstaller.install(app) }
        }
    }

    fun refreshSettings() { secureSettings = settingsStore.load() }

    fun saveSettings(settings: SecureSettings) {
        settingsStore.save(settings)
        secureSettings = settings
    }

    fun exportCookies(onDone: (String) -> Unit, onError: (String) -> Unit) =
        runTask(work = {
            val file = withContext(Dispatchers.IO) { CookieExporter.exportYoutubeCookies(app) }
            onDone(file.absolutePath)
        }, onError = onError)

    fun launchYouTubeAuth(activity: Activity) {
        activity.startActivity(Intent(activity, YouTubeAuthActivity::class.java))
    }

    fun attachVideoUri(uri: Uri, onError: (String) -> Unit) = runTask(work = {
        val file = UriCopyHelper.copyToCache(app, uri, "source_video.mp4")
        videoLocalPath = file.absolutePath
        sourceMode = SourceMode.GALLERY
        progress = 100; progressMessage = "Video loaded"
    }, onError = onError)

    fun attachAudioUri(uri: Uri, onError: (String) -> Unit) = runTask(work = {
        val file = UriCopyHelper.copyToCache(app, uri, "music_input.bin")
        audioLocalPath = file.absolutePath
        audioSourceMode = SourceMode.GALLERY
        progress = 100; progressMessage = "Audio loaded"
    }, onError = onError)

    fun attachPhonkUri(uri: Uri, onError: (String) -> Unit) = runTask(work = {
        val file = UriCopyHelper.copyToCache(app, uri, "phonk_input.bin")
        phonkLocalPath = file.absolutePath
        phonkSourceMode = SourceMode.GALLERY
        progress = 100; progressMessage = "Phonk audio loaded"
    }, onError = onError)

    fun attachSkullUri(uri: Uri, onError: (String) -> Unit) = runTask(work = {
        val file = UriCopyHelper.copyToCache(app, uri, "skull_overlay.png")
        skullPath = file.absolutePath
        progress = 100; progressMessage = "Skull image ready"
    }, onError = onError)

    fun downloadVideo(onError: (String) -> Unit) = runTask(work = {
        require(videoUrl.isNotBlank()) { "Video URL is required" }
        val file = downloader.resolveVideo(videoUrl, videoReferer) { p, msg ->
            progress = p; progressMessage = msg
        }
        videoLocalPath = file.absolutePath
        sourceMode = SourceMode.URL
    }, onError = onError)

    fun downloadAudio(onError: (String) -> Unit) = runTask(work = {
        require(audioUrl.isNotBlank()) { "Audio URL is required" }
        val file = downloader.resolveAudio(audioUrl) { p, msg ->
            progress = p; progressMessage = msg
        }
        audioLocalPath = file.absolutePath
        audioSourceMode = SourceMode.URL
    }, onError = onError)

    fun downloadPhonk(onError: (String) -> Unit) = runTask(work = {
        require(phonkUrl.isNotBlank()) { "Phonk URL is required" }
        val file = downloader.resolveAudio(phonkUrl) { p, msg ->
            progress = p; progressMessage = msg
        }
        phonkLocalPath = file.absolutePath
        phonkSourceMode = SourceMode.URL
    }, onError = onError)

    fun generatePreview(onError: (String) -> Unit) = process(ExportQuality.PREVIEW, onError)
    fun exportFull(onError: (String) -> Unit) = process(ExportQuality.FULL, onError)

    private fun process(quality: ExportQuality, onError: (String) -> Unit) = runTask(work = {
        val result = processor.process(buildOptions(), quality) { p, msg ->
            progress = p; progressMessage = msg
        }
        if (quality == ExportQuality.PREVIEW) previewPath = result.outputPath
        else fullExportPath = result.outputPath
    }, onError = onError)

    fun saveLatestToGallery(onDone: (String) -> Unit, onError: (String) -> Unit) = runTask(work = {
        val path = fullExportPath ?: previewPath ?: error("Generate preview or export first")
        val uri = galleryExporter.export(File(path), "video_transform_${System.currentTimeMillis()}.mp4")
        onDone(uri.toString())
    }, onError = onError)

    fun publishYouTube(onDone: (String) -> Unit, onError: (String) -> Unit) = runTask(work = {
        refreshSettings()
        val path = fullExportPath ?: previewPath ?: error("Generate or export the video first")
        val url = youTubePublisher.upload(
            secureSettings,
            PublishPayload(path, youtubeTitle.ifBlank { "Video Transform Export" },
                youtubeDescription, youtubePrivacy)
        ) { p, msg -> progress = p; progressMessage = msg }
        onDone(url)
    }, onError = onError)

    fun publishFacebook(onDone: (String) -> Unit, onError: (String) -> Unit) = runTask(work = {
        refreshSettings()
        val path = fullExportPath ?: previewPath ?: error("Generate or export the video first")
        val url = facebookPublisher.upload(
            secureSettings,
            PublishPayload(path,
                facebookTitle.ifBlank { youtubeTitle.ifBlank { "Video Transform Export" } },
                facebookDescription)
        ) { p, msg -> progress = p; progressMessage = msg }
        onDone(url)
    }, onError = onError)

    fun cleanupTempAfterExport(onDone: (() -> Unit)? = null) = runTask(work = {
        previewPath = null
        fullExportPath = null
        tempFileManager.cleanupLeftovers()
        onDone?.invoke()
    }, onError = {})

    fun parseTimestamps(): List<TimestampRange> {
        val regex = Regex("^\\d{2}:\\d{2}:\\d{2}-\\d{2}:\\d{2}:\\d{2}$")
        val rawLines = timestampsText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val ranges = rawLines.mapNotNull { line ->
            if (!regex.matches(line)) null
            else line.split("-").let { TimestampRange(it[0], it[1]) }
        }
        timestampsValid = rawLines.isNotEmpty() && ranges.size == rawLines.size
        return if (timestampsValid) ranges else emptyList()
    }

    private fun buildOptions(): ProcessOptions {
        val ranges = parseTimestamps()
        require(videoLocalPath != null) { "Load a source video first" }
        require(timestampsValid && ranges.isNotEmpty()) { "Timestamp format is invalid" }
        return ProcessOptions(
            sourcePath = videoLocalPath!!,
            timestamps = ranges,
            caption = CaptionOptions(
                enabled = captionEnabled, text = captionText, fontSize = captionFontSize,
                color = captionColor, position = captionPosition, style = captionStyle,
            ),
            colorGrading = ColorGradingOptions(
                enabled = colorEnabled, preset = colorPreset,
                brightness = brightness, contrast = contrast, saturation = saturation,
            ),
            transition = TransitionOptions(
                enabled = transitionEnabled, type = transitionType,
                durationFrames = transitionFrames.toInt(),
            ),
            background = backgroundType,
            audio = AudioOptions(
                enabled = audioEnabled && audioLocalPath != null,
                sourceMode = audioSourceMode, sourceUrl = audioUrl, sourcePath = audioLocalPath,
                autoDuck = autoDuck, duckLevel = duckLevel.toInt(),
                musicVolume = musicVolume.toInt(),
                dialogueEnabled = dialogueEnabled, vocalIsolation = vocalIsolation,
            ),
            phonkFreeze = PhonkFreezeOptions(
                enabled = freezeEnabled, timestamp = freezeTimestamp,
                musicEnabled = phonkMusicEnabled && phonkLocalPath != null,
                musicSourceMode = phonkSourceMode, musicUrl = phonkUrl, musicPath = phonkLocalPath,
                musicVolume = phonkVolume.toInt(),
                skullImagePath = skullPath,
            ),
        )
    }

    private fun runTask(work: suspend () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                working = true
                progress = 0
                progressMessage = "Working..."
                work()
                logs += progressMessage
            } catch (t: Throwable) {
                logs += (t.message ?: t.javaClass.simpleName)
                onError(t.message ?: "Unknown error")
            } finally {
                working = false
            }
        }
    }
}
