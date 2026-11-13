package com.example.ui.viewmodel

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.FreeModeManager
import com.example.ai.FreeModeStatus
import com.example.ai.GeminiStudioEngine
import com.example.data.local.LocalProjectStore
import com.example.data.local.StudioPreferencesStore
import com.example.data.model.AspectRatioOption
import com.example.data.model.DurationOption
import com.example.data.model.GenerationStage
import com.example.data.model.NarrationStyleOption
import com.example.data.model.StudioLanguage
import com.example.data.model.StudioSettings
import com.example.data.model.UsageLog
import com.example.data.model.VideoProject
import com.example.data.model.VideoScene
import com.example.data.model.VideoStyleOption
import com.example.data.repository.StudioRepository
import com.example.media.TimelineSegment
import com.example.media.TtsCapabilityReport
import com.example.media.VideoAssemblerEngine
import com.example.media.VisualStudioEngine
import com.example.media.VoiceStudioEngine
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "StudioViewModel"

sealed interface StudioUiState<out T> {
    data object Loading : StudioUiState<Nothing>
    data class Success<T>(val data: T) : StudioUiState<T>
    data class Error(val message: String) : StudioUiState<Nothing>
}

data class PipelineTrackerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentStageIndex: Int = 0,
    val stageProgress: Float = 0f,
    val statusMessage: String = "Ready to create your explainer video",
    val errorMessage: String? = null,
    val activeProjectId: String? = null,
    val rendererLabel: String? = null
)

