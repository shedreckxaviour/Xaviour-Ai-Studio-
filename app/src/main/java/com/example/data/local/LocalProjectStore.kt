package com.example.data.local

import com.example.data.model.VideoProject
import com.example.data.model.VideoScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Genuine on-device persistence for projects, scenes, scripts, and user media paths.
 * Guarantees projects can be reopened, edited, and resumed offline or alongside Firestore sync
 * without automatically uploading user media.
 */
class LocalProjectStore(private val projectsRootDir: File) {

    private val indexFile: File
        get() = File(projectsRootDir, "local_projects_index.json")

    suspend fun loadProjects(): List<VideoProject> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    add(projectFromJson(arr.getJSONObject(i)))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun upsertProject(project: VideoProject) = withContext(Dispatchers.IO) {
        projectsRootDir.mkdirs()
        val existing = loadProjects().toMutableList()
        val idx = existing.indexOfFirst { it.id == project.id }
        if (idx >= 0) existing[idx] = project else existing.add(0, project)
        writeProjectsIndex(existing)
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val remaining = loadProjects().filterNot { it.id == projectId }
        writeProjectsIndex(remaining)
        File(projectsRootDir, projectId).deleteRecursively()
    }

    suspend fun saveScenes(projectId: String, scenes: List<VideoScene>) = withContext(Dispatchers.IO) {
        val dir = File(projectsRootDir, projectId).apply { mkdirs() }
        val file = File(dir, "scenes.json")
        val arr = JSONArray()
        scenes.forEach { arr.put(sceneToJson(it)) }
        file.writeText(arr.toString(2), Charsets.UTF_8)
    }

