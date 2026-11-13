package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.ai.FreeModeStatus
import com.example.data.model.GoogleModelCatalog
import com.example.data.model.LogoPositionOption
import com.example.data.model.LogoSizeOption
import com.example.data.model.StudioSettings
import com.example.data.model.UsageLog
import com.example.ui.theme.CoralError
import com.example.ui.theme.CosmicVoid
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldFree
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaBorder
import com.example.ui.theme.NebulaSurface
import com.example.ui.theme.NebulaTextMuted
import com.example.ui.theme.NebulaTextSecondary
import com.example.ui.theme.StarlightWhite
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioSettingsScreen(
    settings: StudioSettings,
    customLogoUri: String?,
    logoPosition: String,
    logoSize: String,
    backgroundMusicName: String?,
    hardwareSupports1080p: Boolean,
    brandingPreviewPaths: Pair<String, String>?,
    userEmail: String?,
    onSaveSettings: (StudioSettings) -> Unit,
    onLogoSelected: (String?) -> Unit,
    onSelectBackgroundMusic: (Uri?) -> Unit,
    onPreviewBranding: (String, String) -> Unit,
    onDismissBrandingPreview: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var channelName by remember(settings.channelName) { mutableStateOf(settings.channelName) }
    var outroTagline by remember(settings.outroTagline) { mutableStateOf(settings.outroTagline) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onLogoSelected(uri.toString())
        }
    }

    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) onSelectBackgroundMusic(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("STUDIO SETTINGS", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
            Text("Free Mode & XAVIOUR EXPLAINS Branding", style = MaterialTheme.typography.headlineMedium, color = StarlightWhite)
        }

        // Strict Free-Only Policy Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldFree.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldFree, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Strict Free-Only Mode Active", style = MaterialTheme.typography.titleMedium, color = EmeraldFree)
                            Text(
                                "Paid APIs, Blaze billing, Veo, and paid Gemini models are permanently disabled. Never falls back to paid tiers.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NebulaTextSecondary
                            )
                        }
                    }

                    Text("PREFERRED FREE SCRIPT MODEL", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoogleModelCatalog.selectableFreeTextModels.forEach { modelId ->
                            FilterChip(
                                selected = settings.preferredTextModel == modelId,
                                onClick = {
                                    onSaveSettings(settings.copy(freeModeEnabled = true, preferredTextModel = modelId))
                                },
                                label = { Text("$modelId (Free)") }
                            )
                        }
                    }

                    Text("EXPORT VIDEO RESOLUTION & BACKGROUND MUSIC", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("720p", "1080p").forEach { res ->
                            FilterChip(
                                selected = settings.exportResolution == res,
                                onClick = { onSaveSettings(settings.copy(exportResolution = res)) },
                                label = {
                                    Text(
                                        if (res == "1080p" && !hardwareSupports1080p) "1080p (Auto->720p)" else res
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Background Music Mix", style = MaterialTheme.typography.bodyLarge, color = StarlightWhite)
                        Switch(
                            checked = settings.backgroundMusicEnabled,
                            onCheckedChange = { onSaveSettings(settings.copy(backgroundMusicEnabled = it)) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { musicPickerLauncher.launch(arrayOf("audio/*")) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (backgroundMusicName.isNullOrBlank()) "Select Audio File" else backgroundMusicName.take(20))
                        }
                        if (!backgroundMusicName.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { onSelectBackgroundMusic(null) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }

        // XAVIOUR EXPLAINS Logo Upload, Placement, Size & Live Preview
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("XAVIOUR EXPLAINS BRANDING", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CosmicVoid)
                                .border(1.dp, MetallicGold.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!customLogoUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = Uri.parse(customLogoUri),
                                    contentDescription = "Exact uploaded logo preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(4.dp)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.img_xaviour_logo_placeholder),
                                    contentDescription = "Logo placeholder",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Upload Logo")
                            }
                            if (!customLogoUri.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { onLogoSelected(null) },
                                    modifier = Modifier.fillMaxWidth().height(44.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Use Placeholder")
                                }
                            }
                        }
                    }

                    Text("LOGO POSITION", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogoPositionOption.entries.forEach { pos ->
                            FilterChip(
                                selected = logoPosition == pos.code,
                                onClick = { onSaveSettings(settings.copy(logoPosition = pos.code)) },
                                label = { Text(pos.label) }
                            )
                        }
                    }

                    Text("LOGO SIZE", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogoSizeOption.entries.forEach { sz ->
                            FilterChip(
                                selected = logoSize == sz.code,
                                onClick = { onSaveSettings(settings.copy(logoSize = sz.code)) },
                                label = { Text(sz.label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        label = { Text("Channel Name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = StarlightWhite, unfocusedTextColor = StarlightWhite),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = outroTagline,
                        onValueChange = { outroTagline = it },
                        label = { Text("Outro Tagline") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = StarlightWhite, unfocusedTextColor = StarlightWhite),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onPreviewBranding(channelName, outroTagline) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Preview, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Preview Frames")
                        }
                        Button(
                            onClick = {
                                onSaveSettings(
                                    settings.copy(
                                        channelName = channelName.ifBlank { "XAVIOUR EXPLAINS" },
                                        outroTagline = outroTagline.ifBlank { "THINK BEYOND. EXPLORE EVERYTHING." }
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = CosmicVoid),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Save Branding", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Google Account Sign Out
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Signed in with Google", style = MaterialTheme.typography.labelSmall, color = CyanSpark)
                        Text(userEmail ?: "Authenticated Account", style = MaterialTheme.typography.titleMedium, color = StarlightWhite)
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }

    // Branding Live Preview Dialog (Intro + Outro Frames)
    brandingPreviewPaths?.let { (introPath, outroPath) ->
        Dialog(onDismissRequest = onDismissBrandingPreview) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CosmicVoid),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MetallicGold, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BRANDING INTRO & OUTRO PREVIEW", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                    Text("Intro Opening Card", style = MaterialTheme.typography.bodyMedium, color = CyanSpark)
                    AsyncImage(
                        model = File(introPath),
                        contentDescription = "Intro branding preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Text("Outro End Card", style = MaterialTheme.typography.bodyMedium, color = CyanSpark)
                    AsyncImage(
                        model = File(outroPath),
                        contentDescription = "Outro branding preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Button(onClick = onDismissBrandingPreview, modifier = Modifier.fillMaxWidth()) {
                        Text("Close Preview")
                    }
                }
            }
        }
    }
}

@Composable
fun ApiUsageScreen(
    usageLogs: List<UsageLog>,
    freeModeStatus: FreeModeStatus
) {
    val successFreeCount = usageLogs.count { it.isFreeTier && it.status == "success" }
    val quotaExhaustedCount = usageLogs.count { it.status == "quota_exhausted" }
    val totalTokens = usageLogs.sumOf { it.estimatedTokens }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("API USAGE & FREE QUOTA STATUS", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
            Text("Transparent Free-Mode Telemetry", style = MaterialTheme.typography.headlineMedium, color = StarlightWhite)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                UsageStatBox("Measured Calls", "$successFreeCount", EmeraldFree, Modifier.weight(1f))
                UsageStatBox("Session Calls", "${freeModeStatus.sessionFreeRequestsUsed}", CyanSpark, Modifier.weight(1f))
                UsageStatBox("Est. Tokens", "$totalTokens", MetallicGold, Modifier.weight(1f))
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldFree.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "FREE-ONLY POLICY: ${freeModeStatus.requestStatus.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldFree
                    )
                    Text(
                        text = "Execution Mode: ${freeModeStatus.executionLocation.label} • Active: ${freeModeStatus.activeModelId}. Free quotas depend on Google's free-tier limits (not unlimited). If exhausted ($quotaExhaustedCount recorded), retry later — no paid fallback is ever called.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NebulaTextSecondary
                    )
                    if (!freeModeStatus.lastQuotaError.isNullOrBlank()) {
                        Text(
                            text = freeModeStatus.lastQuotaError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralError
                        )
                    }
                }
            }
        }

        item {
            Text("VERIFIED FREE MODELS & LOCAL ENGINES", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
        }

        items(GoogleModelCatalog.models) { model ->
            val color = when {
                model.isBlockedInFreeMode -> CoralError
                model.isLocalOnly -> CyanSpark
                else -> EmeraldFree
            }
            val tag = when {
                model.isBlockedInFreeMode -> "DISABLED (PAID)"
                model.isLocalOnly -> "ON-DEVICE ($0.00)"
                else -> "FREE CLOUD TIER"
            }
            val liveStatus = freeModeStatus.modelHealthMap[model.id] ?: model.availabilitySummary
            Card(
                colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NebulaBorder, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(model.displayName, style = MaterialTheme.typography.titleMedium, color = StarlightWhite)
                        Text(tag, style = MaterialTheme.typography.labelSmall, color = color)
                    }
                    Text(liveStatus, style = MaterialTheme.typography.bodyMedium, color = CyanSpark)
                    Text(model.quotaNote, style = MaterialTheme.typography.bodyMedium, color = NebulaTextSecondary)
                }
            }
        }

        item {
            Text("RECENT RECORDED LOGS", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
        }

        if (usageLogs.isEmpty()) {
            item {
                Text("No API calls logged yet. Generate a script or video to track live free-tier requests.", style = MaterialTheme.typography.bodyMedium, color = NebulaTextMuted)
            }
        } else {
            items(usageLogs, key = { it.id }) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NebulaSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NebulaBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${log.modelName} • ${log.operation}", style = MaterialTheme.typography.titleMedium, color = StarlightWhite)
                            Text(
                                text = log.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.status == "success") EmeraldFree else CoralError
                            )
                        }
                        if (log.detail.isNotBlank()) {
                            Text(log.detail, style = MaterialTheme.typography.bodyMedium, color = NebulaTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageStatBox(label: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NebulaSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, NebulaBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = NebulaTextMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}
