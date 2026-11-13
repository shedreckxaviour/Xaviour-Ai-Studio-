package com.example.ai

import com.example.data.model.StudioLanguage
import com.example.data.model.VideoScene
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

enum class ExecutionLocation(val label: String) {
    CLOUD_FREE_TIER("Cloud AI (Free Tier)"),
    LOCAL_DEVICE("On-Device Local ($0.00)")
}

data class FreeModeStatus(
    val freeModeEnforced: Boolean = true,
    val activeModelId: String = "gemini-3.8-flash",
    val executionLocation: ExecutionLocation = ExecutionLocation.CLOUD_FREE_TIER,
    val requestStatus: String = "Ready",
    val sessionFreeRequestsUsed: Int = 0,
    val lastQuotaError: String? = null,
    val cooldownUntilMs: Long = 0L,
    val modelHealthMap: Map<String, String> = mapOf(
        "gemini-3.8-flash" to "Available (Free Tier)",
        "gemini-3.5-flash-lite" to "Available (Free Fallback)",
        "android-google-tts" to "Ready (On-Device)",
        "media3-transformer-local" to "Ready (On-Device)"
    )
)

class FreeModeManager {

    private val _status = MutableStateFlow(FreeModeStatus())
    val status: StateFlow<FreeModeStatus> = _status.asStateFlow()

    /**
     * Validates that an AI or media request strictly obeys Free-Only policy before execution.
     * Never selects or falls back to any paid model (Veo, Nano Banana, Argon, Imagen).
     */
    fun validateAndPrepareCloudRequest(
        requestedModelId: String,
        operation: String,
        nowMs: Long = System.currentTimeMillis()
    ): Result<String> {
        val current = _status.value
        if (!current.freeModeEnforced) {
            return Result.failure(IllegalStateException("Free Mode must always be enabled."))
        }

        if (requestedModelId in BLOCKED_PAID_MODELS || requestedModelId !in APPROVED_FREE_CLOUD_MODELS) {
            val blockedMsg = "Blocked by Free-Only Policy: '$requestedModelId' is not an approved free-tier model for $operation."
            _status.value = current.copy(
                requestStatus = "Blocked (Paid Model Disallowed)",
                lastQuotaError = blockedMsg
            )
            return Result.failure(SecurityException(blockedMsg))
        }

        val resolvedModel = if (nowMs < current.cooldownUntilMs && requestedModelId == PRIMARY_FREE_MODEL) {
            // Safe free-tier fallback within approved free models only
            FALLBACK_FREE_MODEL
        } else {
            requestedModelId
        }

        _status.value = current.copy(
            activeModelId = resolvedModel,
            executionLocation = ExecutionLocation.CLOUD_FREE_TIER,
            requestStatus = "Running $operation ($resolvedModel)",
            lastQuotaError = null
        )
        return Result.success(resolvedModel)
    }

    fun markLocalOperation(engineId: String, operation: String) {
        if (engineId !in APPROVED_LOCAL_ENGINES) return
        _status.value = _status.value.copy(
            activeModelId = engineId,
            executionLocation = ExecutionLocation.LOCAL_DEVICE,
            requestStatus = "Running $operation locally",
            lastQuotaError = null
        )
    }

    fun recordCloudSuccess(modelId: String, operation: String) {
        val current = _status.value
        val updatedHealth = current.modelHealthMap.toMutableMap()
        updatedHealth[modelId] = "Available (Free Tier)"
        _status.value = current.copy(
            activeModelId = modelId,
            executionLocation = ExecutionLocation.CLOUD_FREE_TIER,
            requestStatus = "Completed $operation",
            sessionFreeRequestsUsed = current.sessionFreeRequestsUsed + 1,
            lastQuotaError = null,
            modelHealthMap = updatedHealth
        )
    }

    fun recordLocalSuccess(engineId: String, operation: String) {
        val current = _status.value
        _status.value = current.copy(
            activeModelId = engineId,
            executionLocation = ExecutionLocation.LOCAL_DEVICE,
            requestStatus = "Completed $operation locally",
            lastQuotaError = null
        )
    }

