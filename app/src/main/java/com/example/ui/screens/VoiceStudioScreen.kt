package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.NarrationStyleOption
import com.example.data.model.StudioLanguage
import com.example.data.model.StudioSettings
import com.example.data.model.VideoProject
import com.example.data.model.VideoScene
import com.example.media.TtsCapabilityReport
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldFree
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaBorder
import com.example.ui.theme.NebulaSurface
import com.example.ui.theme.NebulaTextSecondary
import com.example.ui.theme.StarlightWhite
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceStudioScreen(
    projects: List<VideoProject>,
    activeScenes: List<VideoScene>,
    settings: StudioSettings,
    ttsCapabilities: TtsCapabilityReport?,
    previewStatus: String?,
    isPlayingPreview: Boolean,
    onRefreshTtsCapabilities: () -> Unit,
    onSynthesizeAndPreview: (String, StudioLanguage, NarrationStyleOption, String, Float) -> Unit,
    onPlayFile: (String) -> Unit,
    onStopAudio: () -> Unit
) {
    val context = LocalContext.current
    var scriptText by remember {
        mutableStateOf(
            activeScenes.joinToString("\n\n") { it.narration }
                .ifBlank { "Welcome to Xaviour AI Studio. Today we explore the deepest mysteries of our cosmos, grounded in verified science." }
        )
    }
    var language by remember {
        mutableStateOf(StudioLanguage.entries.firstOrNull { it.label == settings.preferredLanguage } ?: StudioLanguage.ENGLISH)
    }
    var narrationStyle by remember { mutableStateOf(NarrationStyleOption.DEEP_DOCUMENTARY) }
    var voiceGender by remember { mutableStateOf(settings.preferredVoiceGender) }
    var speed by remember { mutableFloatStateOf(settings.speakingSpeed.toFloat().coerceIn(0.75f, 1.35f)) }

    var pendingExportPath by remember { mutableStateOf<String?>(null) }
    val saveAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        val srcPath = pendingExportPath
        if (uri != null && !srcPath.isNullOrBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    File(srcPath).inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(context, "Narration WAV saved!", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Export failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("AI VOICE STUDIO", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
            Text("On-Device Documentary Narration", style = MaterialTheme.typography.headlineMedium, color = StarlightWhite)
            Spacer(Modifier.height(4.dp))
            Text(
                "Uses On-Device Android TextToSpeech ($0.00, automatic long-script chunking & WAV merging).",
                style = MaterialTheme.typography.bodyMedium,
                color = EmeraldFree
            )
        }

        // Honest Device TTS Capability Inspection Card
        if (ttsCapabilities != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NebulaBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INSTALLED DEVICE TTS VOICES (${ttsCapabilities.defaultEnginePackage})",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSpark,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onRefreshTtsCapabilities) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh device voices", tint = CyanSpark)
                            }
                        }
                        ttsCapabilities.languages.forEach { support ->
                            val statusColor = if (support.isAvailable) EmeraldFree else AmberCaution
                            Text(
                                text = "• ${support.language.label}: ${support.statusDetail}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("VOICE PROFILE", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("female" to "Female Preference", "male" to "Male Preference").forEach { (id, label) ->
                            FilterChip(
                                selected = voiceGender == id,
                                onClick = { voiceGender = id },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text("LANGUAGE", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { language = lang },
                                label = { Text(lang.label) }
                            )
                        }
                    }

                    Text("NARRATION TONE", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NarrationStyleOption.entries.forEach { style ->
                            FilterChip(
                                selected = narrationStyle == style,
                                onClick = { narrationStyle = style },
                                label = { Text(style.label) }
                            )
                        }
                    }

                    Text(
                        text = "Speaking Speed: ${String.format("%.2fx", speed)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StarlightWhite
                    )
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.75f..1.35f,
                        colors = SliderDefaults.colors(thumbColor = MetallicGold, activeTrackColor = ElectricBlue)
                    )

                    OutlinedTextField(
                        value = scriptText,
                        onValueChange = { scriptText = it },
                        label = { Text("Narration Script (auto-chunked if long)") },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = NebulaBorder,
                            focusedTextColor = StarlightWhite,
                            unfocusedTextColor = StarlightWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (previewStatus != null) {
                        Surface(color = ElectricBlue.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(previewStatus, style = MaterialTheme.typography.bodyMedium, color = CyanSpark, modifier = Modifier.padding(10.dp))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSynthesizeAndPreview(scriptText, language, narrationStyle, voiceGender, speed) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("voice_synthesize_button")
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Synthesize & Preview")
                        }
                        if (isPlayingPreview) {
                            OutlinedButton(
                                onClick = onStopAudio,
                                modifier = Modifier.height(50.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                            }
                        }
                    }

                    val previewWav = File(context.filesDir, "xaviour_projects/voice_studio_preview.wav")
                    if (previewWav.exists()) {
                        OutlinedButton(
                            onClick = {
                                pendingExportPath = previewWav.absolutePath
                                saveAudioLauncher.launch("xaviour_voice_preview.wav")
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Download Voice Preview (.wav)")
                        }
                    }
                }
            }
        }

        val voicedProjects = projects.filter { it.localAudioPath.isNotBlank() && File(it.localAudioPath).exists() }
        if (voicedProjects.isNotEmpty()) {
            item {
                Text("PROJECT NARRATION TRACKS", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
            }
            items(voicedProjects, key = { it.id }) { proj ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NebulaBorder, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proj.title, style = MaterialTheme.typography.titleMedium, color = StarlightWhite)
                            Text("${proj.language} • ${proj.narrationStyle}", style = MaterialTheme.typography.bodyMedium, color = NebulaTextSecondary)
                        }
                        Row {
                            OutlinedButton(
                                onClick = { onPlayFile(proj.localAudioPath) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = {
                                    pendingExportPath = proj.localAudioPath
                                    saveAudioLauncher.launch("${proj.title.take(24).replace(" ", "_")}_narration.wav")
                                },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    }
                }
            }
        }
    }
}