    suspend fun loadScenes(projectId: String): List<VideoScene> = withContext(Dispatchers.IO) {
        val file = File(File(projectsRootDir, projectId), "scenes.json")
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    add(sceneFromJson(arr.getJSONObject(i)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeProjectsIndex(projects: List<VideoProject>) {
        projectsRootDir.mkdirs()
        val arr = JSONArray()
        projects.forEach { arr.put(projectToJson(it)) }
        val tmp = File(projectsRootDir, "local_projects_index.tmp")
        tmp.writeText(arr.toString(2), Charsets.UTF_8)
        tmp.copyTo(indexFile, overwrite = true)
        tmp.delete()
    }

    private fun projectToJson(p: VideoProject): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("userId", p.userId)
        put("title", p.title)
        put("prompt", p.prompt)
        put("durationMinutes", p.durationMinutes)
        put("aspectRatio", p.aspectRatio)
        put("videoStyle", p.videoStyle)
        put("language", p.language)
        put("narrationStyle", p.narrationStyle)
        put("status", p.status)
        put("currentStageIndex", p.currentStageIndex)
        put("scriptTopic", p.scriptTopic)
        put("targetAudience", p.targetAudience)
        put("scriptOutline", p.scriptOutline)
        put("openingHook", p.openingHook)
        put("conclusion", p.conclusion)
        put("youtubeTitle", p.youtubeTitle)
        put("youtubeDescription", p.youtubeDescription)
        put("hashtags", JSONArray(p.hashtags))
        put("thumbnailText", p.thumbnailText)
        put("factCheckNotes", p.factCheckNotes)
        put("sourcesOrUncertaintyNotes", p.sourcesOrUncertaintyNotes)
        put("estimatedNarrationSeconds", p.estimatedNarrationSeconds)
        put("freeModeUsed", p.freeModeUsed)
        put("modelUsed", p.modelUsed)
        put("visualSourceMode", p.visualSourceMode)
        put("brandingEnabled", p.brandingEnabled)
        put("outroDurationSec", p.outroDurationSec)
        put("backgroundMusicUri", p.backgroundMusicUri)
        put("backgroundMusicName", p.backgroundMusicName)
        put("localVideoPath", p.localVideoPath)
        put("localAudioPath", p.localAudioPath)
        put("localSrtPath", p.localSrtPath)
        put("localThumbnailPath", p.localThumbnailPath)
        put("estimatedSizeBytes", p.estimatedSizeBytes)
    }

    private fun projectFromJson(j: JSONObject): VideoProject {
        val tagsArr = j.optJSONArray("hashtags")
        val tags = buildList {
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    add(tagsArr.optString(i))
                }
            }
        }
        return VideoProject(
            id = j.optString("id"),
            userId = j.optString("userId"),
            title = j.optString("title"),
            prompt = j.optString("prompt"),
            durationMinutes = j.optInt("durationMinutes", 5),
            aspectRatio = j.optString("aspectRatio", "16:9"),
            videoStyle = j.optString("videoStyle", "Cinematic Documentary"),
            language = j.optString("language", "English"),
            narrationStyle = j.optString("narrationStyle", "Deep documentary"),
            status = j.optString("status", "draft"),
            currentStageIndex = j.optInt("currentStageIndex", 0),
            scriptTopic = j.optString("scriptTopic"),
            targetAudience = j.optString("targetAudience"),
            scriptOutline = j.optString("scriptOutline"),
            openingHook = j.optString("openingHook"),
            conclusion = j.optString("conclusion"),
            youtubeTitle = j.optString("youtubeTitle"),
            youtubeDescription = j.optString("youtubeDescription"),
            hashtags = tags,
            thumbnailText = j.optString("thumbnailText"),
            factCheckNotes = j.optString("factCheckNotes"),
            sourcesOrUncertaintyNotes = j.optString("sourcesOrUncertaintyNotes"),
            estimatedNarrationSeconds = j.optInt("estimatedNarrationSeconds", 0),
            freeModeUsed = j.optBoolean("freeModeUsed", true),
            modelUsed = j.optString("modelUsed", "gemini-3.8-flash"),
            visualSourceMode = j.optString("visualSourceMode", "free_animated_slides"),
            brandingEnabled = j.optBoolean("brandingEnabled", true),
            outroDurationSec = j.optInt("outroDurationSec", 5),
            backgroundMusicUri = j.optString("backgroundMusicUri"),
            backgroundMusicName = j.optString("backgroundMusicName"),
            localVideoPath = j.optString("localVideoPath"),
            localAudioPath = j.optString("localAudioPath"),
            localSrtPath = j.optString("localSrtPath"),
            localThumbnailPath = j.optString("localThumbnailPath"),
            estimatedSizeBytes = j.optLong("estimatedSizeBytes", 0L)
        )
    }

    private fun sceneToJson(s: VideoScene): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("userId", s.userId)
        put("projectId", s.projectId)
        put("sceneIndex", s.sceneIndex)
        put("title", s.title)
        put("narration", s.narration)
        put("visualPrompt", s.visualPrompt)
        put("estimatedSeconds", s.estimatedSeconds)
        put("factType", s.factType)
        put("visualSourceType", s.visualSourceType)
        put("status", s.status)
        put("accentHex", s.accentHex)
        put("userImagePath", s.userImagePath)
        put("localImagePath", s.localImagePath)
        put("localAudioPath", s.localAudioPath)
    }

    private fun sceneFromJson(j: JSONObject): VideoScene = VideoScene(
        id = j.optString("id"),
        userId = j.optString("userId"),
        projectId = j.optString("projectId"),
        sceneIndex = j.optInt("sceneIndex", 0),
        title = j.optString("title"),
        narration = j.optString("narration"),
        visualPrompt = j.optString("visualPrompt"),
        estimatedSeconds = j.optInt("estimatedSeconds", 20),
        factType = j.optString("factType", "verified_fact"),
        visualSourceType = j.optString("visualSourceType", "pending"),
        status = j.optString("status", "pending"),
        accentHex = j.optString("accentHex", "#2979FF"),
        userImagePath = j.optString("userImagePath"),
        localImagePath = j.optString("localImagePath"),
        localAudioPath = j.optString("localAudioPath")
    )
}
