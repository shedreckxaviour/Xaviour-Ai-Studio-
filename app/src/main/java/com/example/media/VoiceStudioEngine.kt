package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.NarrationStyleOption
import com.example.data.model.StudioLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "VoiceStudioEngine"

data class SynthesizedAudioResult(
    val wavFile: File,
    val durationMs: Long,
    val engineLabel: String,
    val fallbackWarning: String? = null
)

data class LanguageVoiceSupport(
    val language: StudioLanguage,
    val isAvailable: Boolean,
    val voiceCount: Int,
    val hasMaleHint: Boolean,
    val hasFemaleHint: Boolean,
    val statusDetail: String
)

data class TtsCapabilityReport(
    val engineAvailable: Boolean,
    val defaultEnginePackage: String,
    val languages: List<LanguageVoiceSupport>
)

class VoiceStudioEngine(private val context: Context) {

    private var activePlayer: MediaPlayer? = null

    suspend fun inspectDeviceTtsCapabilities(): TtsCapabilityReport = withContext(Dispatchers.IO) {
        val tts = initTts()
        if (tts == null) {
            return@withContext TtsCapabilityReport(
                engineAvailable = false,
                defaultEnginePackage = "Unavailable",
                languages = StudioLanguage.entries.map { lang ->
                    LanguageVoiceSupport(
                        language = lang,
                        isAvailable = false,
                        voiceCount = 0,
                        hasMaleHint = false,
                        hasFemaleHint = false,
                        statusDetail = "No TTS engine installed on device"
                    )
                }
            )
        }

        try {
            val defaultEngine = runCatching { tts.defaultEngine.orEmpty() }.getOrDefault("")
            val allVoices = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            val langReports = StudioLanguage.entries.map { lang ->
                val locale = Locale.forLanguageTag(lang.localeTag)
                val status = runCatching { tts.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                val available = status >= TextToSpeech.LANG_AVAILABLE
                val matchingVoices = allVoices.filter { it.locale.language.equals(locale.language, ignoreCase = true) }
                val maleHint = matchingVoices.any { v ->
                    val n = v.name.lowercase(Locale.ROOT)
                    n.contains("male") || n.contains("-m-") || n.contains("-d-") || n.endsWith("m")
                }
                val femaleHint = matchingVoices.any { v ->
                    val n = v.name.lowercase(Locale.ROOT)
                    n.contains("female") || n.contains("-f-") || n.contains("-c-") || n.endsWith("f")
                }
                val detail = when {
                    available && matchingVoices.isNotEmpty() -> "${matchingVoices.size} voice(s) installed"
                    available -> "Installed (default system voice)"
                    status == TextToSpeech.LANG_MISSING_DATA -> "Voice data missing — download in Android TTS settings"
                    else -> "Not installed on this device (falls back to English)"
                }
                LanguageVoiceSupport(
                    language = lang,
                    isAvailable = available,
                    voiceCount = matchingVoices.size,
                    hasMaleHint = maleHint,
                    hasFemaleHint = femaleHint,
                    statusDetail = detail
                )
            }
            TtsCapabilityReport(
                engineAvailable = true,
                defaultEnginePackage = defaultEngine.ifBlank { "System TTS" },
                languages = langReports
            )
        } finally {
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
        }
    }

    suspend fun synthesizeNarrationToWav(
        text: String,
        language: StudioLanguage,
        narrationStyle: NarrationStyleOption,
        voiceGender: String,
        speakingSpeed: Float,
        outputFile: File
    ): Result<SynthesizedAudioResult> = withContext(Dispatchers.IO) {
        val cleanedText = text.trim()
        if (cleanedText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Narration text cannot be empty."))
        }

        val tts = initTts() ?: return@withContext Result.failure(
            IllegalStateException("Android TextToSpeech engine is unavailable on this device.")
        )

        try {
            val locale = Locale.forLanguageTag(language.localeTag)
            val langStatus = tts.setLanguage(locale)
            var fallbackWarning: String? = null
            if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale ${language.localeTag} unavailable ($langStatus); falling back to US English")
                fallbackWarning = "${language.label} TTS voice pack is not installed on this device; fell back to English."
                tts.setLanguage(Locale.US)
            }

            val targetLocaleLang = if (fallbackWarning == null) locale.language else Locale.US.language
            val matchingVoice = tts.voices?.firstOrNull { voice ->
                val matchesLang = voice.locale.language.equals(targetLocaleLang, ignoreCase = true)
                val nameLower = voice.name.lowercase(Locale.ROOT)
                val matchesGender = if (voiceGender == "male") {
                    nameLower.contains("male") || nameLower.contains("-m-") || nameLower.contains("#male") || nameLower.endsWith("m")
                } else {
                    nameLower.contains("female") || nameLower.contains("-f-") || nameLower.contains("#female") || nameLower.endsWith("f")
                }
                matchesLang && matchesGender && !voice.isNetworkConnectionRequired
            } ?: tts.voices?.firstOrNull { it.locale.language.equals(targetLocaleLang, ignoreCase = true) && !it.isNetworkConnectionRequired }
                ?: tts.voices?.firstOrNull { it.locale.language.equals(targetLocaleLang, ignoreCase = true) }

            if (matchingVoice != null) {
                tts.voice = matchingVoice
            }

            tts.setPitch(narrationStyle.defaultPitch)
            tts.setSpeechRate((speakingSpeed * narrationStyle.defaultSpeed).coerceIn(0.6f, 1.5f))

            val chunks = chunkNarration(cleanedText, maxChunkChars = 1200)
            val chunkFiles = mutableListOf<File>()
            outputFile.parentFile?.mkdirs()

            for ((idx, chunk) in chunks.withIndex()) {
                val partFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}_part_$idx.wav")
                val ok = synthesizeChunk(tts, chunk, partFile)
                if (ok && partFile.exists() && partFile.length() > 44) {
                    chunkFiles.add(partFile)
                }
            }

            if (chunkFiles.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Device TTS returned no audio samples for ${language.label}.")
                )
            }

