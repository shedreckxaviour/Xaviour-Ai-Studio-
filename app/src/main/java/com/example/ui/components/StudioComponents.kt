package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.GenerationStage
import com.example.ui.theme.CoralError
import com.example.ui.theme.CosmicVoid
import com.example.ui.theme.CyanSpark
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldFree
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaBorder
import com.example.ui.theme.NebulaSurface
import com.example.ui.theme.NebulaTextMuted
import com.example.ui.theme.StarlightWhite
import com.example.ui.viewmodel.PipelineTrackerState
import kotlin.random.Random

@Composable
fun CosmicStarfieldBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        val rng = Random(2026)
        List(58) {
            Triple(rng.nextFloat(), rng.nextFloat(), 1.2f + rng.nextFloat() * 2.1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(CosmicVoid, DeepNavy, CosmicVoid)
                )
                val blueAuraCenter = Offset(size.width * 0.85f, size.height * 0.15f)
                val blueAuraRadius = size.maxDimension * 0.45f
                val blueAura = Brush.radialGradient(
                    colors = listOf(ElectricBlue.copy(alpha = 0.14f), Color.Transparent),
                    center = blueAuraCenter,
                    radius = blueAuraRadius
                )
                val goldAuraCenter = Offset(size.width * 0.15f, size.height * 0.78f)
                val goldAuraRadius = size.maxDimension * 0.40f
                val goldAura = Brush.radialGradient(
                    colors = listOf(MetallicGold.copy(alpha = 0.08f), Color.Transparent),
                    center = goldAuraCenter,
                    radius = goldAuraRadius
                )
                onDrawBehind {
                    drawRect(brush = bgBrush)
                    drawCircle(brush = blueAura, radius = blueAuraRadius, center = blueAuraCenter)
                    drawCircle(brush = goldAura, radius = goldAuraRadius, center = goldAuraCenter)
                    stars.forEachIndexed { idx, (rx, ry, radius) ->
                        val color = when {
                            idx % 8 == 0 -> MetallicGold.copy(alpha = 0.55f)
                            idx % 4 == 0 -> CyanSpark.copy(alpha = 0.45f)
                            else -> Color.White.copy(alpha = 0.35f)
                        }
                        drawCircle(
                            color = color,
                            radius = radius,
                            center = Offset(rx * size.width, ry * size.height)
                        )
                    }
                }
            }
    )
}

@Composable
fun StudioLogoBadge(
    customLogoUri: String?,
    sizeDp: Int = 42,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .border(1.5.dp, MetallicGold.copy(alpha = 0.85f), CircleShape)
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        if (!customLogoUri.isNullOrBlank()) {
            AsyncImage(
                model = Uri.parse(customLogoUri),
                contentDescription = "Uploaded channel logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_xaviour_logo_placeholder),
                contentDescription = "Xaviour AI Studio logo placeholder",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun FreeModePill(freeModeActive: Boolean = true, modifier: Modifier = Modifier) {
    val bg = EmeraldFree.copy(alpha = 0.16f)
    val fg = EmeraldFree
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "FREE MODE",
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

@Composable
fun RealTimeGenerationTrackerCard(
    tracker: PipelineTrackerState,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NebulaSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ElectricBlue.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "REAL-TIME GENERATION TRACKER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MetallicGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tracker.statusMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = StarlightWhite
                    )
                }
                Text(
                    text = "${(tracker.stageProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricBlue,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { tracker.stageProgress.coerceIn(0f, 1f) },
                color = if (tracker.errorMessage != null) CoralError else ElectricBlue,
                trackColor = NebulaBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )

            if (tracker.errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = CoralError.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tracker.errorMessage,
                        color = CoralError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            GenerationStage.entries.forEach { stage ->
                val done = tracker.currentStageIndex > stage.index ||
                    (!tracker.isRunning && tracker.currentStageIndex == 10 && tracker.errorMessage == null)
                val active = tracker.isRunning && tracker.currentStageIndex == stage.index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        done -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Done",
                            tint = EmeraldFree,
                            modifier = Modifier.size(16.dp)
                        )
                        active -> CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = ElectricBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Pending",
                            tint = NebulaTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${stage.index}. ${stage.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            done -> StarlightWhite
                            active -> CyanSpark
                            else -> NebulaTextMuted
                        }
                    )
                }
            }

            if (tracker.isRunning) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (tracker.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (tracker.isPaused) "Resume" else "Pause")
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_generation_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

fun formatByteSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
}
