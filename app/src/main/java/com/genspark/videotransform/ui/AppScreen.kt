package com.genspark.videotransform.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.genspark.videotransform.data.BackgroundType
import com.genspark.videotransform.data.CaptionPosition
import com.genspark.videotransform.data.CaptionStyle
import com.genspark.videotransform.data.ColorPreset
import com.genspark.videotransform.data.SecureSettings
import com.genspark.videotransform.data.SourceMode
import com.genspark.videotransform.data.TransitionType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTransformScreen(vm: VideoTransformViewModel) {
    val context = LocalContext.current
    val activity = context as Activity
    var showSettings by remember { mutableStateOf(false) }
    var showCookieLogin by remember { mutableStateOf(false) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.attachVideoUri(it) { msg -> toast(context, msg) } }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.attachAudioUri(it) { msg -> toast(context, msg) } }
    }
    val pickPhonk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.attachPhonkUri(it) { msg -> toast(context, msg) } }
    }
    val pickSkull = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.attachSkullUri(it) { msg -> toast(context, msg) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Transform", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showCookieLogin = true }) {
                        Icon(Icons.Default.Web, contentDescription = "YouTube cookie login")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0F))
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Step-by-step InShot-style port of the Drama Shorts Pro web flow.",
                color = Color(0xFFB4B8C5),
            )

            StepCard(1, "Video source") {
                ChoiceRow(vm.sourceMode == SourceMode.URL, "URL / m3u8 / YouTube") { vm.sourceMode = SourceMode.URL }
                ChoiceRow(vm.sourceMode == SourceMode.GALLERY, "Gallery") { vm.sourceMode = SourceMode.GALLERY }
                Spacer(Modifier.height(8.dp))
                if (vm.sourceMode == SourceMode.URL) {
                    OutlinedTextField(value = vm.videoUrl, onValueChange = { vm.videoUrl = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Video URL") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = vm.videoReferer, onValueChange = { vm.videoReferer = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Referer (optional)") })
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.downloadVideo { msg -> toast(context, msg) } },
                        enabled = !vm.working) { Text("Load source") }
                } else {
                    OutlinedButton(onClick = { pickVideo.launch(arrayOf("video/*")) },
                        enabled = !vm.working) { Text("Pick video from gallery") }
                }
                vm.videoLocalPath?.let { LabeledPath("Ready", it) }
            }

            StepCard(2, "Timestamps + clip join") {
                OutlinedTextField(
                    value = vm.timestampsText,
                    onValueChange = { vm.timestampsText = it; vm.parseTimestamps() },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    label = { Text("One HH:MM:SS-HH:MM:SS range per line") },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (vm.timestampsValid) "Format valid" else "Invalid timestamp format",
                    color = if (vm.timestampsValid) Color(0xFF81C784) else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow("Transitions", vm.transitionEnabled) { vm.transitionEnabled = it }
                if (vm.transitionEnabled) {
                    EnumSelector(TransitionType.entries, vm.transitionType, { it.name.lowercase() }) {
                        vm.transitionType = it
                    }
                    SliderRow("Duration frames", vm.transitionFrames, 2f..8f) { vm.transitionFrames = it }
                }
            }

            StepCard(3, "9:16 + captions + grading") {
                ToggleRow("Caption overlay", vm.captionEnabled) { vm.captionEnabled = it }
                if (vm.captionEnabled) {
                    OutlinedTextField(value = vm.captionText, onValueChange = { vm.captionText = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Caption text") })
                    SliderRow("Font size", vm.captionFontSize, 24f..90f) { vm.captionFontSize = it }
                    OutlinedTextField(value = vm.captionColor, onValueChange = { vm.captionColor = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Hex color") })
                    EnumSelector(CaptionPosition.entries, vm.captionPosition, { it.name.lowercase() }) {
                        vm.captionPosition = it
                    }
                    EnumSelector(CaptionStyle.entries, vm.captionStyle, { it.name.lowercase() }) {
                        vm.captionStyle = it
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Color grading", vm.colorEnabled) { vm.colorEnabled = it }
                if (vm.colorEnabled) {
                    EnumSelector(ColorPreset.entries, vm.colorPreset, { it.name.lowercase() }) {
                        vm.colorPreset = it
                    }
                    if (vm.colorPreset == ColorPreset.CUSTOM) {
                        SliderRow("Brightness", vm.brightness, -1f..1f) { vm.brightness = it }
                        SliderRow("Contrast", vm.contrast, 0.5f..2f) { vm.contrast = it }
                        SliderRow("Saturation", vm.saturation, 0.5f..2f) { vm.saturation = it }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Canvas background")
                EnumSelector(BackgroundType.entries, vm.backgroundType, { it.name.lowercase() }) {
                    vm.backgroundType = it
                }
            }

            StepCard(4, "Music + ducking + vocal isolation") {
                ToggleRow("Background music", vm.audioEnabled) { vm.audioEnabled = it }
                if (vm.audioEnabled) {
                    ChoiceRow(vm.audioSourceMode == SourceMode.URL, "URL") { vm.audioSourceMode = SourceMode.URL }
                    ChoiceRow(vm.audioSourceMode == SourceMode.GALLERY, "Gallery") { vm.audioSourceMode = SourceMode.GALLERY }
                    Spacer(Modifier.height(8.dp))
                    if (vm.audioSourceMode == SourceMode.URL) {
                        OutlinedTextField(value = vm.audioUrl, onValueChange = { vm.audioUrl = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Audio URL") })
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.downloadAudio { msg -> toast(context, msg) } },
                            enabled = !vm.working) { Text("Fetch audio") }
                    } else {
                        OutlinedButton(onClick = { pickAudio.launch(arrayOf("audio/*", "video/*")) },
                            enabled = !vm.working) { Text("Pick audio/video file") }
                    }
                }
                ToggleRow("Auto ducking", vm.autoDuck) { vm.autoDuck = it }
                if (vm.autoDuck) SliderRow("Duck level", vm.duckLevel, 10f..90f) { vm.duckLevel = it }
                ToggleRow("Keep source dialogue", vm.dialogueEnabled) { vm.dialogueEnabled = it }
                ToggleRow("Vocal isolation", vm.vocalIsolation) { vm.vocalIsolation = it }
                SliderRow("Music volume", vm.musicVolume, 0f..100f) { vm.musicVolume = it }
                vm.audioLocalPath?.let { LabeledPath("Audio ready", it) }
            }

            StepCard(5, "Phonk Freeze") {
                ToggleRow("Enable Phonk Freeze", vm.freezeEnabled) { vm.freezeEnabled = it }
                if (vm.freezeEnabled) {
                    OutlinedTextField(value = vm.freezeTimestamp, onValueChange = { vm.freezeTimestamp = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Freeze at timestamp") })
                    ToggleRow("Phonk music", vm.phonkMusicEnabled) { vm.phonkMusicEnabled = it }
                    if (vm.phonkMusicEnabled) {
                        ChoiceRow(vm.phonkSourceMode == SourceMode.URL, "URL") { vm.phonkSourceMode = SourceMode.URL }
                        ChoiceRow(vm.phonkSourceMode == SourceMode.GALLERY, "Gallery") { vm.phonkSourceMode = SourceMode.GALLERY }
                        Spacer(Modifier.height(8.dp))
                        if (vm.phonkSourceMode == SourceMode.URL) {
                            OutlinedTextField(value = vm.phonkUrl, onValueChange = { vm.phonkUrl = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("Phonk URL") })
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { vm.downloadPhonk { msg -> toast(context, msg) } }) {
                                Text("Fetch phonk audio")
                            }
                        } else {
                            OutlinedButton(onClick = { pickPhonk.launch(arrayOf("audio/*", "video/*")) }) {
                                Text("Pick phonk file")
                            }
                        }
                        SliderRow("Phonk volume", vm.phonkVolume, 0f..100f) { vm.phonkVolume = it }
                    }
                    OutlinedButton(onClick = { pickSkull.launch(arrayOf("image/*")) }) {
                        Text("Pick skull overlay")
                    }
                    vm.phonkLocalPath?.let { LabeledPath("Phonk ready", it) }
                    vm.skullPath?.let { LabeledPath("Skull ready", it) }
                }
            }

            StepCard(6, "Preview, export, gallery") {
                if (vm.working || vm.progress > 0) {
                    LinearProgressIndicator(progress = { vm.progress / 100f },
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(vm.progressMessage)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.generatePreview { msg -> toast(context, msg) } },
                        enabled = !vm.working && vm.timestampsValid) { Text("Generate preview") }
                    Button(onClick = { vm.exportFull { msg -> toast(context, msg) } },
                        enabled = !vm.working && vm.timestampsValid) { Text("Export full") }
                }
                Spacer(Modifier.height(8.dp))
                vm.previewPath?.let {
                    Text("Preview", fontWeight = FontWeight.SemiBold)
                    VideoPreview(Uri.fromFile(File(it)))
                    LabeledPath("Preview file", it)
                }
                vm.fullExportPath?.let { LabeledPath("Full export", it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            vm.saveLatestToGallery(
                                onDone = { uri -> toast(context, "Saved to gallery: $uri") },
                                onError = { msg -> toast(context, msg) },
                            )
                        },
                        enabled = !vm.working &&
                                (vm.fullExportPath != null || vm.previewPath != null),
                    ) { Text("Save to gallery") }
                    OutlinedButton(
                        onClick = { vm.cleanupTempAfterExport { toast(context, "Temp files cleaned") } },
                        enabled = !vm.working,
                    ) { Text("Clean temp") }
                }
            }

            StepCard(7, "Publish") {
                Text("YouTube", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = vm.youtubeTitle, onValueChange = { vm.youtubeTitle = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Title") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = vm.youtubeDescription, onValueChange = { vm.youtubeDescription = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Description") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = vm.youtubePrivacy, onValueChange = { vm.youtubePrivacy = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Privacy (private/unlisted/public)") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.launchYouTubeAuth(activity) }) {
                        Text("Connect YouTube (OAuth2)")
                    }
                    Button(
                        onClick = {
                            vm.publishYouTube(
                                onDone = { msg -> toast(context, msg) },
                                onError = { msg -> toast(context, msg) },
                            )
                        },
                        enabled = !vm.working &&
                                (vm.fullExportPath != null || vm.previewPath != null),
                    ) { Text("Upload to YouTube") }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Text("Facebook Reels", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = vm.facebookTitle, onValueChange = { vm.facebookTitle = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Reel title") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = vm.facebookDescription, onValueChange = { vm.facebookDescription = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Reel description") })
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.publishFacebook(
                            onDone = { msg -> toast(context, msg) },
                            onError = { msg -> toast(context, msg) },
                        )
                    },
                    enabled = !vm.working &&
                            (vm.fullExportPath != null || vm.previewPath != null),
                ) { Text("Upload to Facebook Reels") }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            current = vm.secureSettings,
            onDismiss = { showSettings = false },
            onSave = {
                vm.saveSettings(it)
                showSettings = false
                toast(context, "Encrypted settings saved")
            },
        )
    }
    if (showCookieLogin) {
        CookieLoginDialog(
            onDismiss = { showCookieLogin = false },
            onSave = {
                vm.exportCookies(
                    onDone = { path -> toast(context, "cookies.txt saved: $path") },
                    onError = { msg -> toast(context, msg) },
                )
                showCookieLogin = false
            },
        )
    }
}

@Composable
private fun StepCard(index: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141722)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF2A2F45)),
                    contentAlignment = Alignment.Center,
                ) { Text(index.toString(), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text("$label: ${"%.2f".format(value)}")
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun <T> EnumSelector(entries: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        entries.forEach { item ->
            AssistChip(
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                colors = if (item == selected)
                    AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF3853FF).copy(alpha = 0.25f),
                        labelColor = Color.White,
                    )
                else AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun LabeledPath(label: String, path: String) {
    Text(label, fontWeight = FontWeight.SemiBold)
    Text(path, color = Color(0xFF9AA3B2))
}

@Composable
private fun VideoPreview(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = true
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(current: SecureSettings, onDismiss: () -> Unit, onSave: (SecureSettings) -> Unit) {
    var settings by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Encrypted settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(settings.youtubeClientId, { settings = settings.copy(youtubeClientId = it) },
                modifier = Modifier.fillMaxWidth(), label = { Text("YouTube Client ID") })
            OutlinedTextField(settings.youtubeClientSecret, { settings = settings.copy(youtubeClientSecret = it) },
                modifier = Modifier.fillMaxWidth(), label = { Text("YouTube Client Secret") })
            OutlinedTextField(settings.facebookPageId, { settings = settings.copy(facebookPageId = it) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Facebook Page ID") })
            OutlinedTextField(settings.facebookPageAccessToken,
                { settings = settings.copy(facebookPageAccessToken = it) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Facebook Page Access Token") })
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onSave(settings) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CookieLoginDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                CookieManager.getInstance().flush()
                onSave()
            }) { Text("Save cookies.txt") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("YouTube cookie login") },
        text = {
            Column {
                Text(
                    "Login with Google -> open YouTube -> tap Save. Cookies are written to " +
                            "files/yt-dlp/cookies.txt and passed to yt-dlp on every download.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    factory = {
                        WebView(it).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            loadUrl("https://accounts.google.com/")
                        }
                    },
                )
            }
        },
    )
}

private fun toast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
