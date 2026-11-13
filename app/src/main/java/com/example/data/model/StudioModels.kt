package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class VideoProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val durationMinutes: Int = 5,
    val aspectRatio: String = "16:9",
    val videoStyle: String = "Cinematic Documentary",
    val language: String = "English",
    val narrationStyle: String = "Deep documentary",
    val status: String = "draft",
    val currentStageIndex: Int = 0,
    val scriptTopic: String = "",
    val targetAudience: String = "",
    val scriptOutline: String = "",
    val openingHook: String = "",
    val conclusion: String = "",
    val youtubeTitle: String = "",
    val youtubeDescription: String = "",
    val hashtags: List<String> = emptyList(),
    val thumbnailText: String = "",
    val factCheckNotes: String = "",
    val sourcesOrUncertaintyNotes: String = "",
    val estimatedNarrationSeconds: Int = 0,
    val freeModeUsed: Boolean = true,
    val modelUsed: String = "gemini-3.8-flash",
    val visualSourceMode: String = "free_animated_slides",
    val brandingEnabled: Boolean = true,
    val outroDurationSec: Int = 5,
    val backgroundMusicUri: String = "",
    val backgroundMusicName: String = "",
    val localVideoPath: String = "",
    val localAudioPath: String = "",
    val localSrtPath: String = "",
    val localThumbnailPath: String = "",
    val estimatedSizeBytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoScene(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val sceneIndex: Int = 0,
    val title: String = "",
    val narration: String = "",
    val visualPrompt: String = "",
    val estimatedSeconds: Int = 20,
    val factType: String = "verified_fact",
    val visualSourceType: String = "pending",
    val status: String = "pending",
    val accentHex: String = "#2979FF",
    val userImagePath: String = "",
    val localImagePath: String = "",
    val localAudioPath: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsageLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val modelName: String = "",
    val operation: String = "",
    val isFreeTier: Boolean = true,
    val estimatedTokens: Long = 0L,
    val status: String = "success",
    val detail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudioSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val freeModeEnabled: Boolean = true,
    val preferredTextModel: String = "gemini-3.8-flash",
    val preferredVoiceGender: String = "female",
    val preferredLanguage: String = "English",
    val speakingSpeed: Double = 1.0,
    val brandingEnabled: Boolean = true,
    val channelName: String = "XAVIOUR EXPLAINS",
    val outroTagline: String = "THINK BEYOND. EXPLORE EVERYTHING.",
    val outroDurationSec: Int = 5,
    val logoPosition: String = "top_center",
    val logoSize: String = "medium",
    val backgroundMusicEnabled: Boolean = true,
    val exportResolution: String = "1080p",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class DurationOption(val minutes: Int, val label: String, val recommendedScenes: Int) {
    THREE_MIN(3, "3 minutes", 7),
    FIVE_MIN(5, "5 minutes", 10),
    TEN_MIN(10, "10 minutes", 14),
    FIFTEEN_MIN(15, "15 minutes", 18)
}

enum class AspectRatioOption(val code: String, val label: String, val width720: Int, val height720: Int, val width1080: Int, val height1080: Int) {
    LANDSCAPE_16_9("16:9", "16:9 YouTube Video", 1280, 720, 1920, 1080),
    PORTRAIT_9_16("9:16", "9:16 Vertical Video", 720, 1280, 1080, 1920),
    SQUARE_1_1("1:1", "1:1 Square Video", 720, 720, 1080, 1080)
}

enum class VideoStyleOption(val label: String) {
    CINEMATIC_DOCUMENTARY("Cinematic Documentary"),
    SCIENCE_EXPLAINER("Science Explainer"),
    SPACE_DOCUMENTARY("Space Documentary"),
    TECHNOLOGY_EXPLAINER("Technology Explainer"),
    HISTORICAL_DOCUMENTARY("Historical Documentary"),
    MYSTERY_DOCUMENTARY("Mystery Documentary"),
    EDUCATIONAL_ANIMATION("Educational Animation"),
    REALISTIC_CINEMATIC("Realistic Cinematic"),
    FUTURISTIC_SCIFI("Futuristic Science Fiction")
}

enum class StudioLanguage(val label: String, val localeTag: String) {
    ENGLISH("English", "en-US"),
    TELUGU("Telugu", "te-IN"),
    HINDI("Hindi", "hi-IN")
}

enum class NarrationStyleOption(val label: String, val defaultPitch: Float, val defaultSpeed: Float) {
    DEEP_DOCUMENTARY("Deep documentary", 0.88f, 0.94f),
    PROFESSIONAL_NARRATOR("Professional narrator", 1.0f, 1.0f),
    FRIENDLY_EDUCATIONAL("Friendly educational", 1.06f, 1.02f),
    DRAMATIC_STORYTELLING("Dramatic storytelling", 0.95f, 0.96f)
}

enum class LogoPositionOption(val code: String, val label: String) {
    TOP_CENTER("top_center", "Top Center"),
    TOP_LEFT("top_left", "Top Left"),
    TOP_RIGHT("top_right", "Top Right"),
    CENTER("center", "Center"),
    BOTTOM_RIGHT("bottom_right", "Bottom Right")
}

enum class LogoSizeOption(val code: String, val label: String, val introDpScale: Float, val outroDpScale: Float) {
    SMALL("small", "Small", 0.78f, 0.78f),
    MEDIUM("medium", "Medium", 1.0f, 1.0f),
    LARGE("large", "Large", 1.32f, 1.28f)
}

enum class GenerationStage(val index: Int, val label: String) {
    UNDERSTANDING_PROMPT(1, "Analyze topic"),
    RESEARCH_FACT_CHECK(2, "Create structured outline & fact check"),
    WRITING_SCRIPT(3, "Write complete narration script"),
    DIVIDING_SCENES(4, "Divide into scenes & visual directions"),
    GENERATING_VISUALS(5, "Prepare animated visual slides & user images"),
    GENERATING_NARRATION(6, "Generate free narration audio"),
    CREATING_SUBTITLES(7, "Prepare subtitles & captions"),
    COMBINING_SCENES(8, "Combine scenes, music & branding"),
    RENDERING_VIDEO(9, "Assemble MP4 locally (Media3)"),
    FINAL_EXPORT(10, "Save project & export MP4")
}

data class GoogleModelInfo(
    val id: String,
    val displayName: String,
    val category: String,
    val isFreeTier: Boolean,
    val isLocalOnly: Boolean,
    val isBlockedInFreeMode: Boolean,
    val availabilitySummary: String,
    val quotaNote: String
)

object GoogleModelCatalog {
    val selectableFreeTextModels = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")

    val models = listOf(
        GoogleModelInfo(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            category = "Script, Outlines & Fact Notes",
            isFreeTier = true,
            isLocalOnly = false,
            isBlockedInFreeMode = false,
            availabilitySummary = "Cloud Free Tier • Primary Model",
            quotaNote = "Subject to Google AI free-tier RPM/daily quota; retries later if exhausted"
        ),
        GoogleModelInfo(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            category = "Fast Script & Fallback",
            isFreeTier = true,
            isLocalOnly = false,
            isBlockedInFreeMode = false,
            availabilitySummary = "Cloud Free Tier • Safe Fallback",
            quotaNote = "Lightweight free-tier fallback when primary model is busy or throttled"
        ),
        GoogleModelInfo(
            id = "android-google-tts",
            displayName = "Android TextToSpeech",
            category = "On-Device Narration",
            isFreeTier = true,
            isLocalOnly = true,
            isBlockedInFreeMode = false,
            availabilitySummary = "Local Device Engine • $0.00",
            quotaNote = "Depends on language & voice packs installed on this device"
        ),
        GoogleModelInfo(
            id = "media3-transformer-local",
            displayName = "Media3 Local Video Assembler",
            category = "Animated Visual-Slide MP4",
            isFreeTier = true,
            isLocalOnly = true,
            isBlockedInFreeMode = false,
            availabilitySummary = "Local Device Hardware • $0.00",
            quotaNote = "Renders animated slides, user photos, Ken Burns motion, subtitles & branding locally"
        ),
        GoogleModelInfo(
            id = "veo-video-api",
            displayName = "Google Veo / Paid Cloud Media",
            category = "Paid Cloud Video & Images",
            isFreeTier = false,
            isLocalOnly = false,
            isBlockedInFreeMode = true,
            availabilitySummary = "Permanently Disabled in Free-Only Mode",
            quotaNote = "No billing, paid APIs, or paid fallbacks exist in Xaviour AI Studio"
        )
    )
}