    fun recordCloudFailure(modelId: String, error: Throwable, nowMs: Long = System.currentTimeMillis()): String {
        val raw = error.message.orEmpty()
        val isQuota = raw.contains("quota", ignoreCase = true) ||
            raw.contains("429", ignoreCase = true) ||
            raw.contains("resource_exhausted", ignoreCase = true) ||
            raw.contains("rate limit", ignoreCase = true)
        val isUnavailable = raw.contains("404", ignoreCase = true) ||
            raw.contains("not found", ignoreCase = true) ||
            raw.contains("unavailable", ignoreCase = true)

        val friendly = when {
            isQuota -> "Free quota limit reached for $modelId. No paid fallback will be used — please retry in a minute."
            isUnavailable -> "$modelId is temporarily unavailable on the free tier. Try switching to Gemini 3.5 Flash-Lite or retry shortly."
            raw.contains("app check", ignoreCase = true) || raw.contains("403", ignoreCase = true) ->
                "Firebase App Check verification failed for $modelId. Ensure this build is properly registered."
            raw.contains("network", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
                "Network timeout while contacting $modelId. Check connection and retry."
            else -> error.localizedMessage ?: "Free cloud AI request failed ($modelId)."
        }

        val current = _status.value
        val updatedHealth = current.modelHealthMap.toMutableMap()
        updatedHealth[modelId] = when {
            isQuota -> "Quota Exhausted — Retry Later"
            isUnavailable -> "Temporarily Unavailable"
            else -> "Error — Retry Later"
        }

        _status.value = current.copy(
            activeModelId = modelId,
            executionLocation = ExecutionLocation.CLOUD_FREE_TIER,
            requestStatus = if (isQuota) "Free Quota Exhausted" else "Request Failed",
            lastQuotaError = friendly,
            cooldownUntilMs = if (isQuota) nowMs + 60_000L else current.cooldownUntilMs,
            modelHealthMap = updatedHealth
        )
        return friendly
    }

    /**
     * Estimates spoken narration duration strictly from actual text length and speaking speed.
     * Never claims a short script lasts 15 minutes.
     */
    fun estimateSpeechDurationSeconds(
        text: String,
        speakingSpeed: Float,
        language: StudioLanguage
    ): Int {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return 0
        val speed = speakingSpeed.coerceIn(0.6f, 1.6f)
        val words = cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
        // Average documentary pace ~135 WPM in English; Indic scripts have fewer whitespace tokens per spoken second.
        val baseWordsPerMinute = when (language) {
            StudioLanguage.ENGLISH -> 135f
            StudioLanguage.TELUGU -> 108f
            StudioLanguage.HINDI -> 118f
        }
        val effectiveWpm = baseWordsPerMinute * speed
        val wordBasedSeconds = (words / effectiveWpm) * 60f
        // Character floor prevents undercounting unspaced scripts while staying realistic
        val charBasedSeconds = cleaned.length / (14.5f * speed)
        return maxOf(wordBasedSeconds, charBasedSeconds * 0.8f).roundToInt().coerceAtLeast(3)
    }

    fun estimateTotalScriptDurationSeconds(
        openingHook: String,
        scenes: List<VideoScene>,
        conclusion: String,
        speakingSpeed: Float,
        language: StudioLanguage
    ): Int {
        val hookSec = estimateSpeechDurationSeconds(openingHook, speakingSpeed, language)
        val scenesSec = scenes.sumOf { estimateSpeechDurationSeconds(it.narration, speakingSpeed, language) }
        val conclusionSec = estimateSpeechDurationSeconds(conclusion, speakingSpeed, language)
        return hookSec + scenesSec + conclusionSec
    }

    companion object {
        const val PRIMARY_FREE_MODEL = "gemini-3.8-flash"
        const val FALLBACK_FREE_MODEL = "gemini-3.5-flash-lite"

        val APPROVED_FREE_CLOUD_MODELS = setOf(
            PRIMARY_FREE_MODEL,
            FALLBACK_FREE_MODEL
        )

        val APPROVED_LOCAL_ENGINES = setOf(
            "android-google-tts",
            "media3-transformer-local"
        )

        val BLOCKED_PAID_MODELS = setOf(
            "veo-video-api",
            "veo-2",
            "veo-3",
            "gemini-nano-banana-2.1",
            "gemini-3-pro-image",
            "gemini-3.1-flash-lite-image",
            "gemini-4-argon",
            "imagen-3",
            "imagen-4"
        )
    }
}
