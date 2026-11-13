package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.FreeModeManager
import com.example.data.local.LocalProjectStore
import com.example.data.model.StudioLanguage
import com.example.data.model.VideoProject
import com.example.data.model.VideoScene
import com.example.media.TimelineSegment
import com.example.media.VideoAssemblerEngine
import com.example.media.VisualStudioEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FreeModeAndPipelineTest {

    @Test
    fun freeModeManager_blocksPaidModelsAndAllowsVerifiedFreeModels() {
        val manager = FreeModeManager()

        val flashOk = manager.validateAndPrepareCloudRequest("gemini-3.8-flash", "script_generation")
        assertTrue(flashOk.isSuccess)
        assertEquals("gemini-3.8-flash", flashOk.getOrNull())

        val liteOk = manager.validateAndPrepareCloudRequest("gemini-3.5-flash-lite", "script_generation")
        assertTrue(liteOk.isSuccess)

        listOf("veo-video-api", "gemini-nano-banana-2.1", "gemini-4-argon", "imagen-3").forEach { paidId ->
            val blocked = manager.validateAndPrepareCloudRequest(paidId, "test_op")
            assertTrue("Expected $paidId to be blocked", blocked.isFailure)
        }
    }

    @Test
    fun freeModeManager_quotaExhaustedSetsCooldownAndFriendlyMessage() {
        val manager = FreeModeManager()
        val msg = manager.recordCloudFailure(
            "gemini-3.8-flash",
            RuntimeException("HTTP 429 RESOURCE_EXHAUSTED quota exceeded"),
            nowMs = 1_000L
        )
        assertTrue(msg.contains("Free quota limit reached", ignoreCase = true))
        assertTrue(manager.status.value.cooldownUntilMs > 1_000L)

        // Within cooldown, primary falls back ONLY to free fallback gemini-3.5-flash-lite
        val resolved = manager.validateAndPrepareCloudRequest("gemini-3.8-flash", "retry", nowMs = 5_000L)
        assertEquals("gemini-3.5-flash-lite", resolved.getOrNull())
    }

    @Test
    fun freeModeManager_durationEstimationReflectsActualTextLength() {
        val manager = FreeModeManager()
        val shortText = "This is a ten word documentary test script sentence here."
        val shortSec = manager.estimateSpeechDurationSeconds(shortText, 1.0f, StudioLanguage.ENGLISH)
        assertTrue("Short text should never be falsely labeled 15 minutes", shortSec in 3..15)

        val longText = List(400) { "galaxy" }.joinToString(" ")
        val longSec = manager.estimateSpeechDurationSeconds(longText, 1.0f, StudioLanguage.ENGLISH)
        assertTrue("400 words should estimate ~160-200 seconds", longSec in 150..210)
    }

    @Test
    fun localProjectStore_persistsAndReloadsProjectsAndScenes() = runBlocking {
        val tempDir = Files.createTempDirectory("xaviour_local_store").toFile()
        try {
            val store = LocalProjectStore(tempDir)
            val project = VideoProject(
                id = "proj_test_1",
                userId = "uid_1",
                title = "Europa Ocean Mystery",
                prompt = "Explain subsurface oceans on Europa",
                durationMinutes = 3,
                scriptOutline = "1. Ice Crust\n2. Tidal Heating",
                sourcesOrUncertaintyNotes = "NASA Europa Clipper mission context",
                estimatedNarrationSeconds = 175
            )
            val scenes = listOf(
                VideoScene(
                    id = "scene_1",
                    userId = "uid_1",
                    projectId = "proj_test_1",
                    sceneIndex = 0,
                    title = "Icy Horizon",
                    narration = "Beneath kilometers of fractured ice lies a global saltwater ocean.",
                    visualPrompt = "Jupiter rising over Europa ice ridges",
                    estimatedSeconds = 18,
                    userImagePath = "/tmp/sample.jpg"
                )
            )
            store.upsertProject(project)
            store.saveScenes("proj_test_1", scenes)

            val loadedProjects = store.loadProjects()
            assertEquals(1, loadedProjects.size)
            assertEquals("1. Ice Crust\n2. Tidal Heating", loadedProjects.first().scriptOutline)
            assertEquals(175, loadedProjects.first().estimatedNarrationSeconds)

            val loadedScenes = store.loadScenes("proj_test_1")
            assertEquals(1, loadedScenes.size)
            assertEquals("/tmp/sample.jpg", loadedScenes.first().userImagePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sampleShortProject_rendersSlidesBrandingPreviewThumbnailAndSrt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = Files.createTempDirectory("xaviour_sample_render").toFile()
        try {
            val visualEngine = VisualStudioEngine(context)
            val videoEngine = VideoAssemblerEngine(context)

            val scene = VideoScene(
                id = "scene_1",
                sceneIndex = 0,
                title = "Voyager 1 Pale Blue Dot",
                narration = "From six billion kilometers away Earth appeared as a tiny speck suspended in a sunbeam.",
                visualPrompt = "Deep space camera view showing sunlit Earth speck",
                estimatedSeconds = 8
            )
            val slideOut = File(workDir, "scene_1.png")
            val (renderedSlide, sourceType) = visualEngine.renderSceneVisualFrame(
                scene = scene,
                totalScenes = 1,
                videoStyle = "Space Documentary",
                width = 640,
                height = 360,
                outputFile = slideOut
            )
            assertTrue(renderedSlide.exists() && renderedSlide.length() > 1000L)
            assertEquals("free_motion_slide", sourceType)

            val (introPreview, outroPreview) = visualEngine.renderBrandingPreviewPair(
                channelName = "XAVIOUR EXPLAINS",
                tagline = "THINK BEYOND. EXPLORE EVERYTHING.",
                logoUriString = null,
                logoPosition = "top_center",
                logoSize = "medium",
                previewDir = workDir
            )
            assertTrue(introPreview.exists() && outroPreview.exists())

            val thumb = visualEngine.renderYoutubeThumbnail(
                titleText = "PALE BLUE DOT",
                subtitleBadge = "Space Documentary",
                subjectImagePath = null,
                outputFile = File(workDir, "thumb.png")
            )
            assertTrue(thumb.exists() && thumb.length() > 1000L)

            val srt = videoEngine.generateSrtFile(
                segments = listOf(
                    TimelineSegment("Intro", "", introPreview, null, 3000L, isBrandingCard = true),
                    TimelineSegment(scene.title, scene.narration, renderedSlide, null, 8000L, isBrandingCard = false)
                ),
                srtOutputFile = File(workDir, "subtitles.srt")
            )
            val srtText = srt.readText()
            assertFalse(srtText.isBlank())
            assertTrue(srtText.contains("00:00:03,000 -->"))
            assertTrue(srtText.contains("Pale Blue Dot", ignoreCase = true) || srtText.contains("six billion"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun cinemaFxFrames_animateKenBurnsParticlesLowerThirdAndDissolve() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = Files.createTempDirectory("xaviour_cinema_fx").toFile()
        try {
            val visualEngine = VisualStudioEngine(context)
            val videoEngine = VideoAssemblerEngine(context)
            val scene = VideoScene(
                id = "scene_fx",
                sceneIndex = 0,
                title = "Orbital Quantum Telemetry",
                narration = "Light rays bend around massive galaxy clusters across cosmic distances.",
                visualPrompt = "Deep space observatory view with glowing gravitational arcs",
                estimatedSeconds = 8,
                factType = "scientific_context",
                accentHex = "#40C4FF"
            )
            val slideFile = File(workDir, "fx_slide.png")
            visualEngine.renderSceneVisualFrame(scene, 3, "Space Documentary", 640, 360, slideFile)
            val introFile = File(workDir, "fx_intro.png")
            visualEngine.renderBrandingIntroFrame("Cosmic Lensing", "XAVIOUR EXPLAINS", null, "top_center", "medium", 640, 360, introFile)

            val slideBmp = android.graphics.BitmapFactory.decodeFile(slideFile.absolutePath)
            val introBmp = android.graphics.BitmapFactory.decodeFile(introFile.absolutePath)
            val seg = TimelineSegment(
                title = scene.title,
                subtitleText = scene.narration,
                imageFile = slideFile,
                audioFile = null,
                durationMs = 8000L,
                isBrandingCard = false,
                accentHex = "#40C4FF",
                videoStyle = "Space Documentary",
                factType = "scientific_context",
                sceneNumber = 1,
                totalScenes = 3
            )

            val rectEarly = videoEngine.computeKenBurnsRect(
                srcW = 640,
                srcH = 360,
                dstW = 640,
                dstH = 360,
                eased = 0.05f,
                preset = com.example.media.MotionPreset.PUSH_IN,
                segIndex = 0
            )
            val rectLate = videoEngine.computeKenBurnsRect(
                srcW = 640,
                srcH = 360,
                dstW = 640,
                dstH = 360,
                eased = 0.95f,
                preset = com.example.media.MotionPreset.PUSH_IN,
                segIndex = 0
            )
            assertTrue("Expected Ken Burns push-in to zoom inward over time", rectLate.width() > rectEarly.width() + 20f)

            val silentPcm = ShortArray(2000) { 0 }
            val loudPcm = ShortArray(2000) { idx -> if (idx % 2 == 0) 14000 else -14000 }
            assertEquals(0f, videoEngine.computeWindowRmsLevel(silentPcm, 0, 2000), 0.001f)
            assertTrue(videoEngine.computeWindowRmsLevel(loudPcm, 0, 2000) > 0.5f)

            val frameEarly = videoEngine.renderCinemaPreviewFrame(
                bitmap = slideBmp,
                previousBitmap = introBmp,
                dissolveAlpha = 0.6f,
                segment = seg,
                segIndex = 1,
                width = 640,
                height = 360,
                segProgress = 0.08f,
                globalTimeSec = 0.64f,
                audioLevel = 0.85f,
                subtitle = "Light rays bend around massive galaxy clusters",
                subtitleProgress = 0.25f
            )
            val frameMid = videoEngine.renderCinemaPreviewFrame(
                bitmap = slideBmp,
                previousBitmap = null,
                dissolveAlpha = 0f,
                segment = seg,
                segIndex = 1,
                width = 640,
                height = 360,
                segProgress = 0.65f,
                globalTimeSec = 5.2f,
                audioLevel = 0.25f,
                subtitle = "across cosmic distances.",
                subtitleProgress = 0.85f
            )
            assertEquals(640, frameEarly.width)
            assertEquals(360, frameEarly.height)
            assertEquals(640, frameMid.width)
            assertEquals(360, frameMid.height)

            frameEarly.recycle()
            frameMid.recycle()
            slideBmp.recycle()
            introBmp.recycle()
        } finally {
            workDir.deleteRecursively()
        }
    }
}
