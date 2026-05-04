package com.genspark.videotransform.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genspark.videotransform.data.BackgroundType
import com.genspark.videotransform.data.CaptionPosition
import com.genspark.videotransform.data.CaptionStyle
import com.genspark.videotransform.data.ColorPreset
import com.genspark.videotransform.data.SecureSettings
import com.genspark.videotransform.data.SourceMode
import com.genspark.videotransform.data.TransitionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTransformScreen(vm: VideoTransformViewModel) {
    val ctx = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var snack by remember { mutableStateOf<String?>(null) }

    val onErr: (String) -> Unit = { snack = it }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { vm.attachVideoUri(it, onErr) } }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { vm.attachAudioUri(it, onErr) } }

    val phonkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { vm.attachPhonkUri(it, onErr) } }

    val skullPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { vm.attachSkullUri(it, onErr) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "VideoTransform",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, "Settings", tint = Color.White)
            }
        }
        Text(
            "9:16 reels • cuts • captions • transitions",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                StepCard(1, "Source video") {
                    EnumSelector(
                        listOf("Gallery" to SourceMode.GALLERY, "URL" to SourceMode.URL),
                        vm.sourceMode
                    ) { vm.sourceMode = it }
                    Spacer(Modifier.height(8.dp))
                    if (vm.sourceMode == SourceMode.GALLERY) {
                        PrimaryButton(
                            text = if (vm.videoLocalPath != null) "Change video" else "Pick video",
                            icon = Icons.Default.Movie,
                            onClick = { videoPicker.launch(arrayOf("video/*")) }
                        )
                    } else {
                        DarkField(vm.videoUrl, { vm.videoUrl = it }, "Video URL", "https://… or YouTube URL")
                        Spacer(Modifier.height(6.dp))
                        DarkField(vm.videoReferer, { vm.videoReferer = it }, "Referer (optional)", "")
                        Spacer(Modifier.height(6.dp))
                        Row {
                            PrimaryButton(
                                text = "Download",
                                icon = Icons.Default.Download,
                                modifier = Modifier.weight(1f),
                                onClick = { vm.downloadVideo(onErr) }
                            )
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = {
                                    (ctx as? Activity)?.let { vm.launchYouTubeAuth(it) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Login, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("YouTube sign in", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    vm.videoLocalPath?.let {
                        Spacer(Modifier.height(6.dp))
                        StatusLine("Loaded: ${it.substringAfterLast('/')}")
                    }
                }
            }

            item {
                StepCard(2, "Cuts (one timestamp range per line)") {
                    DarkField(
                        vm.timestampsText,
                        { vm.timestampsText = it },
                        "Ranges",
                        "00:00:00-00:00:10",
                        single = false
                    )
                    if (!vm.timestampsValid) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Format: HH:MM:SS-HH:MM:SS, one per line",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                StepCard(3, "Caption") {
                    SwitchRow("Add caption", vm.captionEnabled) { vm.captionEnabled = it }
                    if (vm.captionEnabled) {
                        Spacer(Modifier.height(6.dp))
                        DarkField(vm.captionText, { vm.captionText = it }, "Text", "")
                        Spacer(Modifier.height(6.dp))
                        Label("Style")
                        EnumSelector(
                            CaptionStyle.entries.map { it.name to it },
                            vm.captionStyle
                        ) { vm.captionStyle = it }
                        Spacer(Modifier.height(6.dp))
                        Label("Position")
                        EnumSelector(
                            CaptionPosition.entries.map { it.name to it },
                            vm.captionPosition
                        ) { vm.captionPosition = it }
                        Spacer(Modifier.height(6.dp))
                        Label("Font size: ${vm.captionFontSize.toInt()}")
                        Slider(
                            value = vm.captionFontSize,
                            onValueChange = { vm.captionFontSize = it },
                            valueRange = 24f..120f,
                            colors = sliderColors()
                        )
                    }
                }
            }

            item {
                StepCard(4, "Color grading") {
                    SwitchRow("Enable", vm.colorEnabled) { vm.colorEnabled = it }
                    if (vm.colorEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Label("Preset")
                        EnumSelector(
                            ColorPreset.entries.map { it.name to it },
                            vm.colorPreset
                        ) { vm.colorPreset = it }
                    }
                }
            }

            item {
                StepCard(5, "Transitions & background") {
                    SwitchRow("Use transition between cuts", vm.transitionEnabled) {
                        vm.transitionEnabled = it
                    }
                    if (vm.transitionEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Label("Type")
                        EnumSelector(
                            TransitionType.entries.map { it.name to it },
                            vm.transitionType
                        ) { vm.transitionType = it }
                    }
                    Spacer(Modifier.height(8.dp))
                    Label("Background (when not 9:16)")
                    EnumSelector(
                        BackgroundType.entries.map { it.name to it },
                        vm.backgroundType
                    ) { vm.backgroundType = it }
                }
            }

            item {
                StepCard(6, "Music with auto-ducking") {
                    SwitchRow("Add background music", vm.audioEnabled) { vm.audioEnabled = it }
                    if (vm.audioEnabled) {
                        Spacer(Modifier.height(6.dp))
                        EnumSelector(
                            listOf("Gallery" to SourceMode.GALLERY, "URL" to SourceMode.URL),
                            vm.audioSourceMode
                        ) { vm.audioSourceMode = it }
                        Spacer(Modifier.height(6.dp))
                        if (vm.audioSourceMode == SourceMode.GALLERY) {
                            PrimaryButton(
                                text = if (vm.audioLocalPath != null) "Change track" else "Pick audio",
                                icon = Icons.Default.Movie,
                                onClick = { audioPicker.launch(arrayOf("audio/*")) }
                            )
                        } else {
                            DarkField(vm.audioUrl, { vm.audioUrl = it }, "Audio URL", "")
                            Spacer(Modifier.height(6.dp))
                            PrimaryButton(
                                text = "Download",
                                icon = Icons.Default.Download,
                                onClick = { vm.downloadAudio(onErr) }
                            )
                        }
                        vm.audioLocalPath?.let {
                            Spacer(Modifier.height(6.dp))
                            StatusLine("Audio: ${it.substringAfterLast('/')}")
                        }
                        Spacer(Modifier.height(8.dp))
                        SwitchRow("Auto-duck under speech", vm.autoDuck) { vm.autoDuck = it }
                        SwitchRow("Vocal isolation", vm.vocalIsolation) { vm.vocalIsolation = it }
                        Spacer(Modifier.height(6.dp))
                        Label("Music volume: ${vm.musicVolume.toInt()}%")
                        Slider(
                            value = vm.musicVolume,
                            onValueChange = { vm.musicVolume = it },
                            valueRange = 0f..100f,
                            colors = sliderColors()
                        )
                    }
                }
            }

            item {
                StepCard(7, "Phonk freeze (skull)") {
                    SwitchRow("Enable freeze frame", vm.freezeEnabled) { vm.freezeEnabled = it }
                    if (vm.freezeEnabled) {
                        Spacer(Modifier.height(6.dp))
                        DarkField(
                            vm.freezeTimestamp,
                            { vm.freezeTimestamp = it },
                            "Freeze at",
                            "HH:MM:SS"
                        )
                        Spacer(Modifier.height(6.dp))
                        SwitchRow("Add phonk track", vm.phonkMusicEnabled) {
                            vm.phonkMusicEnabled = it
                        }
                        if (vm.phonkMusicEnabled) {
                            Spacer(Modifier.height(6.dp))
                            EnumSelector(
                                listOf("Gallery" to SourceMode.GALLERY, "URL" to SourceMode.URL),
                                vm.phonkSourceMode
                            ) { vm.phonkSourceMode = it }
                            Spacer(Modifier.height(6.dp))
                            if (vm.phonkSourceMode == SourceMode.GALLERY) {
                                PrimaryButton(
                                    text = if (vm.phonkLocalPath != null) "Change phonk" else "Pick phonk",
                                    icon = Icons.Default.Movie,
                                    onClick = { phonkPicker.launch(arrayOf("audio/*")) }
                                )
                            } else {
                                DarkField(vm.phonkUrl, { vm.phonkUrl = it }, "Phonk URL", "")
                                Spacer(Modifier.height(6.dp))
                                PrimaryButton(
                                    text = "Download",
                                    icon = Icons.Default.Download,
                                    onClick = { vm.downloadPhonk(onErr) }
                                )
                            }
                            vm.phonkLocalPath?.let {
                                Spacer(Modifier.height(4.dp))
                                StatusLine("Phonk: ${it.substringAfterLast('/')}")
                            }
                            Spacer(Modifier.height(6.dp))
                            Label("Phonk volume: ${vm.phonkVolume.toInt()}%")
                            Slider(
                                value = vm.phonkVolume,
                                onValueChange = { vm.phonkVolume = it },
                                valueRange = 0f..100f,
                                colors = sliderColors()
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(
                            text = if (vm.skullPath != null) "Change skull image" else "Pick skull image",
                            icon = Icons.Default.Movie,
                            onClick = { skullPicker.launch(arrayOf("image/*")) }
                        )
                    }
                }
            }

            item {
                StepCard(8, "Process & publish") {
                    Row {
                        PrimaryButton(
                            text = "Preview",
                            icon = Icons.Default.Movie,
                            modifier = Modifier.weight(1f),
                            enabled = !vm.working,
                            onClick = { vm.generatePreview(onErr) }
                        )
                        Spacer(Modifier.width(6.dp))
                        PrimaryButton(
                            text = "Full export",
                            icon = Icons.Default.Movie,
                            modifier = Modifier.weight(1f),
                            enabled = !vm.working,
                            onClick = { vm.exportFull(onErr) }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                vm.saveLatestToGallery({ snack = "Saved to gallery" }, onErr)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Save", color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = {
                                vm.publishYouTube({ snack = "YouTube: $it" }, onErr)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("YT", color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = {
                                vm.publishFacebook({ snack = "FB: $it" }, onErr)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("FB", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (vm.working) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (vm.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${vm.progress}% — ${vm.progressMessage}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            vm.progressMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    vm.previewPath?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("Preview: $it", color = Color(0xFF7CFFA0), fontSize = 11.sp)
                    }
                    vm.fullExportPath?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("Export: $it", color = Color(0xFF7CFFA0), fontSize = 11.sp)
                    }
                    snack?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SettingsSheet(
                vm = vm,
                onClose = { showSettings = false }
            )
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$number",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun <T> EnumSelector(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column {
        options.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    val isSelected = value == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelect(value) }
                            .padding(vertical = 9.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (isSelected) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    single: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = single,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    )
}

@Composable
private fun PrimaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
}

@Composable
private fun StatusLine(text: String) {
    Text(text, color = Color(0xFF7CFFA0), fontSize = 12.sp)
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
)

@Composable
private fun checkboxColors() = CheckboxDefaults.colors(
    checkedColor = MaterialTheme.colorScheme.primary,
    uncheckedColor = MaterialTheme.colorScheme.outline,
    checkmarkColor = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(vm: VideoTransformViewModel, onClose: () -> Unit) {
    var ytClientId by remember { mutableStateOf(vm.secureSettings.youtubeClientId) }
    var ytClientSecret by remember { mutableStateOf(vm.secureSettings.youtubeClientSecret) }
    var fbPageId by remember { mutableStateOf(vm.secureSettings.facebookPageId) }
    var fbAccessToken by remember { mutableStateOf(vm.secureSettings.facebookPageAccessToken) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        Text("YouTube OAuth2", color = Color.White, fontWeight = FontWeight.SemiBold)
        DarkField(ytClientId, { ytClientId = it }, "Client ID", "")
        Spacer(Modifier.height(6.dp))
        DarkField(ytClientSecret, { ytClientSecret = it }, "Client secret", "")
        Spacer(Modifier.height(10.dp))

        Text("Facebook Reels", color = Color.White, fontWeight = FontWeight.SemiBold)
        DarkField(fbPageId, { fbPageId = it }, "Page ID", "")
        Spacer(Modifier.height(6.dp))
        DarkField(fbAccessToken, { fbAccessToken = it }, "Page access token", "")
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Save",
            icon = Icons.Default.Save,
            onClick = {
                vm.saveSettings(
                    SecureSettings(
                        youtubeClientId = ytClientId,
                        youtubeClientSecret = ytClientSecret,
                        facebookPageId = fbPageId,
                        facebookPageAccessToken = fbAccessToken,
                        youtubeAccessToken = vm.secureSettings.youtubeAccessToken,
                        youtubeRefreshToken = vm.secureSettings.youtubeRefreshToken,
                        youtubeTokenExpiry = vm.secureSettings.youtubeTokenExpiry,
                    )
                )
                onClose()
            }
        )
        Spacer(Modifier.height(20.dp))
    }
}