            mergeWavFiles(chunkFiles, outputFile)
            chunkFiles.forEach { it.delete() }

            val durationMs = estimateWavDurationMs(outputFile)
            Result.success(
                SynthesizedAudioResult(
                    wavFile = outputFile,
                    durationMs = durationMs,
                    engineLabel = "On-Device Android TTS (${language.label})",
                    fallbackWarning = fallbackWarning
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Voice synthesis failed", e)
            Result.failure(e)
        } finally {
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
        }
    }

    suspend fun mergeSceneWavFiles(
        inputFiles: List<File>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val validFiles = inputFiles.filter { it.exists() && it.length() > 44 }
        if (validFiles.isEmpty()) {
            writeSilentWav(outputFile, durationMs = 2000L)
            return@withContext outputFile
        }
        mergeWavFiles(validFiles, outputFile)
        outputFile
    }

    fun playPreview(file: File, onComplete: () -> Unit, onError: (String) -> Unit) {
        stopPreview()
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPreview()
                    onComplete()
                }
                start()
            }
            activePlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play preview", e)
            onError(e.localizedMessage ?: "Audio preview error")
        }
    }

    fun stopPreview() {
        try {
            activePlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            activePlayer?.release()
        } catch (_: Exception) {
        }
        activePlayer = null
    }

    private suspend fun initTts(): TextToSpeech? = suspendCancellableCoroutine { cont ->
        var ttsRef: TextToSpeech? = null
        ttsRef = TextToSpeech(context.applicationContext) { status ->
            if (cont.isActive) {
                if (status == TextToSpeech.SUCCESS) {
                    cont.resume(ttsRef)
                } else {
                    ttsRef?.shutdown()
                    cont.resume(null)
                }
            }
        }
        cont.invokeOnCancellation {
            ttsRef?.shutdown()
        }
    }

    private suspend fun synthesizeChunk(
        tts: TextToSpeech,
        text: String,
        destFile: File
    ): Boolean = suspendCancellableCoroutine { cont ->
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(false)
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && cont.isActive) cont.resume(false)
            }
        })
        val params = Bundle()
        val result = tts.synthesizeToFile(text, params, destFile, utteranceId)
        if (result != TextToSpeech.SUCCESS && cont.isActive) {
            cont.resume(false)
        }
    }

    private fun chunkNarration(text: String, maxChunkChars: Int): List<String> {
        if (text.length <= maxChunkChars) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?।])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (current.length + sentence.length + 1 > maxChunkChars && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
            }
            current.append(sentence).append(' ')
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }

    private fun mergeWavFiles(inputs: List<File>, output: File) {
        if (inputs.size == 1) {
            inputs.first().copyTo(output, overwrite = true)
            return
        }
        val firstBytes = inputs.first().readBytes()
        val firstInfo = parseWavHeader(firstBytes)
        val pcmBuffer = ByteArrayOutputStream()

        for (file in inputs) {
            val bytes = file.readBytes()
            val info = parseWavHeader(bytes)
            if (bytes.size > info.dataOffset) {
                val rawPcm = bytes.copyOfRange(info.dataOffset, bytes.size)
                val normalized = if (info.sampleRate == firstInfo.sampleRate && info.channels == firstInfo.channels) {
                    rawPcm
                } else {
                    resamplePcm16(rawPcm, info.sampleRate, info.channels, firstInfo.sampleRate, firstInfo.channels)
                }
                pcmBuffer.write(normalized)
            }
        }

        val pcmData = pcmBuffer.toByteArray()
        writeWavFile(output, pcmData, firstInfo.sampleRate, firstInfo.channels, firstInfo.bitsPerSample)
    }

    data class WavInfo(val sampleRate: Int, val channels: Int, val bitsPerSample: Int, val dataOffset: Int)

    fun parseWavHeader(bytes: ByteArray): WavInfo {
        if (bytes.size < 44) return WavInfo(22050, 1, 16, 44)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt().coerceIn(1, 2)
        val sampleRate = bb.getInt(24).coerceIn(8000, 96000)
        val bitsPerSample = bb.getShort(34).toInt().coerceIn(8, 32)
        var offset = 12
        while (offset + 8 < bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = bb.getInt(offset + 4)
            if (chunkId == "data") {
                return WavInfo(sampleRate, channels, bitsPerSample, offset + 8)
            }
            if (chunkSize <= 0) break
            offset += 8 + chunkSize
        }
        return WavInfo(sampleRate, channels, bitsPerSample, 44)
    }

    fun estimateWavDurationMs(wavFile: File): Long {
        if (!wavFile.exists() || wavFile.length() <= 44) return 0L
        val header = wavFile.inputStream().use { stream ->
            val buf = ByteArray(128)
            val read = stream.read(buf)
            if (read > 0) buf.copyOf(read) else ByteArray(0)
        }
        val info = parseWavHeader(header)
        val dataBytes = (wavFile.length() - info.dataOffset).coerceAtLeast(0L)
        val bytesPerSecond = info.sampleRate.toLong() * info.channels * (info.bitsPerSample / 8)
        return if (bytesPerSecond > 0) (dataBytes * 1000L) / bytesPerSecond else 0L
    }

    private fun resamplePcm16(
        srcBytes: ByteArray,
        srcRate: Int,
        srcChannels: Int,
        dstRate: Int,
        dstChannels: Int
    ): ByteArray {
        val srcSamples = srcBytes.size / (2 * srcChannels)
        if (srcSamples <= 0) return ByteArray(0)
        val dstSamples = ((srcSamples.toLong() * dstRate) / srcRate).toInt().coerceAtLeast(1)
        val srcBuf = ByteBuffer.wrap(srcBytes).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.allocate(dstSamples * 2 * dstChannels).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until dstSamples) {
            val srcIndex = ((i.toLong() * srcRate) / dstRate).toInt().coerceIn(0, srcSamples - 1)
            val sample = srcBuf.getShort(srcIndex * 2 * srcChannels)
            repeat(dstChannels) { outBuf.putShort(sample) }
        }
        return outBuf.array()
    }

    fun writeSilentWav(output: File, durationMs: Long, sampleRate: Int = 22050) {
        val sampleCount = ((durationMs * sampleRate) / 1000L).toInt().coerceAtLeast(sampleRate)
        val pcm = ByteArray(sampleCount * 2)
        writeWavFile(output, pcm, sampleRate, 1, 16)
    }

    private fun writeWavFile(output: File, pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()

        FileOutputStream(output).use { out ->
            out.write(header)
            out.write(pcmData)
        }
    }
}
