package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.VideoProject
import com.example.data.model.VideoScene
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldFree
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaBorder
import com.example.ui.theme.NebulaSurface
import com.example.ui.theme.NebulaTextSecondary
import com.example.ui.theme.StarlightWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptWriterScreen(
    projects: List<VideoProject>,
    selectedProjectId: String?,
    scenes: List<VideoScene>,
    isBusy: Boolean,
    onSelectProject: (String) -> Unit,
    onSaveScript: (VideoProject, List<VideoScene>) -> Unit,
    onExpandScript: (VideoProject, List<VideoScene>) -> Unit,
    onRegenerateSection: (VideoProject, String, String, (String) -> Unit) -> Unit,
    onRegenerateScene: (VideoProject, VideoScene) -> Unit,
    onAttachSceneImage: (VideoProject, Int, Uri, List<VideoScene>) -> Unit,
    onGoToCreate: () -> Unit
) {
    val activeProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(activeProject?.id) {
        if (activeProject != null && selectedProjectId != activeProject.id) {
            onSelectProject(activeProject.id)
        }
    }

    if (activeProject == null) {
        EmptyWorkspaceMessage(
            icon = Icons.Default.EditNote,
            title = "No Scripts Generated Yet",
            subtitle = "Enter one prompt in Create Video and Gemini will write a complete documentary outline, fact-check notes, and calibrated scene narration.",
            ctaLabel = "Open Create Video",
            onCta = onGoToCreate
        )
        return
    }

    var youtubeTitle by remember(activeProject.id, activeProject.youtubeTitle) { mutableStateOf(activeProject.youtubeTitle) }
    var scriptOutline by remember(activeProject.id, activeProject.scriptOutline) { mutableStateOf(activeProject.scriptOutline) }
    var openingHook by remember(activeProject.id, activeProject.openingHook) { mutableStateOf(activeProject.openingHook) }
    var conclusion by remember(activeProject.id, activeProject.conclusion) { mutableStateOf(activeProject.conclusion) }
    var youtubeDescription by remember(activeProject.id, activeProject.youtubeDescription) { mutableStateOf(activeProject.youtubeDescription) }
    var thumbnailText by remember(activeProject.id, activeProject.thumbnailText) { mutableStateOf(activeProject.thumbnailText) }

    val editableScenes = remember(activeProject.id, scenes) {
        mutableStateListOf<VideoScene>().apply { addAll(scenes) }
    }

    var pendingScenePhotoIndex by remember { mutableIntStateOf(-1) }
    val scenePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && pendingScenePhotoIndex >= 0) {
            onAttachSceneImage(activeProject, pendingScenePhotoIndex, uri, editableScenes.toList())
        }
        pendingScenePhotoIndex = -1
    }

    val totalEstSec = remember(activeProject.estimatedNarrationSeconds, editableScenes.toList()) {
        val sceneSum = editableScenes.sumOf { it.estimatedSeconds }
        if (sceneSum > 0) sceneSum else activeProject.estimatedNarrationSeconds
    }
    val targetSec = activeProject.durationMinutes * 60
    val isShortOfTarget = totalEstSec > 0 && totalEstSec < (targetSec * 0.72f).toInt()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "AI SCRIPT STUDIO", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                    Text(text = "Outline, Fact-Check & Edit", style = MaterialTheme.typography.headlineMedium, color = StarlightWhite)
                }
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = ElectricBlue)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { proj ->
                    FilterChip(
                        selected = proj.id == activeProject.id,
                        onClick = { onSelectProject(proj.id) },
                        label = { Text(proj.title.take(26)) }
                    )
                }
            }
        }

        // Honest Duration Calibration + Expand Script Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isShortOfTarget) AmberCaution.copy(alpha = 0.7f) else EmeraldFree.copy(alpha = 0.55f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SPOKEN DURATION ESTIMATE: ${totalEstSec / 60}m ${totalEstSec % 60}s (Target: ${activeProject.durationMinutes}m 00s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isShortOfTarget) AmberCaution else EmeraldFree
                    )
                    Text(
                        text = "Estimated strictly from actual narration word count and speaking speed (${editableScenes.sumOf { it.narration.split(Regex("\\s+")).count { w -> w.isNotBlank() } }} words).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NebulaTextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editableScenes.isNotEmpty()) {
                            OutlinedButton(
                                enabled = !isBusy,
                                onClick = { onExpandScript(activeProject, editableScenes.toList()) },
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Expand Script")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val fullScript = buildString {
                                    appendLine("# $youtubeTitle")
                                    appendLine()
                                    if (scriptOutline.isNotBlank()) {
                                        appendLine("## Outline")
                                        appendLine(scriptOutline)
                                        appendLine()
                                    }
                                    appendLine("## Opening Hook")
                                    appendLine(openingHook)
                                    appendLine()
                                    editableScenes.forEachIndexed { i, sc ->
                                        appendLine("## Scene ${i + 1}: ${sc.title} (${sc.estimatedSeconds}s)")
                                        appendLine(sc.narration)
                                        appendLine("[Visual: ${sc.visualPrompt}]")
                                        appendLine()
                                    }
                                    appendLine("## Conclusion")
                                    appendLine(conclusion)
                                }
                                clipboard.setText(AnnotatedString(fullScript))
                                Toast.makeText(context, "Full script copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Full Script")
                        }
                    }
                }
            }
        }

        // Script Metadata, Outline & Fact-Checking Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Topic: ${activeProject.scriptTopic.ifBlank { activeProject.prompt }}", style = MaterialTheme.typography.titleMedium, color = StarlightWhite)
                    Text("Target Audience: ${activeProject.targetAudience.ifBlank { "General learners" }}", style = MaterialTheme.typography.bodyMedium, color = CyanSpark)

                    if (activeProject.factCheckNotes.isNotBlank()) {
                        Surface(
                            color = EmeraldFree.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("FACT VS SPECULATION NOTES", style = MaterialTheme.typography.labelSmall, color = EmeraldFree)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(activeProject.factCheckNotes, style = MaterialTheme.typography.bodyMedium, color = StarlightWhite)
                            }
                        }
                    }

                    if (activeProject.sourcesOrUncertaintyNotes.isNotBlank()) {
                        Surface(
                            color = CyanSpark.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("SOURCES & UNCERTAINTY NOTES", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(activeProject.sourcesOrUncertaintyNotes, style = MaterialTheme.typography.bodyMedium, color = NebulaTextSecondary)
                            }
                        }
                    }

                    ScriptField("Structured Outline", scriptOutline, minLines = 3) { scriptOutline = it }
                    ScriptField("YouTube Title", youtubeTitle) { youtubeTitle = it }
                    ScriptField("Thumbnail Text", thumbnailText) { thumbnailText = it }

                    ScriptField("Opening Hook", openingHook, minLines = 2) { openingHook = it }
                    TextButton(
                        enabled = !isBusy,
                        onClick = {
                            onRegenerateSection(activeProject, "Opening Hook", openingHook) { newHook ->
                                openingHook = newHook
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Regenerate Opening Hook")
                    }

                    ScriptField("Conclusion", conclusion, minLines = 2) { conclusion = it }
                    TextButton(
                        enabled = !isBusy,
                        onClick = {
                            onRegenerateSection(activeProject, "Conclusion", conclusion) { newConc ->
                                conclusion = newConc
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Regenerate Conclusion")
                    }

                    ScriptField("YouTube Description", youtubeDescription, minLines = 3) { youtubeDescription = it }

                    if (activeProject.hashtags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            activeProject.hashtags.forEach { tag ->
                                Surface(color = ElectricBlue.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                                    Text(tag, style = MaterialTheme.typography.labelSmall, color = CyanSpark, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SCENE PLANNER (${editableScenes.size} SCENES)", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                Button(
                    onClick = {
                        onSaveScript(
                            activeProject.copy(
                                scriptOutline = scriptOutline,
                                youtubeTitle = youtubeTitle,
                                openingHook = openingHook,
                                conclusion = conclusion,
                                youtubeDescription = youtubeDescription,
                                thumbnailText = thumbnailText
                            ),
                            editableScenes.toList()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    modifier = Modifier.testTag("save_script_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save Script")
                }
            }
        }

        itemsIndexed(editableScenes, key = { idx, sc -> "${sc.id}_$idx" }) { index, scene ->
            val factColor = when (scene.factType) {
                "speculation_labeled" -> AmberCaution
                "scientific_context" -> CyanSpark
                else -> EmeraldFree
            }
            val sourceLabel = when {
                scene.userImagePath.isNotBlank() || scene.visualSourceType == "user_uploaded_image" -> "User Uploaded Image + Motion"
                scene.visualSourceType == "free_motion_slide" -> "Free Animated Motion Slide"
                else -> "Animated Visual Slide (Free)"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scene ${index + 1} • ${scene.estimatedSeconds}s",
                            style = MaterialTheme.typography.titleMedium,
                            color = MetallicGold,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(color = factColor.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = scene.factType.replace("_", " ").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = factColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text("Visual Source: $sourceLabel", style = MaterialTheme.typography.labelSmall, color = NebulaTextSecondary)

                    ScriptField("Scene Title", scene.title) { newVal ->
                        editableScenes[index] = scene.copy(title = newVal)
                    }
                    ScriptField("Narration", scene.narration, minLines = 3) { newVal ->
                        editableScenes[index] = scene.copy(narration = newVal)
                    }
                    ScriptField("Visual Direction", scene.visualPrompt, minLines = 2) { newVal ->
                        editableScenes[index] = scene.copy(visualPrompt = newVal)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = { onRegenerateScene(activeProject, scene) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Regen Scene")
                        }
                        OutlinedButton(
                            onClick = {
                                pendingScenePhotoIndex = index
                                scenePhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (scene.userImagePath.isNotBlank()) "Replace Photo" else "Scene Photo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptField(
    label: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = NebulaTextSecondary) },
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricBlue,
            unfocusedBorderColor = NebulaBorder,
            focusedTextColor = StarlightWhite,
            unfocusedTextColor = StarlightWhite
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun EmptyWorkspaceMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    ctaLabel: String,
    onCta: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MetallicGold, modifier = Modifier.height(48.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = StarlightWhite)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NebulaTextSecondary)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCta,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
            modifier = Modifier.height(50.dp)
        ) {
            Text(ctaLabel)
        }
    }
}
