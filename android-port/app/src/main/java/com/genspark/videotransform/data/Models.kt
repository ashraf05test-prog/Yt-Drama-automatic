package com.genspark.videotransform.data

import android.net.Uri

enum class SourceMode { GALLERY, URL }
enum class CaptionStyle { NORMAL, DRAMATIC, FIRE, MEME }
enum class CaptionPosition { TOP, BOTTOM }
enum class ColorPreset { NONE, CINEMATIC, COLD_BLUE, VINTAGE, HIGH_CONTRAST, CUSTOM }
enum class TransitionType { WHITE_FLASH, GLITCH, FADE }
enum class BackgroundType { BLACK, BLUR }
enum class ExportQuality { PREVIEW, FULL }

data class TimestampRange(val start: String, val end: String)

data class CaptionOptions(
    val enabled: Boolean = false,
    val text: String = "",
    val fontSize: Float = 56f,
    val color: String = "#FFFFFF",
    val position: CaptionPosition = CaptionPosition.BOTTOM,
    val style: CaptionStyle = CaptionStyle.NORMAL,
)

data class ColorGradingOptions(
    val enabled: Boolean = false,
    val preset: ColorPreset = ColorPreset.CINEMATIC,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
)

data class TransitionOptions(
    val enabled: Boolean = false,
    val type: TransitionType = TransitionType.FADE,
    val durationFrames: Int = 4,
)

data class AudioOptions(
    val enabled: Boolean = false,
    val sourceMode: SourceMode = SourceMode.URL,
    val sourceUrl: String = "",
    val pickedUri: Uri? = null,
    val sourcePath: String? = null,
    val autoDuck: Boolean = false,
    val duckLevel: Int = 30,
    val musicVolume: Int = 50,
    val dialogueEnabled: Boolean = true,
    val vocalIsolation: Boolean = false,
)

data class PhonkFreezeOptions(
    val enabled: Boolean = false,
    val timestamp: String = "00:00:19",
    val musicEnabled: Boolean = false,
    val musicSourceMode: SourceMode = SourceMode.URL,
    val musicUrl: String = "",
    val pickedMusicUri: Uri? = null,
    val musicPath: String? = null,
    val musicVolume: Int = 80,
    val skullImageUri: Uri? = null,
    val skullImagePath: String? = null,
)

data class ProcessOptions(
    val sourcePath: String,
    val timestamps: List<TimestampRange>,
    val caption: CaptionOptions = CaptionOptions(),
    val colorGrading: ColorGradingOptions = ColorGradingOptions(),
    val transition: TransitionOptions = TransitionOptions(),
    val background: BackgroundType = BackgroundType.BLUR,
    val audio: AudioOptions = AudioOptions(),
    val phonkFreeze: PhonkFreezeOptions = PhonkFreezeOptions(),
)

data class SecureSettings(
    val youtubeClientId: String = "",
    val youtubeClientSecret: String = "",
    val facebookPageId: String = "",
    val facebookPageAccessToken: String = "",
    val youtubeAccessToken: String = "",
    val youtubeRefreshToken: String = "",
    val youtubeTokenExpiry: Long = 0L,
)

data class PublishPayload(
    val filePath: String,
    val title: String,
    val description: String = "",
    val privacyStatus: String = "private",
)

data class ProcessResult(val outputPath: String, val quality: ExportQuality)
