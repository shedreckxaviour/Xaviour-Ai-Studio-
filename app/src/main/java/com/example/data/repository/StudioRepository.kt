package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.ai.FreeModeManager
import com.example.data.model.StudioSettings
import com.example.data.model.UsageLog
import com.example.data.model.VideoProject
import com.example.data.model.VideoScene
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must sign in with Google before accessing Firestore.")
    }

    private fun safeFreeModelId(modelId: String): String {
        return if (modelId in FreeModeManager.APPROVED_FREE_CLOUD_MODELS) {
            modelId
        } else {
            FreeModeManager.PRIMARY_FREE_MODEL
        }
    }

    private fun projectsCollection(uid: String) =
        db.collection("users").document(uid).collection("projects")

    private fun scenesCollection(uid: String, projectId: String) =
        projectsCollection(uid).document(projectId).collection("scenes")

    private fun usageLogsCollection(uid: String) =
        db.collection("users").document(uid).collection("usageLogs")

    private fun settingsDoc(uid: String) =
        db.collection("users").document(uid).collection("studioSettings").document(DEFAULT_SETTINGS_DOC_ID)

    fun observeProjects(userId: String): Flow<List<VideoProject>> = flow {
        val path = "users/$userId/projects"
        emitAll(
            projectsCollection(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeScenes(userId: String, projectId: String): Flow<List<VideoScene>> = flow {
        val path = "users/$userId/projects/$projectId/scenes"
        emitAll(
            scenesCollection(userId, projectId)
                .orderBy("sceneIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoScene::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUsageLogs(userId: String): Flow<List<UsageLog>> = flow {
        val path = "users/$userId/usageLogs"
        emitAll(
            usageLogsCollection(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UsageLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeStudioSettings(userId: String): Flow<StudioSettings> = flow {
        val path = "users/$userId/studioSettings/$DEFAULT_SETTINGS_DOC_ID"
        emitAll(
            settingsDoc(userId)
                .snapshots()
                .map { snapshot ->
                    val raw = snapshot.toObject(
                        StudioSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: StudioSettings(id = DEFAULT_SETTINGS_DOC_ID, userId = userId)
                    raw.copy(
                        freeModeEnabled = true,
                        preferredTextModel = safeFreeModelId(raw.preferredTextModel)
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createProject(project: VideoProject): String {
        val uid = requireUserId()
        val docRef = if (project.id.isNotBlank()) {
            projectsCollection(uid).document(project.id)
        } else {
            projectsCollection(uid).document()
        }
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "title" to project.title.take(160).ifBlank { "Untitled Video" },
            "prompt" to project.prompt.take(2000),
            "durationMinutes" to project.durationMinutes,
            "aspectRatio" to project.aspectRatio,
            "videoStyle" to project.videoStyle.take(80),
            "language" to project.language,
            "narrationStyle" to project.narrationStyle.take(80),
            "status" to project.status,
            "currentStageIndex" to project.currentStageIndex,
            "scriptTopic" to project.scriptTopic.take(300),
            "targetAudience" to project.targetAudience.take(300),
            "scriptOutline" to project.scriptOutline.take(3000),
            "openingHook" to project.openingHook.take(1500),
            "conclusion" to project.conclusion.take(1500),
            "youtubeTitle" to project.youtubeTitle.take(140),
            "youtubeDescription" to project.youtubeDescription.take(4000),
            "hashtags" to project.hashtags.take(15),
            "thumbnailText" to project.thumbnailText.take(120),
            "factCheckNotes" to project.factCheckNotes.take(2000),
            "sourcesOrUncertaintyNotes" to project.sourcesOrUncertaintyNotes.take(2000),
            "estimatedNarrationSeconds" to project.estimatedNarrationSeconds.coerceIn(0, 36000),
            "freeModeUsed" to true,
            "modelUsed" to safeFreeModelId(project.modelUsed),
            "visualSourceMode" to project.visualSourceMode,
            "brandingEnabled" to project.brandingEnabled,
            "outroDurationSec" to project.outroDurationSec.coerceIn(2, 15),
            "backgroundMusicName" to project.backgroundMusicName.take(200),
            "localVideoPath" to project.localVideoPath.take(500),
            "localAudioPath" to project.localAudioPath.take(500),
            "localSrtPath" to project.localSrtPath.take(500),
            "localThumbnailPath" to project.localThumbnailPath.take(500),
            "estimatedSizeBytes" to project.estimatedSizeBytes.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getProject(projectId: String): VideoProject? {
        val uid = requireUserId()
        val docRef = projectsCollection(uid).document(projectId)
        return try {
            docRef.get().await().toObject(
                VideoProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateProject(project: VideoProject) {
        val uid = requireUserId()
        require(project.id.isNotBlank()) { "Project ID must not be blank for update" }
        val docRef = projectsCollection(uid).document(project.id)
        try {
            val updates = mapOf<String, Any>(
                "title" to project.title.take(160).ifBlank { "Untitled Video" },
                "status" to project.status,
                "currentStageIndex" to project.currentStageIndex.coerceIn(0, 10),
                "scriptTopic" to project.scriptTopic.take(300),
                "targetAudience" to project.targetAudience.take(300),
                "scriptOutline" to project.scriptOutline.take(3000),
                "openingHook" to project.openingHook.take(1500),
                "conclusion" to project.conclusion.take(1500),
                "youtubeTitle" to project.youtubeTitle.take(140),
                "youtubeDescription" to project.youtubeDescription.take(4000),
                "hashtags" to project.hashtags.take(15),
                "thumbnailText" to project.thumbnailText.take(120),
                "factCheckNotes" to project.factCheckNotes.take(2000),
                "sourcesOrUncertaintyNotes" to project.sourcesOrUncertaintyNotes.take(2000),
                "estimatedNarrationSeconds" to project.estimatedNarrationSeconds.coerceIn(0, 36000),
                "freeModeUsed" to true,
                "modelUsed" to safeFreeModelId(project.modelUsed),
                "visualSourceMode" to project.visualSourceMode,
                "backgroundMusicName" to project.backgroundMusicName.take(200),
                "localVideoPath" to project.localVideoPath.take(500),
                "localAudioPath" to project.localAudioPath.take(500),
                "localSrtPath" to project.localSrtPath.take(500),
                "localThumbnailPath" to project.localThumbnailPath.take(500),
                "estimatedSizeBytes" to project.estimatedSizeBytes.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun renameProject(projectId: String, newTitle: String) {
        val uid = requireUserId()
        require(projectId.isNotBlank()) { "Project ID must not be blank" }
        val sanitized = newTitle.trim().take(160)
        require(sanitized.isNotEmpty()) { "Title cannot be empty" }
        val docRef = projectsCollection(uid).document(projectId)
        try {
            docRef.update(
                mapOf(
                    "title" to sanitized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProject(projectId: String) {
        val uid = requireUserId()
        val projectRef = projectsCollection(uid).document(projectId)
        try {
            val scenesSnapshot = scenesCollection(uid, projectId).get().await()
            for (sceneDoc in scenesSnapshot.documents) {
                sceneDoc.reference.delete().await()
            }
            projectRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, projectRef.path)
            throw e
        }
    }

    suspend fun replaceProjectScenes(projectId: String, scenes: List<VideoScene>) {
        val uid = requireUserId()
        val col = scenesCollection(uid, projectId)
        try {
            val existingDocs = col.get().await()
            val batch = db.batch()
            for (doc in existingDocs.documents) {
                batch.delete(doc.reference)
            }
            scenes.forEachIndexed { idx, scene ->
                val sceneRef = if (scene.id.isNotBlank()) col.document(scene.id) else col.document("scene_${idx + 1}")
                val payload = hashMapOf<String, Any>(
                    "userId" to uid,
                    "projectId" to projectId,
                    "sceneIndex" to idx,
                    "title" to scene.title.take(160).ifBlank { "Scene ${idx + 1}" },
                    "narration" to scene.narration.take(4000),
                    "visualPrompt" to scene.visualPrompt.take(2000),
                    "estimatedSeconds" to scene.estimatedSeconds.coerceIn(2, 300),
                    "factType" to scene.factType,
                    "visualSourceType" to scene.visualSourceType,
                    "status" to scene.status,
                    "accentHex" to scene.accentHex.take(20),
                    "userImagePath" to scene.userImagePath.take(500),
                    "localImagePath" to scene.localImagePath.take(500),
                    "localAudioPath" to scene.localAudioPath.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(sceneRef, payload)
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/projects/$projectId/scenes")
            throw e
        }
    }

    suspend fun updateScene(projectId: String, scene: VideoScene) {
        val uid = requireUserId()
        require(scene.id.isNotBlank()) { "Scene ID must not be blank" }
        val docRef = scenesCollection(uid, projectId).document(scene.id)
        try {
            docRef.update(
                mapOf(
                    "sceneIndex" to scene.sceneIndex,
                    "title" to scene.title.take(160).ifBlank { "Scene ${scene.sceneIndex + 1}" },
                    "narration" to scene.narration.take(4000),
                    "visualPrompt" to scene.visualPrompt.take(2000),
                    "estimatedSeconds" to scene.estimatedSeconds.coerceIn(2, 300),
                    "factType" to scene.factType,
                    "visualSourceType" to scene.visualSourceType,
                    "status" to scene.status,
                    "accentHex" to scene.accentHex.take(20),
                    "userImagePath" to scene.userImagePath.take(500),
                    "localImagePath" to scene.localImagePath.take(500),
                    "localAudioPath" to scene.localAudioPath.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun recordUsageLog(log: UsageLog): String {
        val uid = requireUserId()
        val docRef = usageLogsCollection(uid).document()
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "projectId" to log.projectId.take(128),
            "modelName" to log.modelName.take(80),
            "operation" to log.operation.take(80),
            "isFreeTier" to true,
            "estimatedTokens" to log.estimatedTokens.coerceAtLeast(0L),
            "status" to log.status,
            "detail" to log.detail.take(300),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun saveStudioSettings(settings: StudioSettings) {
        val uid = requireUserId()
        val docRef = settingsDoc(uid)
        val safeModel = safeFreeModelId(settings.preferredTextModel)
        try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "freeModeEnabled" to true,
                        "preferredTextModel" to safeModel,
                        "preferredVoiceGender" to settings.preferredVoiceGender,
                        "preferredLanguage" to settings.preferredLanguage,
                        "speakingSpeed" to settings.speakingSpeed.coerceIn(0.5, 2.0),
                        "brandingEnabled" to settings.brandingEnabled,
                        "channelName" to settings.channelName.take(80).ifBlank { "XAVIOUR EXPLAINS" },
                        "outroTagline" to settings.outroTagline.take(120).ifBlank { "THINK BEYOND. EXPLORE EVERYTHING." },
                        "outroDurationSec" to settings.outroDurationSec.coerceIn(2, 15),
                        "logoPosition" to settings.logoPosition,
                        "logoSize" to settings.logoSize,
                        "backgroundMusicEnabled" to settings.backgroundMusicEnabled,
                        "exportResolution" to settings.exportResolution,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "freeModeEnabled" to true,
                        "preferredTextModel" to safeModel,
                        "preferredVoiceGender" to settings.preferredVoiceGender,
                        "preferredLanguage" to settings.preferredLanguage,
                        "speakingSpeed" to settings.speakingSpeed.coerceIn(0.5, 2.0),
                        "brandingEnabled" to settings.brandingEnabled,
                        "channelName" to settings.channelName.take(80).ifBlank { "XAVIOUR EXPLAINS" },
                        "outroTagline" to settings.outroTagline.take(120).ifBlank { "THINK BEYOND. EXPLORE EVERYTHING." },
                        "outroDurationSec" to settings.outroDurationSec.coerceIn(2, 15),
                        "logoPosition" to settings.logoPosition,
                        "logoSize" to settings.logoSize,
                        "backgroundMusicEnabled" to settings.backgroundMusicEnabled,
                        "exportResolution" to settings.exportResolution,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    companion object {
        const val DEFAULT_SETTINGS_DOC_ID = "preferences"
    }
}
