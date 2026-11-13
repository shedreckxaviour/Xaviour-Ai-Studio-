package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AspectRatioOption
import com.example.data.model.DurationOption
import com.example.data.model.NarrationStyleOption
import com.example.data.model.StudioLanguage
import com.example.data.model.StudioSettings
import com.example.data.model.VideoStyleOption
import com.example.ui.components.FreeModePill
import com.example.ui.components.RealTimeGenerationTrackerCard
import com.example.ui.theme.CosmicVoid
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldFree
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaBorder
import com.example.ui.theme.NebulaElevated
import com.example.ui.theme.NebulaSurface
import com.example.ui.theme.NebulaTextMuted
import com.example.ui.theme.NebulaTextSecondary
import com.example.ui.theme.StarlightWhite
import com.example.ui.viewmodel.GeneratorFormState
import com.example.ui.viewmodel.PipelineTrackerState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateVideoScreen(
    formState: GeneratorFormState,
    pipelineState: PipelineTrackerState,
    settings: StudioSettings,
    backgroundMusicName: String?,
    hardwareSupports1080p: Boolean,
    onUpdateForm: ((GeneratorFormState) -> GeneratorFormState) -> Unit,
    onAttachImages: (List<Uri>) -> Unit,
    onRemoveAttachedImage: (String) -> Unit,
    onSelectBackgroundMusic: (Uri?) -> Unit,
    onGenerateClicked: () -> Unit,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onOpenGeneratedScript: (String) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12)
    ) { uris ->
        if (uris.isNotEmpty()) onAttachImages(uris)
    }

    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onSelectBackgroundMusic(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ONE-PROMPT GENERATOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MetallicGold
                    )
                    Text(
                        text = "Create AI Explainer Video",
                        style = MaterialTheme.typography.headlineMedium,
                        color = StarlightWhite
                    )
                }
                FreeModePill()
            }
        }

        // Real-Time 10-Stage Tracker
        if (pipelineState.isRunning || pipelineState.currentStageIndex > 0) {
            item {
                RealTimeGenerationTrackerCard(
                    tracker = pipelineState,
                    onTogglePause = onTogglePause,
                    onCancel = onCancel
                )
                if (!pipelineState.isRunning && pipelineState.currentStageIndex == 10 && pipelineState.activeProjectId != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onOpenGeneratedScript(pipelineState.activeProjectId) },
                            colors = ButtonDefaults.buttonColors(containerColor = NebulaElevated),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Review Script")
                        }
                        Button(
                            onClick = onOpenLibrary,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldFree, contentColor = CosmicVoid),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Open Video Library", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Single Prompt Input Box
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "YOUR VIDEO IDEA (ONE PROMPT)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanSpark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formState.prompt,
                        onValueChange = { text -> onUpdateForm { it.copy(prompt = text) } },
                        placeholder = {
                            Text(
                                "Describe your video idea. Our AI will turn it into a complete documentary or explainer video.",
                                color = NebulaTextMuted
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = NebulaBorder,
                            focusedTextColor = StarlightWhite,
                            unfocusedTextColor = StarlightWhite,
                            cursorColor = MetallicGold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_input")
                    )
                }
            }
        }

        // Duration + Aspect Ratio
        item {
            SelectorSectionCard(title = "VIDEO DURATION") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DurationOption.entries.forEach { opt ->
                        StudioFilterChip(
                            label = opt.label,
                            selected = formState.duration == opt,
                            onClick = { onUpdateForm { it.copy(duration = opt) } }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "ASPECT RATIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MetallicGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspectRatioOption.entries.forEach { ratio ->
                        StudioFilterChip(
                            label = ratio.label,
                            selected = formState.aspectRatio == ratio,
                            onClick = { onUpdateForm { it.copy(aspectRatio = ratio) } }
                        )
                    }
                }
            }
        }

        // Video Style
        item {
            SelectorSectionCard(title = "VIDEO STYLE") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoStyleOption.entries.forEach { style ->
                        StudioFilterChip(
                            label = style.label,
                            selected = formState.videoStyle == style,
                            onClick = { onUpdateForm { it.copy(videoStyle = style) } }
                        )
                    }
                }
            }
        }

        // Language & Narration Style
        item {
            SelectorSectionCard(title = "LANGUAGE & NARRATION STYLE") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioLanguage.entries.forEach { lang ->
                        StudioFilterChip(
                            label = lang.label,
                            selected = formState.language == lang,
                            onClick = { onUpdateForm { it.copy(language = lang) } }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "NARRATION STYLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MetallicGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NarrationStyleOption.entries.forEach { tone ->
                        StudioFilterChip(
                            label = tone.label,
                            selected = formState.narrationStyle == tone,
                            onClick = { onUpdateForm { it.copy(narrationStyle = tone) } }
                        )
                    }
                }
            }
        }

        // Optional User Images & Background Music for Local Free Video Assembler
        item {
            SelectorSectionCard(title = "SCENE IMAGES & BACKGROUND MUSIC (100% LOCAL)") {
                Text(
                    text = "Attach your own photos/diagrams to combine with Ken Burns animated visual slides, or leave empty for automatic cosmic slides.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NebulaTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            multiPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Scene Images (${formState.attachedImagePaths.size})")
                    }
                    OutlinedButton(
                        onClick = { musicPicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (backgroundMusicName.isNullOrBlank()) "Pick Music" else "Change Music")
                    }
                }

                if (formState.attachedImagePaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(formState.attachedImagePaths) { imgPath ->
                            Box(modifier = Modifier.size(84.dp)) {
                                AsyncImage(
                                    model = File(imgPath),
                                    contentDescription = "Attached scene image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, MetallicGold, RoundedCornerShape(10.dp))
                                )
                                IconButton(
                                    onClick = { onRemoveAttachedImage(imgPath) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(26.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove image", tint = StarlightWhite)
                                }
                            }
                        }
                    }
                }

                if (!backgroundMusicName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = ElectricBlue.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Music: $backgroundMusicName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyanSpark,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onSelectBackgroundMusic(null) }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear music", tint = NebulaTextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // XAVIOUR EXPLAINS Branding & Honest Free Assembler Info
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "XAVIOUR EXPLAINS Branding",
                                style = MaterialTheme.typography.titleMedium,
                                color = StarlightWhite
                            )
                            Text(
                                text = "Galaxy opening title + outro ('THINK BEYOND. EXPLORE EVERYTHING.')",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NebulaTextSecondary
                            )
                        }
                        Switch(
                            checked = formState.brandingEnabled,
                            onCheckedChange = { checked -> onUpdateForm { it.copy(brandingEnabled = checked) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = MetallicGold, checkedTrackColor = ElectricBlue)
                        )
                    }

                    if (formState.brandingEnabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5, 8, 10).forEach { sec ->
                                StudioFilterChip(
                                    label = "Outro ${sec}s",
                                    selected = formState.outroDurationSec == sec,
                                    onClick = { onUpdateForm { it.copy(outroDurationSec = sec) } }
                                )
                            }
                        }
                    }

                    Surface(
                        color = EmeraldFree.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "24fps CINEMA FX VIDEO ENGINE (FREE MODE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldFree
                            )
                            Text(
                                text = "Veo & paid image APIs remain disabled. Video is assembled locally via Media3 Transformer with 24fps Ken Burns camera motion, scene cross-dissolves, volumetric light rays, parallax particles, animated chapter cards, voice-reactive waveform, and SRT captions. " +
                                    if (settings.exportResolution == "1080p" && !hardwareSupports1080p) {
                                        "Note: This device encoder limits H.264 below 1080p and will auto-export at 720p."
                                    } else {
                                        "Target export: ${settings.exportResolution}."
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = NebulaTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Big GENERATE VIDEO CTA
        item {
            Button(
                onClick = onGenerateClicked,
                enabled = !pipelineState.isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = StarlightWhite
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("generate_video_button")
            ) {
                Icon(imageVector = Icons.Default.MovieCreation, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (pipelineState.isRunning) "GENERATING VIDEO..." else "GENERATE VIDEO (FREE MODE)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = EmeraldFree,
                    modifier = Modifier.height(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${settings.preferredTextModel} (Free Quota) + On-Device Media3 MP4 Assembler",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NebulaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SelectorSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NebulaSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NebulaBorder, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MetallicGold)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun StudioFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ElectricBlue.copy(alpha = 0.28f),
            selectedLabelColor = StarlightWhite,
            containerColor = NebulaElevated,
            labelColor = NebulaTextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = NebulaBorder,
            selectedBorderColor = MetallicGold
        )
    )
}
