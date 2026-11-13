package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.StudioSettings
import com.example.data.model.UsageLog
import com.example.data.model.VideoProject
import com.example.data.model.VideoScene
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class StudioRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createProjectAndScenes_authenticatedOwner_succeedsAndStreams() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = StudioRepository(firestore, auth)

        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createProject(
                VideoProject(
                    title = "Voyager Golden Record",
                    prompt = "Explain the Voyager Golden Record in 5 minutes",
                    durationMinutes = 5,
                    aspectRatio = "16:9",
                    videoStyle = "Space Documentary",
                    language = "English",
                    narrationStyle = "Deep documentary",
                    status = "script_ready",
                    currentStageIndex = 4,
                    hashtags = listOf("#Voyager", "#Space"),
                    freeModeUsed = true,
                    modelUsed = "gemini-3.8-flash",
                    visualSourceMode = "free_animated_slides"
                )
            )
        }
        assertTrue(projectId.isNotBlank())

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.replaceProjectScenes(
                projectId = projectId,
                scenes = listOf(
                    VideoScene(
                        id = "scene_1",
                        sceneIndex = 0,
                        title = "Launch Into the Void",
                        narration = "In 1977, humanity launched two interstellar messengers.",
                        visualPrompt = "Golden phonograph disc orbiting Saturn",
                        estimatedSeconds = 25,
                        factType = "verified_fact",
                        visualSourceType = "free_motion_slide",
                        status = "completed"
                    )
                )
            )
        }

        val projects = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProjects(uid).first { list -> list.any { it.id == projectId } }
        }
        assertTrue(projects.any { it.id == projectId && it.title == "Voyager Golden Record" })

        val scenes = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeScenes(uid, projectId).first { it.isNotEmpty() }
        }
        assertEquals(1, scenes.size)
        assertEquals("Launch Into the Void", scenes.first().title)
    }

    @Test
    fun observeProjects_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = StudioRepository(firestore, auth)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject(
                VideoProject(
                    title = "Alice Documentary",
                    prompt = "How neutron stars form",
                    durationMinutes = 3
                )
            )
        }
        assertTrue(projectId.isNotBlank())

        signInTestUser(BOB_EMAIL)
        val bobRepo = StudioRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeProjects(aliceUid).first()
            }
            fail("Expected cross-user observeProjects to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    @Test
    fun observeProjects_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = StudioRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeProjects("unauth_uid_check").first()
            }
            fail("Expected unauthenticated observeProjects to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)
        }
    }

    @Test
    fun usageLogsAndSettings_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = StudioRepository(firestore, auth)

        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.recordUsageLog(
                UsageLog(
                    projectId = "proj_${UUID.randomUUID().toString().take(8)}",
                    modelName = "gemini-3.8-flash",
                    operation = "script_generation",
                    isFreeTier = true,
                    estimatedTokens = 1450L,
                    status = "success",
                    detail = "Generated 7-scene documentary script"
                )
            )
        }
        assertTrue(logId.isNotBlank())

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveStudioSettings(
                StudioSettings(
                    userId = uid,
                    freeModeEnabled = true,
                    preferredTextModel = "gemini-3.8-flash",
                    preferredVoiceGender = "female",
                    preferredLanguage = "English",
                    speakingSpeed = 1.0,
                    brandingEnabled = true,
                    channelName = "XAVIOUR EXPLAINS",
                    outroTagline = "THINK BEYOND. EXPLORE EVERYTHING.",
                    outroDurationSec = 5,
                    backgroundMusicEnabled = true,
                    exportResolution = "1080p"
                )
            )
        }

        val loadedSettings = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeStudioSettings(uid).first { it.channelName == "XAVIOUR EXPLAINS" }
        }
        assertEquals("1080p", loadedSettings.exportResolution)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_studio@test.com"
        const val BOB_EMAIL = "bob_studio@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
