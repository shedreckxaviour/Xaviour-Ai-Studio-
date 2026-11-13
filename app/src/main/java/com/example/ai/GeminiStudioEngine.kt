package com.example.ai

import android.util.Log
import com.example.data.model.StudioLanguage
import com.example.data.model.VideoProject
import com.example.data.model.VideoScene
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiStudioEngine"

data class GeneratedScriptBundle(
    val topic: String,
    val targetAudience: String,
    val scriptOutline: String,
    val openingHook: String,
    val conclusion: String,
    val youtubeTitle: String,
    val youtubeDescription: String,
    val hashtags: List<String>,
    val thumbnailText: String,
    val factCheckNotes: String,
    val sourcesOrUncertaintyNotes: String,
    val estimatedNarrationSeconds: Int,
    val modelUsed: String,
    val estimatedTokens: Long,
    val scenes: List<VideoScene>
)

class GeminiStudioEngine(
    private val freeModeManager: FreeModeManager
) {

    private val scriptSchema = Schema.obj(
        properties = mapOf(
            "topic" to Schema.string(description = "Clear analysis of the video topic"),
            "targetAudience" to Schema.string(description = "Primary audience for this explainer"),
            "scriptOutline" to Schema.string(description = "Numbered structured documentary outline covering intro, chapters, and outro"),
            "openingHook" to Schema.string(description = "Compelling documentary opening hook"),
            "conclusion" to Schema.string(description = "Memorable closing narration"),
            "youtubeTitle" to Schema.string(description = "Accurate YouTube title under 90 characters"),
            "youtubeDescription" to Schema.string(description = "Detailed YouTube description summarizing verified takeaways"),
            "hashtags" to Schema.array(
                items = Schema.string(),
                description = "5 to 10 relevant hashtags starting with #"
            ),
            "thumbnailText" to Schema.string(description = "Short 2 to 5 word bold thumbnail headline"),
            "factCheckNotes" to Schema.string(description = "Notes distinguishing verified facts from hypothesis or speculation"),
            "sourcesOrUncertaintyNotes" to Schema.string(description = "General domain references or explicit uncertainty notes without inventing citations"),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Short chapter/scene title"),
                        "narration" to Schema.string(description = "Detailed spoken voice-over narration matching scene duration in the requested language"),
                        "visualPrompt" to Schema.string(description = "Visual direction and slide composition notes maintaining continuity"),
                        "estimatedSeconds" to Schema.integer(description = "Target scene duration in seconds"),
                        "factType" to Schema.enumeration(
                            values = listOf("verified_fact", "scientific_context", "speculation_labeled"),
                            description = "Whether this scene states verified facts, scientific context, or labeled speculation"
                        ),
                        "accentHex" to Schema.string(description = "Hex accent color like #2979FF, #40C4FF, or #F5C451")
                    )
                ),
                description = "Sequential timed scenes covering the full selected duration"
            )
        )
    )

    private val singleSceneSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short chapter/scene title"),
            "narration" to Schema.string(description = "Spoken voice-over narration in the requested language"),
            "visualPrompt" to Schema.string(description = "Visual direction notes maintaining continuity"),
            "estimatedSeconds" to Schema.integer(description = "Target scene duration in seconds"),
            "factType" to Schema.enumeration(
                values = listOf("verified_fact", "scientific_context", "speculation_labeled"),
                description = "Fact classification"
            ),
            "accentHex" to Schema.string(description = "Hex accent color")
        )
    )

    private val sectionTextSchema = Schema.obj(
        properties = mapOf(
            "text" to Schema.string(description = "Regenerated documentary section text")
        )
    )

    private val flashScriptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FreeModeManager.PRIMARY_FREE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scriptSchema
                temperature = 0.6f
            }
        )
    }

    private val flashLiteScriptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FreeModeManager.FALLBACK_FREE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scriptSchema
                temperature = 0.6f
            }
        )
    }

    private val sceneRegenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FreeModeManager.PRIMARY_FREE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleSceneSchema
                temperature = 0.7f
            }
        )
    }

    private val sceneRegenFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FreeModeManager.FALLBACK_FREE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleSceneSchema
                temperature = 0.7f
            }
        )
    }

    private val sectionRegenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FreeModeManager.PRIMARY_FREE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = sectionTextSchema
                temperature = 0.7f
            }
        )
    }

    suspend fun generateDocumentaryScript(
        userId: String,
        projectId: String,
        prompt: String,
        durationMinutes: Int,
        aspectRatio: String,
        videoStyle: String,
        language: StudioLanguage,
        narrationStyle: String,
        speakingSpeed: Float,
        preferredModelId: String = FreeModeManager.PRIMARY_FREE_MODEL
    ): Result<GeneratedScriptBundle> {
        val approvedModel = freeModeManager.validateAndPrepareCloudRequest(
            requestedModelId = preferredModelId,
            operation = "script_generation"
        ).getOrElse { return Result.failure(it) }

        val targetScenes = when (durationMinutes) {
            3 -> 7
            5 -> 10
            10 -> 14
            15 -> 18
            else -> 7
        }
        val totalSeconds = durationMinutes * 60
        val avgSceneSec = (totalSeconds / targetScenes).coerceIn(18, 65)
        val targetTotalWords = ((durationMinutes * 135f) * speakingSpeed.coerceIn(0.75f, 1.35f)).toInt()
        val wordsPerScene = (targetTotalWords / targetScenes).coerceAtLeast(45)

        val instruction = buildString {
            appendLine("You are the lead documentary writer and visual director for XAVIOUR AI STUDIO.")
            appendLine("Analyze the user's single topic and produce a complete, structured, scientifically/historically honest explainer script.")
            appendLine()
            appendLine("USER TOPIC / PROMPT: $prompt")
            appendLine("TARGET DURATION: $durationMinutes minutes ($totalSeconds seconds total)")
            appendLine("WORD COUNT CALIBRATION: Write approximately $targetTotalWords total spoken words (~$wordsPerScene spoken words per scene across $targetScenes scenes) so the narration genuinely fills ~$durationMinutes minutes at ${String.format("%.2fx", speakingSpeed)} pace.")
            appendLine("ASPECT RATIO: $aspectRatio")
            appendLine("VISUAL STYLE: $videoStyle")
            appendLine("LANGUAGE FOR SCRIPT, HOOK, OUTLINE & TITLES: ${language.label} (keep visualPrompt in clear English for visual slide directions).")
            appendLine("NARRATION TONE: $narrationStyle")
            appendLine()
            appendLine("STRICT ACCURACY & HONESTY RULES:")
            appendLine("1. Never invent scientific facts, statistics, fake papers, URLs, or fabricated citations.")
            appendLine("2. Clearly distinguish verified scientific/historical facts ('verified_fact' / 'scientific_context') from hypotheses or open questions ('speculation_labeled').")
            appendLine("3. In sourcesOrUncertaintyNotes, mention general established fields/agencies (e.g. NASA, IPCC, peer-reviewed consensus) or state uncertainties transparently — never fabricate specific DOIs or non-existent references.")
            appendLine("4. Build a clear numbered scriptOutline, then write full narration across all $targetScenes sequential scenes.")
        }

        return executeScriptModelWithFreeFallback(approvedModel, instruction) { chosenId, rawJson ->
            val json = JSONObject(rawJson)
            val hashtagsArr = json.optJSONArray("hashtags")
            val hashtags = buildList {
                if (hashtagsArr != null) {
                    for (i in 0 until hashtagsArr.length().coerceAtMost(12)) {
                        val tag = hashtagsArr.optString(i).trim()
                        if (tag.isNotEmpty()) add(if (tag.startsWith("#")) tag else "#$tag")
                    }
                }
            }

            val scenesArr = json.optJSONArray("scenes")
                ?: throw IllegalStateException("Script response omitted scenes array.")

            val openingHook = json.optString("openingHook", "").take(1500)
            val conclusion = json.optString("conclusion", "").take(1500)

            val scenes = buildList {
                for (i in 0 until scenesArr.length()) {
                    val item = scenesArr.getJSONObject(i)
                    val narration = item.optString("narration", "").take(4000)
                    val factType = item.optString("factType", "verified_fact").let {
                        if (it in setOf("verified_fact", "scientific_context", "speculation_labeled")) it else "verified_fact"
                    }
                    // Estimate genuine spoken duration from actual narration text length and speed
                    val realSeconds = freeModeManager.estimateSpeechDurationSeconds(
                        text = narration,
                        speakingSpeed = speakingSpeed,
                        language = language
                    )
                    add(
                        VideoScene(
                            id = "scene_${i + 1}",
                            userId = userId,
                            projectId = projectId,
                            sceneIndex = i,
                            title = item.optString("title", "Scene ${i + 1}").take(160),
                            narration = narration,
                            visualPrompt = item.optString("visualPrompt", "").take(2000),
                            estimatedSeconds = realSeconds.coerceIn(4, 300),
                            factType = factType,
                            visualSourceType = "pending",
                            status = "pending",
                            accentHex = item.optString("accentHex", "#2979FF").take(20)
                        )
                    )
                }
            }

            val actualTotalSeconds = freeModeManager.estimateTotalScriptDurationSeconds(
                openingHook = openingHook,
                scenes = scenes,
                conclusion = conclusion,
                speakingSpeed = speakingSpeed,
                language = language
            )
            val estimatedTokens = ((instruction.length + rawJson.length) / 4L).coerceAtLeast(100L)

            GeneratedScriptBundle(
                topic = json.optString("topic", prompt.take(120)).take(300),
                targetAudience = json.optString("targetAudience", "Curious learners & documentary viewers").take(300),
                scriptOutline = json.optString("scriptOutline", "").take(3000),
                openingHook = openingHook,
                conclusion = conclusion,
                youtubeTitle = json.optString("youtubeTitle", prompt.take(90)).take(140),
                youtubeDescription = json.optString("youtubeDescription", "").take(4000),
                hashtags = hashtags,
                thumbnailText = json.optString("thumbnailText", "THE TRUTH EXPLAINED").take(120),
                factCheckNotes = json.optString("factCheckNotes", "").take(2000),
                sourcesOrUncertaintyNotes = json.optString("sourcesOrUncertaintyNotes", "").take(2000),
                estimatedNarrationSeconds = actualTotalSeconds,
                modelUsed = chosenId,
                estimatedTokens = estimatedTokens,
                scenes = scenes
            )
        }
    }

    suspend fun expandScriptToTargetDuration(
        project: VideoProject,
        currentScenes: List<VideoScene>,
        language: StudioLanguage,
        speakingSpeed: Float
    ): Result<List<VideoScene>> {
        val approvedModel = freeModeManager.validateAndPrepareCloudRequest(
            requestedModelId = FreeModeManager.PRIMARY_FREE_MODEL,
            operation = "script_expansion"
        ).getOrElse { return Result.failure(it) }

        val targetTotalWords = (project.durationMinutes * 135 * speakingSpeed).toInt().coerceAtLeast(350)
        val perSceneWords = (targetTotalWords / currentScenes.size.coerceAtLeast(1)).coerceAtLeast(65)

        val instruction = buildString {
            appendLine("Expand the narration depth of every existing scene so the total spoken script reaches ~$targetTotalWords words (~$perSceneWords words per scene) for a genuine ${project.durationMinutes}-minute ${project.videoStyle} documentary.")
            appendLine("Topic: ${project.prompt}")
            appendLine("Language: ${language.label}")
            appendLine("Narration Tone: ${project.narrationStyle}")
            appendLine("Rules: Keep factual accuracy strict; label speculation clearly; keep the exact same ${currentScenes.size} scene structure.")
            currentScenes.forEachIndexed { idx, sc ->
                appendLine("Scene ${idx + 1}: ${sc.title} | Current: ${sc.narration.take(280)}")
            }
        }

        return executeScriptModelWithFreeFallback(approvedModel, instruction) { _, rawJson ->
            val json = JSONObject(rawJson)
            val arr = json.optJSONArray("scenes")
                ?: throw IllegalStateException("Expansion response omitted scenes.")
            currentScenes.mapIndexed { idx, orig ->
                val obj = if (idx < arr.length()) arr.getJSONObject(idx) else null
                val newNarration = obj?.optString("narration", orig.narration)?.take(4000)?.ifBlank { orig.narration } ?: orig.narration
                val realSec = freeModeManager.estimateSpeechDurationSeconds(newNarration, speakingSpeed, language)
                orig.copy(
                    title = obj?.optString("title", orig.title)?.take(160)?.ifBlank { orig.title } ?: orig.title,
                    narration = newNarration,
                    visualPrompt = obj?.optString("visualPrompt", orig.visualPrompt)?.take(2000)?.ifBlank { orig.visualPrompt } ?: orig.visualPrompt,
                    estimatedSeconds = realSec.coerceIn(4, 300),
                    status = "pending"
                )
            }
        }
    }

    suspend fun regenerateSingleScene(
        projectPrompt: String,
        videoStyle: String,
        language: StudioLanguage,
        narrationStyle: String,
        speakingSpeed: Float,
        scene: VideoScene
    ): Result<VideoScene> {
        val approvedModel = freeModeManager.validateAndPrepareCloudRequest(
            requestedModelId = FreeModeManager.PRIMARY_FREE_MODEL,
            operation = "scene_regeneration"
        ).getOrElse { return Result.failure(it) }

        val prompt = """
            Regenerate Scene ${scene.sceneIndex + 1} for an explainer video.
            Video Topic: $projectPrompt
            Visual Style: $videoStyle
            Language: ${language.label}
            Narration Tone: $narrationStyle
            Previous Title: ${scene.title}
            Previous Narration: ${scene.narration}
            Provide a fresh, detailed narration and visual direction while keeping scientific/historical accuracy strict (never invent citations).
        """.trimIndent()

        return try {
            val model = if (approvedModel == FreeModeManager.FALLBACK_FREE_MODEL) sceneRegenFallbackModel else sceneRegenModel
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Model returned empty scene response."))
            val json = JSONObject(rawJson)
            val factType = json.optString("factType", scene.factType).let {
                if (it in setOf("verified_fact", "scientific_context", "speculation_labeled")) it else scene.factType
            }
            val updatedNarration = json.optString("narration", scene.narration).take(4000)
            val realSec = freeModeManager.estimateSpeechDurationSeconds(updatedNarration, speakingSpeed, language)
            freeModeManager.recordCloudSuccess(approvedModel, "scene_regeneration")
            Result.success(
                scene.copy(
                    title = json.optString("title", scene.title).take(160),
                    narration = updatedNarration,
                    visualPrompt = json.optString("visualPrompt", scene.visualPrompt).take(2000),
                    estimatedSeconds = realSec.coerceIn(4, 300),
                    factType = factType,
                    accentHex = json.optString("accentHex", scene.accentHex).take(20),
                    visualSourceType = if (scene.userImagePath.isNotBlank()) "user_uploaded_image" else "pending",
                    status = "pending"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scene regeneration failed", e)
            val msg = freeModeManager.recordCloudFailure(approvedModel, e)
            Result.failure(Exception(msg, e))
        }
    }

    suspend fun regenerateScriptSection(
        sectionName: String,
        project: VideoProject,
        currentText: String,
        language: StudioLanguage
    ): Result<String> {
        val approvedModel = freeModeManager.validateAndPrepareCloudRequest(
            requestedModelId = FreeModeManager.PRIMARY_FREE_MODEL,
            operation = "section_regeneration"
        ).getOrElse { return Result.failure(it) }

        val prompt = """
            Rewrite the $sectionName for this documentary explainer in ${language.label}.
            Topic: ${project.prompt}
            Style: ${project.videoStyle}
            Tone: ${project.narrationStyle}
            Current text: $currentText
            Keep facts honest and engaging. Do not invent citations.
        """.trimIndent()

        return try {
            val response = withTimeout(40_000L) {
                sectionRegenModel.generateContent(prompt)
            }
            val raw = response.text ?: return Result.failure(IllegalStateException("Empty section response."))
            val newText = JSONObject(raw).optString("text", "").trim()
            if (newText.isBlank()) {
                Result.failure(IllegalStateException("Regenerated text was empty."))
            } else {
                freeModeManager.recordCloudSuccess(approvedModel, "section_regeneration")
                Result.success(newText)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = freeModeManager.recordCloudFailure(approvedModel, e)
            Result.failure(Exception(msg, e))
        }
    }

    private suspend fun <T> executeScriptModelWithFreeFallback(
        initialModelId: String,
        instruction: String,
        parser: (String, String) -> T
    ): Result<T> {
        val modelOrder = if (initialModelId == FreeModeManager.FALLBACK_FREE_MODEL) {
            listOf(FreeModeManager.FALLBACK_FREE_MODEL)
        } else {
            listOf(FreeModeManager.PRIMARY_FREE_MODEL, FreeModeManager.FALLBACK_FREE_MODEL)
        }

        var lastError: Throwable? = null
        for ((attemptIdx, modelId) in modelOrder.withIndex()) {
            val validated = freeModeManager.validateAndPrepareCloudRequest(modelId, "script_generation")
            val chosenId = validated.getOrElse { return Result.failure(it) }
            val model = if (chosenId == FreeModeManager.FALLBACK_FREE_MODEL) flashLiteScriptModel else flashScriptModel
            try {
                val response = withTimeout(60_000L) {
                    model.generateContent(instruction)
                }
                val rawJson = response.text
                    ?: throw IllegalStateException("Gemini returned an empty response.")
                val parsed = parser(chosenId, rawJson)
                freeModeManager.recordCloudSuccess(chosenId, "script_generation")
                return Result.success(parsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TimeoutCancellationException) {
                lastError = e
                val msg = freeModeManager.recordCloudFailure(chosenId, e)
                if (attemptIdx == modelOrder.lastIndex) {
                    return Result.failure(Exception(msg, e))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Free model $chosenId failed", e)
                lastError = e
                val msg = freeModeManager.recordCloudFailure(chosenId, e)
                // Only fall back to the other free-tier model; NEVER to a paid model
                val canRetryFallback = attemptIdx < modelOrder.lastIndex &&
                    (e.message?.contains("403") != true)
                if (!canRetryFallback) {
                    return Result.failure(Exception(msg, e))
                }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Free-tier script generation failed."))
    }
}