data class GeneratorFormState(
    val prompt: String = "",
    val duration: DurationOption = DurationOption.THREE_MIN,
    val aspectRatio: AspectRatioOption = AspectRatioOption.LANDSCAPE_16_9,
    val videoStyle: VideoStyleOption = VideoStyleOption.CINEMATIC_DOCUMENTARY,
    val language: StudioLanguage = StudioLanguage.ENGLISH,
    val narrationStyle: NarrationStyleOption = NarrationStyleOption.DEEP_DOCUMENTARY,
    val brandingEnabled: Boolean = true,
    val outroDurationSec: Int = 5,
    val attachedImagePaths: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(
    private val repository: StudioRepository,
    private val freeModeManager: FreeModeManager,
    private val geminiEngine: GeminiStudioEngine,
    private val visualEngine: VisualStudioEngine,
    private val voiceEngine: VoiceStudioEngine,
    private val videoEngine: VideoAssemblerEngine,
    private val prefsStore: StudioPreferencesStore,
    private val localProjectStore: LocalProjectStore,
    private val contentResolver: ContentResolver,
    private val projectsRootDir: File,
    val currentUserId: String
) : ViewModel() {

    private val _localProjects = MutableStateFlow<List<VideoProject>>(emptyList())
    private val _localScenesMap = MutableStateFlow<Map<String, List<VideoScene>>>(emptyMap())

    val freeModeStatus: StateFlow<FreeModeStatus> = freeModeManager.status

    val projectsState: StateFlow<StudioUiState<List<VideoProject>>> =
        combine(
            repository.observeProjects(currentUserId)
                .map<List<VideoProject>, StudioUiState<List<VideoProject>>> { StudioUiState.Success(it) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val root = fsErr ?: error
                    Log.w(TAG, "Cloud project sync error, using on-device projects", root)
                    emit(StudioUiState.Error(root.message ?: "Cloud sync unavailable"))
                },
            _localProjects
        ) { cloudState, localList ->
            when (cloudState) {
                is StudioUiState.Success -> {
                    val mergedMap = LinkedHashMap<String, VideoProject>()
                    cloudState.data.forEach { cp -> mergedMap[cp.id] = cp }
                    localList.forEach { lp ->
                        val existing = mergedMap[lp.id]
                        if (existing == null) {
                            mergedMap[lp.id] = lp
                        } else {
                            // Keep local media file paths & outline details if newer or missing from cloud
                            mergedMap[lp.id] = existing.copy(
                                localVideoPath = existing.localVideoPath.ifBlank { lp.localVideoPath },
                                localAudioPath = existing.localAudioPath.ifBlank { lp.localAudioPath },
                                localSrtPath = existing.localSrtPath.ifBlank { lp.localSrtPath },
                                localThumbnailPath = existing.localThumbnailPath.ifBlank { lp.localThumbnailPath },
                                backgroundMusicUri = lp.backgroundMusicUri.ifBlank { existing.backgroundMusicUri },
                                backgroundMusicName = existing.backgroundMusicName.ifBlank { lp.backgroundMusicName },
                                scriptOutline = existing.scriptOutline.ifBlank { lp.scriptOutline },
                                sourcesOrUncertaintyNotes = existing.sourcesOrUncertaintyNotes.ifBlank { lp.sourcesOrUncertaintyNotes },
                                estimatedNarrationSeconds = if (existing.estimatedNarrationSeconds > 0) existing.estimatedNarrationSeconds else lp.estimatedNarrationSeconds
                            )
                        }
                    }
                    StudioUiState.Success(mergedMap.values.toList())
                }
                is StudioUiState.Error -> {
                    if (localList.isNotEmpty()) StudioUiState.Success(localList) else cloudState
                }
                StudioUiState.Loading -> {
                    if (localList.isNotEmpty()) StudioUiState.Success(localList) else StudioUiState.Loading
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StudioUiState.Loading)

    val usageLogsState: StateFlow<StudioUiState<List<UsageLog>>> =
        repository.observeUsageLogs(currentUserId)
            .map<List<UsageLog>, StudioUiState<List<UsageLog>>> { StudioUiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Failed observing usage logs", root)
                emit(StudioUiState.Error(root.message ?: "Failed to load usage logs"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StudioUiState.Loading)

    val settingsState: StateFlow<StudioSettings> =
        repository.observeStudioSettings(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed observing settings", error)
                emit(StudioSettings(userId = currentUserId))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000L),
                StudioSettings(userId = currentUserId)
            )

    val customLogoUri: StateFlow<String?> = prefsStore.customLogoUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val logoPosition: StateFlow<String> = prefsStore.logoPosition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "top_center")

    val logoSize: StateFlow<String> = prefsStore.logoSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "medium")

    val backgroundMusicUri: StateFlow<String?> = prefsStore.backgroundMusicUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val backgroundMusicName: StateFlow<String?> = prefsStore.backgroundMusicName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    val selectedScenesState: StateFlow<List<VideoScene>> = _selectedProjectId
        .flatMapLatest { pid ->
            if (pid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                combine(
                    repository.observeScenes(currentUserId, pid)
                        .catch { error ->
                            if (error is CancellationException) throw error
                            Log.w(TAG, "Cloud scenes unavailable, falling back to local scenes", error)
                            emit(emptyList())
                        },
                    _localScenesMap
                ) { cloudScenes, localMap ->
                    val localScenes = localMap[pid].orEmpty()
                    if (cloudScenes.isNotEmpty()) {
                        cloudScenes.mapIndexed { idx, cs ->
                            val ls = localScenes.firstOrNull { it.id == cs.id } ?: localScenes.getOrNull(idx)
                            if (ls != null) {
                                cs.copy(
                                    userImagePath = cs.userImagePath.ifBlank { ls.userImagePath },
                                    localImagePath = cs.localImagePath.ifBlank { ls.localImagePath },
                                    localAudioPath = cs.localAudioPath.ifBlank { ls.localAudioPath }
                                )
                            } else cs
                        }
                    } else {
                        localScenes
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _formState = MutableStateFlow(GeneratorFormState())
    val formState: StateFlow<GeneratorFormState> = _formState.asStateFlow()

    private val _pipelineState = MutableStateFlow(PipelineTrackerState())
    val pipelineState: StateFlow<PipelineTrackerState> = _pipelineState.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _voicePreviewStatus = MutableStateFlow<String?>(null)
    val voicePreviewStatus: StateFlow<String?> = _voicePreviewStatus.asStateFlow()

    private val _isPlayingAudioPreview = MutableStateFlow(false)
    val isPlayingAudioPreview: StateFlow<Boolean> = _isPlayingAudioPreview.asStateFlow()

    private val _ttsCapabilities = MutableStateFlow<TtsCapabilityReport?>(null)
    val ttsCapabilities: StateFlow<TtsCapabilityReport?> = _ttsCapabilities.asStateFlow()

    private val _hardwareSupports1080p = MutableStateFlow(true)
    val hardwareSupports1080p: StateFlow<Boolean> = _hardwareSupports1080p.asStateFlow()

    private val _brandingPreviewPaths = MutableStateFlow<Pair<String, String>?>(null)
    val brandingPreviewPaths: StateFlow<Pair<String, String>?> = _brandingPreviewPaths.asStateFlow()

    private val _isScriptActionBusy = MutableStateFlow(false)
    val isScriptActionBusy: StateFlow<Boolean> = _isScriptActionBusy.asStateFlow()

    private val _actionBanner = MutableStateFlow<String?>(null)
    val actionBanner: StateFlow<String?> = _actionBanner.asStateFlow()

    private var activeGenerationJob: Job? = null
    private val isPausedFlag = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            _localProjects.value = localProjectStore.loadProjects()
        }
        viewModelScope.launch(Dispatchers.Default) {
            _hardwareSupports1080p.value = videoEngine.is1080pEncodingSupported()
        }
        refreshStorageUsage()
        inspectTtsCapabilities()
    }

    fun inspectTtsCapabilities() {
        viewModelScope.launch {
            _ttsCapabilities.value = voiceEngine.inspectDeviceTtsCapabilities()
        }
    }

    fun updateForm(transform: (GeneratorFormState) -> GeneratorFormState) {
        _formState.value = transform(_formState.value)
    }

    fun selectProject(projectId: String?) {
        _selectedProjectId.value = projectId
        if (!projectId.isNullOrBlank()) {
            viewModelScope.launch {
                val local = localProjectStore.loadScenes(projectId)
                if (local.isNotEmpty()) {
                    _localScenesMap.value = _localScenesMap.value + (projectId to local)
                }
            }
        }
    }

    fun clearBanner() {
        _actionBanner.value = null
    }

    fun attachImagesToForm(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val draftImgDir = File(projectsRootDir, "draft_images").apply { mkdirs() }
            val copiedPaths = mutableListOf<String>()
            uris.forEachIndexed { idx, uri ->
                val dest = File(draftImgDir, "user_img_${System.currentTimeMillis()}_$idx.jpg")
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { out -> input.copyTo(out) }
                    }
                    if (dest.exists() && dest.length() > 0) {
                        copiedPaths.add(dest.absolutePath)
                    }
                }
            }
            if (copiedPaths.isNotEmpty()) {
                _formState.value = _formState.value.copy(
                    attachedImagePaths = _formState.value.attachedImagePaths + copiedPaths
                )
                _actionBanner.value = "Attached ${copiedPaths.size} image(s) for scene slides."
            } else {
                _actionBanner.value = "Could not read selected image file(s)."
            }
        }
    }

    fun removeAttachedImageFromForm(path: String) {
        _formState.value = _formState.value.copy(
            attachedImagePaths = _formState.value.attachedImagePaths.filterNot { it == path }
        )
        runCatching { File(path).delete() }
    }

    fun attachImageToScene(project: VideoProject, sceneIndex: Int, uri: Uri, currentScenes: List<VideoScene>) {
        viewModelScope.launch {
            val copiedPath = withContext(Dispatchers.IO) {
                val dir = File(projectsRootDir, project.id).apply { mkdirs() }
                val dest = File(dir, "user_scene_${sceneIndex + 1}_${System.currentTimeMillis()}.jpg")
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { out -> input.copyTo(out) }
                    }
                    dest.takeIf { it.exists() && it.length() > 0 }?.absolutePath
                }.getOrNull()
            }
            if (copiedPath == null) {
                _actionBanner.value = "Could not import selected image."
                return@launch
            }
            val updatedScenes = currentScenes.mapIndexed { idx, sc ->
                if (idx == sceneIndex) sc.copy(userImagePath = copiedPath, visualSourceType = "user_uploaded_image")
                else sc
            }
            localProjectStore.saveScenes(project.id, updatedScenes)
            _localScenesMap.value = _localScenesMap.value + (project.id to updatedScenes)
            updatedScenes.getOrNull(sceneIndex)?.let { sc ->
                if (sc.id.isNotBlank()) {
                    runCatching { repository.updateScene(project.id, sc) }
                }
            }
            _actionBanner.value = "Scene ${sceneIndex + 1} image updated!"
        }
    }

    fun selectBackgroundMusic(uri: Uri?) {
        if (uri == null) {
            viewModelScope.launch {
                prefsStore.setBackgroundMusic(null, null)
                _actionBanner.value = "Cleared custom background music (using subtle ambient synth)."
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val displayName = resolveDisplayName(uri) ?: "Custom Audio Track"
            val copiedFile = withContext(Dispatchers.IO) {
                val musicDir = File(projectsRootDir, "custom_audio").apply { mkdirs() }
                val dest = File(musicDir, "selected_bg_music.audio")
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { out -> input.copyTo(out) }
                    }
                    dest.takeIf { it.exists() && it.length() > 0 }
                }.getOrNull()
            }
            val finalUri = copiedFile?.let { Uri.fromFile(it).toString() } ?: uri.toString()
            prefsStore.setBackgroundMusic(finalUri, displayName)
            _actionBanner.value = "Background music selected: $displayName"
        }
    }

    fun requestVideoGeneration() {
        val form = _formState.value
        val trimmedPrompt = form.prompt.trim()
        if (trimmedPrompt.length < 4) {
            _actionBanner.value = "Enter a video topic or prompt first."
            return
        }
        launchFullGenerationPipeline(form = form, resumeProject = null)
    }

    fun resumeProject(project: VideoProject) {
        val dur = DurationOption.entries.firstOrNull { it.minutes == project.durationMinutes } ?: DurationOption.FIVE_MIN
        val ratio = AspectRatioOption.entries.firstOrNull { it.code == project.aspectRatio } ?: AspectRatioOption.LANDSCAPE_16_9
        val style = VideoStyleOption.entries.firstOrNull { it.label == project.videoStyle } ?: VideoStyleOption.CINEMATIC_DOCUMENTARY
        val lang = StudioLanguage.entries.firstOrNull { it.label == project.language } ?: StudioLanguage.ENGLISH
        val tone = NarrationStyleOption.entries.firstOrNull { it.label == project.narrationStyle } ?: NarrationStyleOption.DEEP_DOCUMENTARY

        val resumedForm = GeneratorFormState(
            prompt = project.prompt,
            duration = dur,
            aspectRatio = ratio,
            videoStyle = style,
            language = lang,
            narrationStyle = tone,
            brandingEnabled = project.brandingEnabled,
            outroDurationSec = project.outroDurationSec
        )
        _formState.value = resumedForm
        _selectedProjectId.value = project.id
        launchFullGenerationPipeline(form = resumedForm, resumeProject = project)
    }

    fun togglePauseGeneration() {
        val nowPaused = !isPausedFlag.get()
        isPausedFlag.set(nowPaused)
        _pipelineState.value = _pipelineState.value.copy(
            isPaused = nowPaused,
            statusMessage = if (nowPaused) "Paused — tap Resume to continue" else "Resuming pipeline..."
        )
    }

    fun cancelGeneration() {
        activeGenerationJob?.cancel()
        isPausedFlag.set(false)
        _pipelineState.value = _pipelineState.value.copy(
            isRunning = false,
            isPaused = false,
            statusMessage = "Generation cancelled by user."
        )
    }

    private fun launchFullGenerationPipeline(
        form: GeneratorFormState,
        resumeProject: VideoProject?
    ) {
        activeGenerationJob?.cancel()
        isPausedFlag.set(false)
        activeGenerationJob = viewModelScope.launch {
            val settings = settingsState.value
            val chosenModel = if (settings.preferredTextModel in FreeModeManager.APPROVED_FREE_CLOUD_MODELS) {
                settings.preferredTextModel
            } else {
                FreeModeManager.PRIMARY_FREE_MODEL
            }
            val currentMusicUri = backgroundMusicUri.value
            val currentMusicName = backgroundMusicName.value.orEmpty()
            var currentProject = resumeProject ?: VideoProject(
                id = "proj_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = currentUserId,
                title = form.prompt.trim().take(48).replaceFirstChar { it.uppercase() },
                prompt = form.prompt.trim(),
                durationMinutes = form.duration.minutes,
                aspectRatio = form.aspectRatio.code,
                videoStyle = form.videoStyle.label,
                language = form.language.label,
                narrationStyle = form.narrationStyle.label,
                status = "generating",
                currentStageIndex = 1,
                freeModeUsed = true,
                modelUsed = chosenModel,
                visualSourceMode = if (form.attachedImagePaths.isNotEmpty()) "user_images_and_slides" else "free_animated_slides",
                brandingEnabled = form.brandingEnabled,
                outroDurationSec = form.outroDurationSec,
                backgroundMusicUri = currentMusicUri.orEmpty(),
                backgroundMusicName = currentMusicName
            )

            try {
                // Stage 1: Analyze topic & workspace
                updatePipeline(GenerationStage.UNDERSTANDING_PROMPT, 0.08f, "1. Analyzing topic & preparing local workspace...")
                if (resumeProject == null) {
                    val createdId = runCatching { repository.createProject(currentProject) }.getOrNull()
                    if (!createdId.isNullOrBlank()) {
                        currentProject = currentProject.copy(id = createdId)
                    }
                } else {
                    currentProject = currentProject.copy(status = "generating", currentStageIndex = 1)
                    runCatching { repository.updateProject(currentProject) }
                }
                persistProjectLocally(currentProject)
                _selectedProjectId.value = currentProject.id
                val projectDir = File(projectsRootDir, currentProject.id).apply { mkdirs() }

                checkPausePoint()

                // Check if resuming a project that already has saved scenes
                var existingScenes = localProjectStore.loadScenes(currentProject.id)
                if (existingScenes.isEmpty()) {
                    existingScenes = selectedScenesState.value.filter { it.projectId == currentProject.id }
                }

                val savedScenes: MutableList<VideoScene>
                if (resumeProject != null && existingScenes.isNotEmpty() && currentProject.openingHook.isNotBlank()) {
                    updatePipeline(GenerationStage.DIVIDING_SCENES, 0.35f, "Resuming from saved ${existingScenes.size}-scene script...", currentProject.id)
                    savedScenes = existingScenes.toMutableList()
                } else {
                    // Stage 2 & 3: Outline, Fact-check & Full calibrated narration script (Free Cloud Model)
                    updatePipeline(GenerationStage.RESEARCH_FACT_CHECK, 0.16f, "2. Creating structured outline & accuracy rules...", currentProject.id)
                    currentProject = currentProject.copy(currentStageIndex = 2)
                    persistProjectLocally(currentProject)
                    runCatching { repository.updateProject(currentProject) }

                    checkPausePoint()

                    updatePipeline(GenerationStage.WRITING_SCRIPT, 0.24f, "3. Writing full narration script via $chosenModel (Free Tier)...", currentProject.id)
                    val scriptResult = geminiEngine.generateDocumentaryScript(
                        userId = currentUserId,
                        projectId = currentProject.id,
                        prompt = form.prompt.trim(),
                        durationMinutes = form.duration.minutes,
                        aspectRatio = form.aspectRatio.code,
                        videoStyle = form.videoStyle.label,
                        language = form.language,
                        narrationStyle = form.narrationStyle.label,
                        speakingSpeed = settings.speakingSpeed.toFloat(),
                        preferredModelId = chosenModel
                    )

                    val scriptBundle = scriptResult.getOrElse { err ->
                        runCatching {
                            repository.recordUsageLog(
                                UsageLog(
                                    projectId = currentProject.id,
                                    modelName = chosenModel,
                                    operation = "script_generation",
                                    isFreeTier = true,
                                    estimatedTokens = 0L,
                                    status = if (err.message?.contains("quota", ignoreCase = true) == true) "quota_exhausted" else "error",
                                    detail = err.message.orEmpty().take(280)
                                )
                            )
                        }
                        throw err
                    }

                    runCatching {
                        repository.recordUsageLog(
                            UsageLog(
                                projectId = currentProject.id,
                                modelName = scriptBundle.modelUsed,
                                operation = "script_generation",
                                isFreeTier = true,
                                estimatedTokens = scriptBundle.estimatedTokens,
                                status = "success",
                                detail = "Generated ${scriptBundle.scenes.size} scenes (~${scriptBundle.estimatedNarrationSeconds / 60}m${scriptBundle.estimatedNarrationSeconds % 60}s)"
                            )
                        )
                    }

                    checkPausePoint()

                    // Stage 4: Divide scenes & attach any user-picked photos sequentially
                    val mergedScenes = scriptBundle.scenes.mapIndexed { idx, sc ->
                        val attachedPath = form.attachedImagePaths.getOrNull(idx).orEmpty()
                        if (attachedPath.isNotBlank() && File(attachedPath).exists()) {
                            val sceneImgDest = File(projectDir, "user_scene_${idx + 1}.jpg")
                            File(attachedPath).copyTo(sceneImgDest, overwrite = true)
                            sc.copy(userImagePath = sceneImgDest.absolutePath, visualSourceType = "user_uploaded_image")
                        } else {
                            sc
                        }
                    }

                    val hasUserImgs = mergedScenes.any { it.userImagePath.isNotBlank() }
                    updatePipeline(GenerationStage.DIVIDING_SCENES, 0.36f, "4. Saved ${mergedScenes.size} timed scenes (${scriptBundle.estimatedNarrationSeconds}s est.)...", currentProject.id)
                    currentProject = currentProject.copy(
                        title = scriptBundle.youtubeTitle.ifBlank { currentProject.title },
                        status = "script_ready",
                        currentStageIndex = 4,
                        scriptTopic = scriptBundle.topic,
                        targetAudience = scriptBundle.targetAudience,
                        scriptOutline = scriptBundle.scriptOutline,
                        openingHook = scriptBundle.openingHook,
                        conclusion = scriptBundle.conclusion,
                        youtubeTitle = scriptBundle.youtubeTitle,
                        youtubeDescription = scriptBundle.youtubeDescription,
                        hashtags = scriptBundle.hashtags,
                        thumbnailText = scriptBundle.thumbnailText,
                        factCheckNotes = scriptBundle.factCheckNotes,
                        sourcesOrUncertaintyNotes = scriptBundle.sourcesOrUncertaintyNotes,
                        estimatedNarrationSeconds = scriptBundle.estimatedNarrationSeconds,
                        modelUsed = scriptBundle.modelUsed,
                        visualSourceMode = if (hasUserImgs) "user_images_and_slides" else "free_animated_slides"
                    )
                    persistProjectLocally(currentProject, mergedScenes)
                    runCatching {
                        repository.updateProject(currentProject)
                        repository.replaceProjectScenes(currentProject.id, mergedScenes)
                    }
                    savedScenes = mergedScenes.toMutableList()
                }

                val resW = if (settings.exportResolution == "1080p") form.aspectRatio.width1080 else form.aspectRatio.width720
                val resH = if (settings.exportResolution == "1080p") form.aspectRatio.height1080 else form.aspectRatio.height720

                // Stage 5: Visuals (User Images + Free Animated Slides) — 100% Local
                freeModeManager.markLocalOperation("media3-transformer-local", "slide_rendering")
                updatePipeline(GenerationStage.GENERATING_VISUALS, 0.42f, "5. Preparing animated visual slides & user images locally...", currentProject.id)

                val thumbFile = File(projectDir, "thumbnail.png")
                for (i in savedScenes.indices) {
                    checkPausePoint()
                    val scene = savedScenes[i]
                    val imgFile = File(projectDir, "scene_${i + 1}.png")
                    val (_, sourceType) = withContext(Dispatchers.Default) {
                        visualEngine.renderSceneVisualFrame(
                            scene = scene,
                            totalScenes = savedScenes.size,
                            videoStyle = form.videoStyle.label,
                            width = resW,
                            height = resH,
                            outputFile = imgFile
                        )
                    }
                    savedScenes[i] = scene.copy(
                        visualSourceType = sourceType,
                        status = "visual_ready",
                        localImagePath = imgFile.absolutePath
                    )
                    val p = 0.42f + 0.16f * ((i + 1).toFloat() / savedScenes.size.coerceAtLeast(1))
                    updatePipeline(GenerationStage.GENERATING_VISUALS, p, "Prepared slide ${i + 1}/${savedScenes.size}", currentProject.id)
                }

                withContext(Dispatchers.Default) {
                    visualEngine.renderYoutubeThumbnail(
                        titleText = currentProject.thumbnailText.ifBlank { currentProject.youtubeTitle },
                        subtitleBadge = form.videoStyle.label,
                        subjectImagePath = savedScenes.firstOrNull { it.userImagePath.isNotBlank() }?.userImagePath,
                        outputFile = thumbFile
                    )
                }
                val anyUserPhotos = savedScenes.any { it.visualSourceType == "user_uploaded_image" }
                currentProject = currentProject.copy(
                    currentStageIndex = 5,
                    visualSourceMode = if (anyUserPhotos) "user_images_and_slides" else "free_animated_slides",
                    localThumbnailPath = thumbFile.absolutePath
                )
                persistProjectLocally(currentProject, savedScenes)
                runCatching {
                    repository.updateProject(currentProject)
                    repository.replaceProjectScenes(currentProject.id, savedScenes)
                }

                // Stage 6: On-device free TTS narration
                freeModeManager.markLocalOperation("android-google-tts", "voice_synthesis")
                updatePipeline(GenerationStage.GENERATING_NARRATION, 0.60f, "6. Synthesizing ${form.language.label} narration on-device...", currentProject.id)
                val sceneWavFiles = mutableListOf<File>()
                val segmentDurationsMs = mutableListOf<Long>()
                var ttsWarningShown: String? = null

                for (i in savedScenes.indices) {
                    checkPausePoint()
                    val scene = savedScenes[i]
                    val wavFile = File(projectDir, "scene_${i + 1}.wav")
                    val ttsResult = voiceEngine.synthesizeNarrationToWav(
                        text = scene.narration.ifBlank { scene.title },
                        language = form.language,
                        narrationStyle = form.narrationStyle,
                        voiceGender = settings.preferredVoiceGender,
                        speakingSpeed = settings.speakingSpeed.toFloat(),
                        outputFile = wavFile
                    )
                    val synth = ttsResult.getOrNull()
                    if (synth != null) {
                        if (synth.fallbackWarning != null && ttsWarningShown == null) {
                            ttsWarningShown = synth.fallbackWarning
                        }
                        sceneWavFiles.add(synth.wavFile)
                        val durMs = synth.durationMs.coerceAtLeast(4000L)
                        segmentDurationsMs.add(durMs)
                        savedScenes[i] = scene.copy(
                            status = "audio_ready",
                            localAudioPath = synth.wavFile.absolutePath,
                            estimatedSeconds = (durMs / 1000L).toInt().coerceAtLeast(4)
                        )
                    } else {
                        val fallbackSec = freeModeManager.estimateSpeechDurationSeconds(
                            scene.narration,
                            settings.speakingSpeed.toFloat(),
                            form.language
                        )
                        segmentDurationsMs.add((fallbackSec * 1000L).coerceAtLeast(4000L))
                    }
                    val p = 0.60f + 0.14f * ((i + 1).toFloat() / savedScenes.size.coerceAtLeast(1))
                    updatePipeline(GenerationStage.GENERATING_NARRATION, p, "Synthesized narration ${i + 1}/${savedScenes.size}", currentProject.id)
                }

                val masterWav = File(projectDir, "full_narration.wav")
                voiceEngine.mergeSceneWavFiles(sceneWavFiles, masterWav)
                freeModeManager.recordLocalSuccess("android-google-tts", "tts_narration")
                runCatching {
                    repository.recordUsageLog(
                        UsageLog(
                            projectId = currentProject.id,
                            modelName = "android-google-tts",
                            operation = "tts_narration",
                            isFreeTier = true,
                            estimatedTokens = savedScenes.sumOf { it.narration.length.toLong() },
                            status = "success",
                            detail = ttsWarningShown ?: "Synthesized ${savedScenes.size} scenes in ${form.language.label}"
                        )
                    )
                }

                checkPausePoint()

                // Stage 7 & 8: Subtitles & Branding Intro/Outro
                updatePipeline(GenerationStage.CREATING_SUBTITLES, 0.76f, "7. Generating synchronized SRT captions...", currentProject.id)
                val posCode = logoPosition.value
                val sizeCode = logoSize.value
                val timeline = mutableListOf<TimelineSegment>()
                if (form.brandingEnabled) {
                    val introImg = File(projectDir, "branding_intro.png")
                    withContext(Dispatchers.Default) {
                        visualEngine.renderBrandingIntroFrame(
                            videoTitle = currentProject.title,
                            channelName = settings.channelName,
                            logoUriString = customLogoUri.value,
                            logoPosition = posCode,
                            logoSize = sizeCode,
                            width = resW,
                            height = resH,
                            outputFile = introImg
                        )
                    }
                    timeline.add(
                        TimelineSegment(
                            title = "Intro",
                            subtitleText = "",
                            imageFile = introImg,
                            audioFile = null,
                            durationMs = 3000L,
                            isBrandingCard = true,
                            videoStyle = form.videoStyle.label
                        )
                    )
                }

                savedScenes.forEachIndexed { idx, sc ->
                    timeline.add(
                        TimelineSegment(
                            title = sc.title,
                            subtitleText = sc.narration,
                            imageFile = File(sc.localImagePath),
                            audioFile = sc.localAudioPath.takeIf { it.isNotBlank() }?.let { File(it) },
                            durationMs = segmentDurationsMs.getOrElse(idx) { 5000L },
                            isBrandingCard = false,
                            accentHex = sc.accentHex,
                            videoStyle = form.videoStyle.label,
                            factType = sc.factType,
                            sceneNumber = idx + 1,
                            totalScenes = savedScenes.size
                        )
                    )
                }

                if (form.brandingEnabled) {
                    val outroImg = File(projectDir, "branding_outro.png")
                    withContext(Dispatchers.Default) {
                        visualEngine.renderBrandingOutroFrame(
                            tagline = settings.outroTagline,
                            channelName = settings.channelName,
                            logoUriString = customLogoUri.value,
                            logoPosition = posCode,
                            logoSize = sizeCode,
                            width = resW,
                            height = resH,
                            outputFile = outroImg
                        )
                    }
                    timeline.add(
                        TimelineSegment(
                            title = "Outro",
                            subtitleText = "",
                            imageFile = outroImg,
                            audioFile = null,
                            durationMs = form.outroDurationSec * 1000L,
                            isBrandingCard = true,
                            videoStyle = form.videoStyle.label
                        )
                    )
                }

                val srtFile = File(projectDir, "subtitles.srt")
                withContext(Dispatchers.IO) {
                    videoEngine.generateSrtFile(timeline, srtFile)
                }

                updatePipeline(GenerationStage.COMBINING_SCENES, 0.80f, "8. Combining ${timeline.size} timeline segments & branding...", currentProject.id)
                checkPausePoint()

                // Stage 9: Assemble MP4 via Media3 Transformer + Ken Burns slide renderer
                freeModeManager.markLocalOperation("media3-transformer-local", "mp4_assembly")
                updatePipeline(GenerationStage.RENDERING_VIDEO, 0.82f, "9. Assembling MP4 (${form.aspectRatio.code}, ${settings.exportResolution})...", currentProject.id)
                val mp4File = File(projectDir, "final_video.mp4")
                val renderResult = videoEngine.assembleMp4Video(
                    segments = timeline,
                    requestedWidth = resW,
                    requestedHeight = resH,
                    includeBackgroundMusic = settings.backgroundMusicEnabled,
                    customMusicUri = currentMusicUri,
                    outputMp4File = mp4File,
                    voiceEngine = voiceEngine,
                    isPaused = isPausedFlag,
                    onProgress = { pct, msg ->
                        val mapped = 0.82f + pct * 0.16f
                        _pipelineState.value = _pipelineState.value.copy(
                            currentStageIndex = GenerationStage.RENDERING_VIDEO.index,
                            stageProgress = mapped.coerceIn(0.82f, 0.98f),
                            statusMessage = msg
                        )
                    }
                ).getOrThrow()

                freeModeManager.recordLocalSuccess("media3-transformer-local", "mp4_assembly")

                // Stage 10: Final local + cloud persistence
                val completedScenes = savedScenes.map { it.copy(status = "completed") }
                val actualAudioDurationSec = (timeline.sumOf { it.durationMs } / 1000L).toInt()
                val totalBytes = projectDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val finalProject = currentProject.copy(
                    status = "completed",
                    currentStageIndex = 10,
                    estimatedNarrationSeconds = actualAudioDurationSec,
                    localVideoPath = renderResult.outputFile.absolutePath,
                    localAudioPath = masterWav.absolutePath,
                    localSrtPath = srtFile.absolutePath,
                    localThumbnailPath = thumbFile.absolutePath,
                    estimatedSizeBytes = totalBytes
                )
                persistProjectLocally(finalProject, completedScenes)
                runCatching {
                    repository.replaceProjectScenes(currentProject.id, completedScenes)
                    repository.updateProject(finalProject)
                }
                refreshStorageUsage()
                _pipelineState.value = PipelineTrackerState(
                    isRunning = false,
                    isPaused = false,
                    currentStageIndex = 10,
                    stageProgress = 1f,
                    statusMessage = "Export complete (${renderResult.actualWidth}x${renderResult.actualHeight}) via ${renderResult.rendererLabel}",
                    activeProjectId = currentProject.id,
                    rendererLabel = renderResult.rendererLabel
                )
                _actionBanner.value = ttsWarningShown ?: "Animated visual-slide MP4 exported! Open Video Library to preview & share."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video pipeline error", e)
                val failedProj = currentProject.copy(status = "error")
                persistProjectLocally(failedProj)
                runCatching { repository.updateProject(failedProj) }
                _pipelineState.value = _pipelineState.value.copy(
                    isRunning = false,
                    isPaused = false,
                    errorMessage = e.localizedMessage ?: "Pipeline error occurred.",
                    statusMessage = "Generation paused/failed — project saved locally so you can resume",
                    activeProjectId = currentProject.id
                )
            }
        }
    }

    fun saveScriptEdits(project: VideoProject, updatedScenes: List<VideoScene>) {
        viewModelScope.launch {
            try {
                val lang = StudioLanguage.entries.firstOrNull { it.label == project.language } ?: StudioLanguage.ENGLISH
                val speed = settingsState.value.speakingSpeed.toFloat()
                val calibratedScenes = updatedScenes.map { sc ->
                    val realSec = freeModeManager.estimateSpeechDurationSeconds(sc.narration, speed, lang)
                    sc.copy(estimatedSeconds = realSec.coerceIn(4, 300))
                }
                val totalEst = freeModeManager.estimateTotalScriptDurationSeconds(
                    project.openingHook,
                    calibratedScenes,
                    project.conclusion,
                    speed,
                    lang
                )
                val updatedProject = project.copy(
                    status = if (project.status == "completed") "completed" else "script_ready",
                    estimatedNarrationSeconds = totalEst
                )
                persistProjectLocally(updatedProject, calibratedScenes)
                runCatching {
                    repository.updateProject(updatedProject)
                    repository.replaceProjectScenes(project.id, calibratedScenes)
                }
                _actionBanner.value = "Script saved (${totalEst / 60}m ${totalEst % 60}s estimated speech)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionBanner.value = "Failed to save script: ${e.localizedMessage}"
            }
        }
    }

    fun expandProjectScript(project: VideoProject, currentScenes: List<VideoScene>) {
        if (currentScenes.isEmpty()) return
        viewModelScope.launch {
            _isScriptActionBusy.value = true
            try {
                val lang = StudioLanguage.entries.firstOrNull { it.label == project.language } ?: StudioLanguage.ENGLISH
                val speed = settingsState.value.speakingSpeed.toFloat()
                val expandedScenes = geminiEngine.expandScriptToTargetDuration(
                    project = project,
                    currentScenes = currentScenes,
                    language = lang,
                    speakingSpeed = speed
                ).getOrThrow()

                val totalEst = freeModeManager.estimateTotalScriptDurationSeconds(
                    project.openingHook,
                    expandedScenes,
                    project.conclusion,
                    speed,
                    lang
                )
                val updatedProject = project.copy(estimatedNarrationSeconds = totalEst)
                persistProjectLocally(updatedProject, expandedScenes)
                runCatching {
                    repository.replaceProjectScenes(project.id, expandedScenes)
                    repository.updateProject(updatedProject)
                    repository.recordUsageLog(
                        UsageLog(
                            projectId = project.id,
                            modelName = freeModeStatus.value.activeModelId,
                            operation = "script_expansion",
                            isFreeTier = true,
                            estimatedTokens = expandedScenes.sumOf { it.narration.length.toLong() / 4L },
                            status = "success",
                            detail = "Expanded ${expandedScenes.size} scenes to ~${totalEst / 60}m ${totalEst % 60}s"
                        )
                    )
                }
                _actionBanner.value = "Expanded script to ~${totalEst / 60}m ${totalEst % 60}s."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionBanner.value = "Expand failed: ${e.localizedMessage}"
            } finally {
                _isScriptActionBusy.value = false
            }
        }
    }

    fun regenerateSection(
        project: VideoProject,
        sectionName: String,
        currentText: String,
        onSectionUpdated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isScriptActionBusy.value = true
            try {
                val lang = StudioLanguage.entries.firstOrNull { it.label == project.language } ?: StudioLanguage.ENGLISH
                val newText = geminiEngine.regenerateScriptSection(
                    sectionName = sectionName,
                    project = project,
                    currentText = currentText,
                    language = lang
                ).getOrThrow()
                val updatedProject = when (sectionName) {
                    "Opening Hook" -> project.copy(openingHook = newText)
                    "Conclusion" -> project.copy(conclusion = newText)
                    else -> project
                }
                persistProjectLocally(updatedProject)
                runCatching {
                    repository.updateProject(updatedProject)
                    repository.recordUsageLog(
                        UsageLog(
                            projectId = project.id,
                            modelName = freeModeStatus.value.activeModelId,
                            operation = "section_regeneration",
                            isFreeTier = true,
                            estimatedTokens = (newText.length / 4L).coerceAtLeast(60L),
                            status = "success",
                            detail = "Regenerated $sectionName"
                        )
                    )
                }
                onSectionUpdated(newText)
                _actionBanner.value = "$sectionName regenerated."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionBanner.value = "Failed to regenerate $sectionName: ${e.localizedMessage}"
            } finally {
                _isScriptActionBusy.value = false
            }
        }
    }

    fun regenerateScene(project: VideoProject, scene: VideoScene) {
        viewModelScope.launch {
            _isScriptActionBusy.value = true
            try {
                val lang = StudioLanguage.entries.firstOrNull { it.label == project.language } ?: StudioLanguage.ENGLISH
                val res = geminiEngine.regenerateSingleScene(
                    projectPrompt = project.prompt,
                    videoStyle = project.videoStyle,
                    language = lang,
                    narrationStyle = project.narrationStyle,
                    speakingSpeed = settingsState.value.speakingSpeed.toFloat(),
                    scene = scene
                )
                val updated = res.getOrThrow()
                val existingList = _localScenesMap.value[project.id] ?: selectedScenesState.value
                val newList = existingList.map { if (it.id == updated.id || it.sceneIndex == updated.sceneIndex) updated else it }
                persistProjectLocally(project, newList)
                runCatching {
                    repository.updateScene(project.id, updated)
                    repository.recordUsageLog(
                        UsageLog(
                            projectId = project.id,
                            modelName = freeModeStatus.value.activeModelId,
                            operation = "scene_regeneration",
                            isFreeTier = true,
                            estimatedTokens = 350L,
                            status = "success",
                            detail = "Regenerated Scene ${scene.sceneIndex + 1}"
                        )
                    )
                }
                _actionBanner.value = "Scene ${scene.sceneIndex + 1} regenerated."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionBanner.value = "Regeneration failed: ${e.localizedMessage}"
            } finally {
                _isScriptActionBusy.value = false
            }
        }
    }

    fun synthesizeStandaloneVoice(
        text: String,
        language: StudioLanguage,
        narrationStyle: NarrationStyleOption,
        voiceGender: String,
        speed: Float
    ) {
        viewModelScope.launch {
            freeModeManager.markLocalOperation("android-google-tts", "voice_preview")
            _voicePreviewStatus.value = "Synthesizing ${language.label} narration..."
            val previewFile = File(projectsRootDir, "voice_studio_preview.wav")
            val result = voiceEngine.synthesizeNarrationToWav(
                text = text,
                language = language,
                narrationStyle = narrationStyle,
                voiceGender = voiceGender,
                speakingSpeed = speed,
                outputFile = previewFile
            )
            result.onSuccess { synth ->
                freeModeManager.recordLocalSuccess("android-google-tts", "voice_preview")
                val warn = synth.fallbackWarning?.let { "\nNote: $it" }.orEmpty()
                _voicePreviewStatus.value = "Ready (${synth.durationMs / 1000}s) • ${synth.engineLabel}$warn"
                _isPlayingAudioPreview.value = true
                voiceEngine.playPreview(
                    file = synth.wavFile,
                    onComplete = { _isPlayingAudioPreview.value = false },
                    onError = { err ->
                        _isPlayingAudioPreview.value = false
                        _voicePreviewStatus.value = "Playback error: $err"
                    }
                )
            }.onFailure { err ->
                _voicePreviewStatus.value = "TTS failed: ${err.localizedMessage}"
            }
        }
    }

    fun playAudioFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            _actionBanner.value = "Audio file not found on device."
            return
        }
        _isPlayingAudioPreview.value = true
        voiceEngine.playPreview(
            file = file,
            onComplete = { _isPlayingAudioPreview.value = false },
            onError = { err ->
                _isPlayingAudioPreview.value = false
                _actionBanner.value = "Playback error: $err"
            }
        )
    }

    fun stopAudioPreview() {
        voiceEngine.stopPreview()
        _isPlayingAudioPreview.value = false
    }

    fun regenerateFreeThumbnail(project: VideoProject, headline: String) {
        viewModelScope.launch {
            try {
                val projectDir = File(projectsRootDir, project.id).apply { mkdirs() }
                val thumbFile = File(projectDir, "thumbnail.png")
                val scenes = _localScenesMap.value[project.id].orEmpty()
                val userImg = scenes.firstOrNull { it.userImagePath.isNotBlank() }?.userImagePath

                withContext(Dispatchers.Default) {
                    visualEngine.renderYoutubeThumbnail(
                        titleText = headline,
                        subtitleBadge = project.videoStyle,
                        subjectImagePath = userImg,
                        outputFile = thumbFile
                    )
                }
                val updated = project.copy(
                    thumbnailText = headline,
                    localThumbnailPath = thumbFile.absolutePath
                )
                persistProjectLocally(updated)
                runCatching { repository.updateProject(updated) }
                refreshStorageUsage()
                _actionBanner.value = "16:9 Thumbnail updated (100% Free)!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionBanner.value = "Thumbnail error: ${e.localizedMessage}"
            }
        }
    }

    fun generateBrandingPreview(channelName: String, outroTagline: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val previewDir = File(projectsRootDir, "branding_preview")
            val (intro, outro) = visualEngine.renderBrandingPreviewPair(
                channelName = channelName.ifBlank { "XAVIOUR EXPLAINS" },
                tagline = outroTagline.ifBlank { "THINK BEYOND. EXPLORE EVERYTHING." },
                logoUriString = customLogoUri.value,
                logoPosition = logoPosition.value,
                logoSize = logoSize.value,
                previewDir = previewDir
            )
            _brandingPreviewPaths.value = intro.absolutePath to outro.absolutePath
        }
    }

    fun dismissBrandingPreview() {
        _brandingPreviewPaths.value = null
    }

    fun renameProject(projectId: String, newTitle: String) {
        viewModelScope.launch {
            val current = _localProjects.value.firstOrNull { it.id == projectId }
            if (current != null) {
                persistProjectLocally(current.copy(title = newTitle.trim().take(160)))
            }
            runCatching { repository.renameProject(projectId, newTitle) }
                .onSuccess { _actionBanner.value = "Project renamed." }
                .onFailure {
                    if (current == null) _actionBanner.value = "Rename failed: ${it.localizedMessage}"
                    else _actionBanner.value = "Renamed locally."
                }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            localProjectStore.deleteProject(projectId)
            _localProjects.value = _localProjects.value.filterNot { it.id == projectId }
            _localScenesMap.value = _localScenesMap.value - projectId
            if (_selectedProjectId.value == projectId) _selectedProjectId.value = null
            runCatching { repository.deleteProject(projectId) }
            refreshStorageUsage()
            _actionBanner.value = "Project deleted."
        }
    }

    fun updateSettings(settings: StudioSettings) {
        viewModelScope.launch {
            val safeModel = if (settings.preferredTextModel in FreeModeManager.APPROVED_FREE_CLOUD_MODELS) {
                settings.preferredTextModel
            } else {
                FreeModeManager.PRIMARY_FREE_MODEL
            }
            val enforced = settings.copy(
                userId = currentUserId,
                freeModeEnabled = true,
                preferredTextModel = safeModel
            )
            prefsStore.setLogoPlacement(enforced.logoPosition, enforced.logoSize)
            runCatching { repository.saveStudioSettings(enforced) }
                .onFailure { _actionBanner.value = "Settings sync failed: ${it.localizedMessage}" }
        }
    }

    fun saveCustomLogoUri(uriString: String?) {
        viewModelScope.launch {
            prefsStore.setCustomLogoUri(uriString)
            _actionBanner.value = if (uriString == null) "Reset to placeholder logo." else "Channel logo updated (exact aspect preserved)."
        }
    }

    fun refreshStorageUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val total = if (projectsRootDir.exists()) {
                projectsRootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
            _storageBytes.value = total
        }
    }

    private suspend fun persistProjectLocally(project: VideoProject, scenes: List<VideoScene>? = null) {
        localProjectStore.upsertProject(project)
        val existing = _localProjects.value.toMutableList()
        val idx = existing.indexOfFirst { it.id == project.id }
        if (idx >= 0) existing[idx] = project else existing.add(0, project)
        _localProjects.value = existing
        if (scenes != null) {
            localProjectStore.saveScenes(project.id, scenes)
            _localScenesMap.value = _localScenesMap.value + (project.id to scenes)
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private suspend fun checkPausePoint() {
        while (isPausedFlag.get()) {
            delay(150L)
        }
    }

    private fun updatePipeline(
        stage: GenerationStage,
        progress: Float,
        message: String,
        projectId: String? = _pipelineState.value.activeProjectId
    ) {
        _pipelineState.value = PipelineTrackerState(
            isRunning = true,
            isPaused = isPausedFlag.get(),
            currentStageIndex = stage.index,
            stageProgress = progress,
            statusMessage = message,
            errorMessage = null,
            activeProjectId = projectId
        )
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.stopPreview()
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                val repo = StudioRepository(db)
                val freeManager = FreeModeManager()
                val rootDir = File(app.filesDir, "xaviour_projects").apply { mkdirs() }
                StudioViewModel(
                    repository = repo,
                    freeModeManager = freeManager,
                    geminiEngine = GeminiStudioEngine(freeManager),
                    visualEngine = VisualStudioEngine(app),
                    voiceEngine = VoiceStudioEngine(app),
                    videoEngine = VideoAssemblerEngine(app),
                    prefsStore = StudioPreferencesStore(app),
                    localProjectStore = LocalProjectStore(rootDir),
                    contentResolver = app.contentResolver,
                    projectsRootDir = rootDir,
                    currentUserId = currentUserId
                )
            }
        }
    }
}
