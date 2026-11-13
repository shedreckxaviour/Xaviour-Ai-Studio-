package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.VideoProject
import com.example.ui.components.formatByteSize
import com.example.ui.theme.CoralError
import com.example.ui.theme.CosmicVoid
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldFree
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaBorder
import com.example.ui.theme.NebulaSurface
import com.example.ui.theme.NebulaTextSecondary
import com.example.ui.theme.StarlightWhite
import java.io.File

@Composable
fun MyProjectsScreen(
    projects: List<VideoProject>,
    onOpenProjectScript: (VideoProject) -> Unit,
    onResumeProject: (VideoProject) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onGoToCreate: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<VideoProject?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<VideoProject?>(null) }

    if (projects.isEmpty()) {
        EmptyWorkspaceMessage(
            icon = Icons.Default.FolderCopy,
            title = "No Projects Saved",
            subtitle = "Every video project you create is saved locally on your device and synced when signed in.",
            ctaLabel = "Create First Video",
            onCta = onGoToCreate
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("MY PROJECTS", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
            Text("Manage & Resume Projects (${projects.size})", style = MaterialTheme.typography.headlineMedium, color = StarlightWhite)
        }

        items(projects, key = { it.id }) { project ->
            val canResume = project.status != "completed"
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium, color = StarlightWhite)
                            Text(
                                "${project.durationMinutes} min • ${project.aspectRatio} • ${project.videoStyle}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NebulaTextSecondary
                            )
                        }
                        Row {
                            IconButton(onClick = {
                                renameTarget = project
                                renameValue = project.title
                            }) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename project", tint = CyanSpark)
                            }
                            IconButton(onClick = { deleteTarget = project }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete project", tint = CoralError)
                            }
                        }
                    }

                    Text(
                        text = "Prompt: ${project.prompt}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NebulaTextSecondary,
                        maxLines = 2
                    )

                    Text(
                        text = "Status: ${project.status.uppercase()} • ${formatByteSize(project.estimatedSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (project.status == "completed") EmeraldFree else MetallicGold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canResume) {
                            Button(
                                onClick = { onResumeProject(project) },
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = CosmicVoid),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Resume")
                            }
                        }
                        Button(
                            onClick = { onOpenProjectScript(project) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Open Script")
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = NebulaSurface,
            title = { Text("Rename Project", color = StarlightWhite) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Project Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StarlightWhite,
                        unfocusedTextColor = StarlightWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameValue.isNotBlank()) onRenameProject(target.id, renameValue)
                    renameTarget = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = NebulaSurface,
            title = { Text("Delete Project?", color = StarlightWhite) },
            text = { Text("Deletes '${target.title}' and removes its local MP4, audio, and thumbnails.", color = NebulaTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoLibraryScreen(
    projects: List<VideoProject>,
    onRegenerateThumbnail: (VideoProject, String) -> Unit,
    onGoToCreate: () -> Unit
) {
    val context = LocalContext.current
    val readyProjects = projects.filter { it.localVideoPath.isNotBlank() || it.localThumbnailPath.isNotBlank() }

    var previewVideoPath by remember { mutableStateOf<String?>(null) }
    var thumbEditProject by remember { mutableStateOf<VideoProject?>(null) }
    var thumbHeadline by remember { mutableStateOf("") }

    var exportSourcePath by remember { mutableStateOf<String?>(null) }
    val createMp4Launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        copyLocalFileToUri(context, exportSourcePath, uri, "MP4 video saved!")
    }
    val createWavLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        copyLocalFileToUri(context, exportSourcePath, uri, "Narration audio saved!")
    }
    val createSrtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        copyLocalFileToUri(context, exportSourcePath, uri, "Subtitles (.srt) saved!")
    }
    val createPngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        copyLocalFileToUri(context, exportSourcePath, uri, "Thumbnail PNG saved!")
    }

    if (readyProjects.isEmpty()) {
        EmptyWorkspaceMessage(
            icon = Icons.Default.VideoLibrary,
            title = "Video Library Is Empty",
            subtitle = "Completed animated-slide MP4 videos, 16:9 YouTube thumbnails, narration WAV tracks, and SRT subtitle files appear here for preview, export, and native sharing.",
            ctaLabel = "Generate a Video",
            onCta = onGoToCreate
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("VIDEO LIBRARY & EXPORTS", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
            Text("Preview, Export & Share Media", style = MaterialTheme.typography.headlineMedium, color = StarlightWhite)
        }

        items(readyProjects, key = { it.id }) { project ->
            val hasVideo = project.localVideoPath.isNotBlank() && File(project.localVideoPath).exists()
            val hasAudio = project.localAudioPath.isNotBlank() && File(project.localAudioPath).exists()
            val hasSrt = project.localSrtPath.isNotBlank() && File(project.localSrtPath).exists()
            val hasThumb = project.localThumbnailPath.isNotBlank() && File(project.localThumbnailPath).exists()
            val safeBaseName = project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(28).ifBlank { "xaviour_video" }

            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (hasThumb) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CosmicVoid)
                        ) {
                            AsyncImage(
                                model = File(project.localThumbnailPath),
                                contentDescription = "YouTube Thumbnail for ${project.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = CosmicVoid.copy(alpha = 0.80f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = if (project.visualSourceMode == "user_images_and_slides") "USER IMAGES + SLIDES" else "ANIMATED SLIDES (FREE)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldFree,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(project.title, style = MaterialTheme.typography.titleLarge, color = StarlightWhite)
                    Text(
                        "${project.durationMinutes}m target • ${project.aspectRatio} • ${project.language} • ${formatByteSize(project.estimatedSizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NebulaTextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasVideo) {
                            Button(
                                onClick = { previewVideoPath = project.localVideoPath },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Preview MP4")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                thumbEditProject = project
                                thumbHeadline = project.thumbnailText.ifBlank { project.title }
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Thumbnail")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hasVideo) {
                            OutlinedButton(onClick = {
                                exportSourcePath = project.localVideoPath
                                createMp4Launcher.launch("${safeBaseName}.mp4")
                            }) {
                                Icon(Icons.Default.VideoFile, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Save MP4")
                            }
                            OutlinedButton(onClick = {
                                shareLocalFile(context, project.localVideoPath, "video/mp4", "Share ${project.title} MP4")
                            }) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Share MP4")
                            }
                        }
                        if (hasAudio) {
                            OutlinedButton(onClick = {
                                exportSourcePath = project.localAudioPath
                                createWavLauncher.launch("${safeBaseName}_audio.wav")
                            }) {
                                Icon(Icons.Default.AudioFile, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("WAV")
                            }
                        }
                        if (hasSrt) {
                            OutlinedButton(onClick = {
                                exportSourcePath = project.localSrtPath
                                createSrtLauncher.launch("${safeBaseName}.srt")
                            }) {
                                Icon(Icons.Default.Subtitles, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("SRT")
                            }
                        }
                        if (hasThumb) {
                            OutlinedButton(onClick = {
                                exportSourcePath = project.localThumbnailPath
                                createPngLauncher.launch("${safeBaseName}_thumb.png")
                            }) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("PNG")
                            }
                        }
                    }
                }
            }
        }
    }

    // In-App Video Preview Player Dialog
    previewVideoPath?.let { path ->
        var isPreparing by remember(path) { mutableStateOf(true) }
        var playbackError by remember(path) { mutableStateOf<String?>(null) }
        var videoViewRef by remember(path) { mutableStateOf<VideoView?>(null) }

        DisposableEffect(path) {
            onDispose {
                runCatching { videoViewRef?.stopPlayback() }
                videoViewRef = null
            }
        }

        Dialog(onDismissRequest = { previewVideoPath = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicVoid),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MetallicGold, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("MP4 PREVIEW", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmicVoid),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    videoViewRef = this
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setZOrderMediaOverlay(true)
                                    setVideoURI(Uri.fromFile(File(path)))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        isPreparing = false
                                        playbackError = null
                                        start()
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        isPreparing = false
                                        playbackError = "Preview codec unavailable on this emulator ($what/$extra). Use Share / Open MP4 below or install APK on your phone for smooth hardware playback."
                                        true
                                    }
                                    requestFocus()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isPreparing && playbackError == null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = CyanSpark)
                                Text(
                                    "Buffering MP4 preview...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NebulaTextSecondary
                                )
                            }
                        }
                        playbackError?.let { err ->
                            Surface(
                                color = CosmicVoid.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CoralError,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { shareLocalFile(context, path, "video/mp4", "Open MP4 in Video Player") },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Open / Share")
                        }
                        Button(
                            onClick = { previewVideoPath = null },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Close Player")
                        }
                    }
                }
            }
        }
    }

    // Free Thumbnail Text Editor Dialog
    thumbEditProject?.let { proj ->
        AlertDialog(
            onDismissRequest = { thumbEditProject = null },
            containerColor = NebulaSurface,
            title = { Text("16:9 Free Thumbnail Editor", color = StarlightWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = thumbHeadline,
                        onValueChange = { thumbHeadline = it },
                        label = { Text("Editable Thumbnail Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StarlightWhite,
                            unfocusedTextColor = StarlightWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Renders locally at 1280x720 using your project's uploaded scene image or cosmic studio artwork ($0.00).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NebulaTextSecondary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onRegenerateThumbnail(proj, thumbHeadline)
                    thumbEditProject = null
                }) {
                    Text("Render 16:9 PNG")
                }
            },
            dismissButton = {
                TextButton(onClick = { thumbEditProject = null }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun shareLocalFile(
    context: Context,
    filePath: String,
    mimeType: String,
    chooserTitle: String
) {
    val file = File(filePath)
    if (!file.exists()) {
        Toast.makeText(context, "File not found on device.", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }.onFailure {
        Toast.makeText(context, "Share failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun copyLocalFileToUri(
    context: Context,
    srcPath: String?,
    destUri: Uri?,
    successMsg: String
) {
    if (srcPath.isNullOrBlank() || destUri == null) return
    runCatching {
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            File(srcPath).inputStream().use { input -> input.copyTo(out) }
        }
        Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Save failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
