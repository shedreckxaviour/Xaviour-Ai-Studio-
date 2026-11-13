package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.FreeModeStatus
import com.example.data.model.GoogleModelCatalog
import com.example.data.model.StudioSettings
import com.example.data.model.UsageLog
import com.example.data.model.VideoProject
import com.example.ui.components.FreeModePill
import com.example.ui.components.StudioLogoBadge
import com.example.ui.components.formatByteSize
import com.example.ui.theme.CoralError
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
import com.example.ui.viewmodel.StudioUiState

@Composable
fun HomeDashboardScreen(
    projectsState: StudioUiState<List<VideoProject>>,
    usageLogsState: StudioUiState<List<UsageLog>>,
    settings: StudioSettings,
    freeModeStatus: FreeModeStatus,
    customLogoUri: String?,
    storageBytes: Long,
    onNavigateToCreate: () -> Unit,
    onNavigateToScript: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToUsage: () -> Unit,
    onOpenProject: (VideoProject) -> Unit
) {
    val projects = (projectsState as? StudioUiState.Success)?.data.orEmpty()
    val logs = (usageLogsState as? StudioUiState.Success)?.data.orEmpty()
    val freeCalls = logs.count { it.isFreeTier && it.status == "success" }
    val completedCount = projects.count { it.status == "completed" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Studio Header Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(24.dp))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_galaxy_studio_hero),
                        contentDescription = "Cosmic studio banner",
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        CosmicVoid.copy(alpha = 0.92f),
                                        CosmicVoid.copy(alpha = 0.72f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StudioLogoBadge(customLogoUri = customLogoUri, sizeDp = 44)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "XAVIOUR AI STUDIO",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = StarlightWhite
                                    )
                                    Text(
                                        text = "CREATE. EXPLAIN. EXPLORE.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MetallicGold
                                    )
                                }
                            }
                            FreeModePill()
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "One Prompt to Complete 3–15 Min Documentary",
                            style = MaterialTheme.typography.headlineMedium,
                            color = StarlightWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${freeModeStatus.executionLocation.label} • Active: ${freeModeStatus.activeModelId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyanSpark
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToCreate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = StarlightWhite
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("home_new_video_button")
                        ) {
                            Icon(imageVector = Icons.Default.AddCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Video", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        // Key Metrics Row (Quota, Storage, Generation History)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    title = "Free Calls",
                    value = "$freeCalls",
                    subtitle = "${freeModeStatus.sessionFreeRequestsUsed} session",
                    accent = EmeraldFree,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToUsage() }
                )
                MetricTile(
                    title = "Completed",
                    value = "$completedCount",
                    subtitle = "${projects.size} projects",
                    accent = ElectricBlue,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToLibrary() }
                )
                MetricTile(
                    title = "Storage Used",
                    value = formatByteSize(storageBytes),
                    subtitle = "Local MP4/WAV",
                    accent = MetallicGold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToProjects() }
                )
            }
        }

        // Studio Workspaces Quick Grid
        item {
            Text(
                text = "STUDIO WORKSPACES",
                style = MaterialTheme.typography.labelSmall,
                color = MetallicGold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WorkspaceTile("AI Script Writer", "Outline, edit & expand", Icons.Default.EditNote, ElectricBlue, Modifier.weight(1f), onNavigateToScript)
                    WorkspaceTile("Voice Studio", "EN • TE • HI narration", Icons.Default.GraphicEq, CyanSpark, Modifier.weight(1f), onNavigateToVoice)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WorkspaceTile("My Projects", "Resume & manage", Icons.Default.FolderCopy, MetallicGold, Modifier.weight(1f), onNavigateToProjects)
                    WorkspaceTile("Video Library", "MP4, Share & Thumbnails", Icons.Default.VideoLibrary, EmeraldFree, Modifier.weight(1f), onNavigateToLibrary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WorkspaceTile("API & Free Credits", "Quotas & model status", Icons.Default.Analytics, CyanSpark, Modifier.weight(1f), onNavigateToUsage)
                    WorkspaceTile("Studio Settings", "Branding & Free Mode", Icons.Default.Settings, MetallicGold, Modifier.weight(1f), onNavigateToSettings)
                }
            }
        }

        // Available Google Model Status (Strictly Free-Only)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FREE MODEL & LOCAL ENGINE STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MetallicGold
                )
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricBlue,
                    modifier = Modifier.clickable { onNavigateToUsage() }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(GoogleModelCatalog.models) { model ->
                    val badgeColor = when {
                        model.isBlockedInFreeMode -> CoralError
                        model.isLocalOnly -> CyanSpark
                        else -> EmeraldFree
                    }
                    val badgeText = when {
                        model.isBlockedInFreeMode -> "DISABLED"
                        model.isLocalOnly -> "LOCAL"
                        else -> "FREE TIER"
                    }
                    val healthText = freeModeStatus.modelHealthMap[model.id] ?: model.availabilitySummary
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .width(245.dp)
                            .border(1.dp, NebulaBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NebulaTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    color = badgeColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = StarlightWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = healthText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (model.isBlockedInFreeMode) CoralError else NebulaTextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Recent Projects & Generation History
        item {
            Text(
                text = "RECENT PROJECTS & GENERATION HISTORY",
                style = MaterialTheme.typography.labelSmall,
                color = MetallicGold
            )
        }

        if (projects.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NebulaBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MetallicGold,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No videos created yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = StarlightWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap New Video to transform a single prompt into a scripted, voiced, and locally exported animated-slide MP4 documentary.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NebulaTextSecondary
                        )
                    }
                }
            }
        } else {
            items(projects.take(6), key = { it.id }) { project ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NebulaBorder, RoundedCornerShape(16.dp))
                        .clickable { onOpenProject(project) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = StarlightWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${project.durationMinutes} min • ${project.aspectRatio} • ${project.language} • ${project.modelUsed}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NebulaTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val statusColor = when (project.status) {
                            "completed" -> EmeraldFree
                            "generating" -> CyanSpark
                            "error" -> MaterialTheme.colorScheme.error
                            else -> MetallicGold
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = project.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NebulaSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, NebulaBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = NebulaTextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = NebulaTextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun WorkspaceTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NebulaElevated.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(1.dp, NebulaBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = StarlightWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = NebulaTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
