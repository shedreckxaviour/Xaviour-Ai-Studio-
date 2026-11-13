package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val TAG = "VideoAssemblerEngine"

enum class MotionPreset(val label: String) {
    AUTO("Dynamic Cinema Choreography"),
    PUSH_IN("Slow Push-In"),
    PULL_OUT("Reveal Pull-Out"),
    DIAGONAL_DRIFT("Diagonal Sweep"),
    ORBITAL_PARALLAX("Orbital Parallax")
}

data class TimelineSegment(
    val title: String,
    val subtitleText: String,
    val imageFile: File,
    val audioFile: File?,
    val durationMs: Long,
    val isBrandingCard: Boolean = false,
    val accentHex: String = "#2979FF",
    val videoStyle: String = "Cinematic Documentary",
    val factType: String = "verified_fact",
    val sceneNumber: Int = 1,
    val totalScenes: Int = 1,
    val motionPreset: MotionPreset = MotionPreset.AUTO
)

data class VideoAssemblyResult(
    val outputFile: File,
    val rendererLabel: String,
    val actualWidth: Int,
    val actualHeight: Int
)

class VideoAssemblerEngine(private val context: Context) {

    private val subtitleTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.SANS_SERIF
    }

    /**
     * Checks device hardware H.264 encoder capabilities for 1080p vs 720p support.
     */
    fun is1080pEncodingSupported(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                if (!info.isEncoder) return@any false
                val caps = runCatching { info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }.getOrNull()
                    ?: return@any false
                val videoCaps = caps.videoCapabilities ?: return@any false
                videoCaps.isSizeSupported(1920, 1080) || videoCaps.isSizeSupported(1080, 1920)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun generateSrtFile(segments: List<TimelineSegment>, srtOutputFile: File): File {
        srtOutputFile.parentFile?.mkdirs()
        val sb = StringBuilder()
        var cursorMs = 0L
        var cueIndex = 1

        for (seg in segments) {
            if (!seg.isBrandingCard && seg.subtitleText.isNotBlank()) {
                val words = seg.subtitleText.trim().split(Regex("\\s+"))
                val lines = words.chunked(12).map { it.joinToString(" ") }
                val sliceMs = (seg.durationMs / lines.size.coerceAtLeast(1)).coerceAtLeast(1000L)
                lines.forEachIndexed { idx, line ->
                    val start = cursorMs + idx * sliceMs
                    val end = if (idx == lines.lastIndex) cursorMs + seg.durationMs else start + sliceMs
                    sb.appendLine(cueIndex++)
                    sb.appendLine("${formatSrtTime(start)} --> ${formatSrtTime(end)}")
                    sb.appendLine(line)
                    sb.appendLine()
                }
            }
            cursorMs += seg.durationMs
        }

        FileOutputStream(srtOutputFile).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        return srtOutputFile
    }

    suspend fun assembleMp4Video(
        segments: List<TimelineSegment>,
        requestedWidth: Int,
        requestedHeight: Int,
        includeBackgroundMusic: Boolean,
        customMusicUri: String?,
        outputMp4File: File,
        voiceEngine: VoiceStudioEngine,
        isPaused: AtomicBoolean,
        onProgress: (Float, String) -> Unit
    ): Result<VideoAssemblyResult> = withContext(Dispatchers.Default) {
        if (segments.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No scenes available to render."))
        }

        outputMp4File.parentFile?.mkdirs()

        // Step 0: Respect real device hardware encoder limits
        val wants1080p = maxOf(requestedWidth, requestedHeight) >= 1920
        val supports1080p = if (wants1080p) is1080pEncodingSupported() else true
        val targetW = if (wants1080p && !supports1080p) (requestedWidth * 2) / 3 else requestedWidth
        val targetH = if (wants1080p && !supports1080p) (requestedHeight * 2) / 3 else requestedHeight
        val encWidth = if (targetW % 2 == 0) targetW else targetW - 1
        val encHeight = if (targetH % 2 == 0) targetH else targetH - 1

        val intermediateMp4 = File(outputMp4File.parentFile, "${outputMp4File.nameWithoutExtension}_slides_base.mp4")
        if (intermediateMp4.exists()) intermediateMp4.delete()

        // Step 1: Render Ken Burns animated visual slides, crossfades, captions & narration PCM to MP4
        val baseResult = renderAnimatedSlidesToMp4(
            segments = segments,
            encWidth = encWidth,
            encHeight = encHeight,
            includeAmbientFallback = includeBackgroundMusic && customMusicUri.isNullOrBlank(),
            outputFile = intermediateMp4,
            voiceEngine = voiceEngine,
            isPaused = isPaused,
            onProgress = { p, msg -> onProgress(p * 0.72f, msg) }
        )

        baseResult.getOrElse { return@withContext Result.failure(it) }

        // Step 2: Compose & finalize MP4 with AndroidX Media3 Transformer (with user music track if selected)
        onProgress(0.76f, "Finalizing MP4 via AndroidX Media3 Transformer...")
        val finalTransformed = File(outputMp4File.parentFile, "${outputMp4File.nameWithoutExtension}_media3.mp4")
        if (finalTransformed.exists()) finalTransformed.delete()

        val media3Res = runMedia3TransformerComposition(
            baseVideoFile = intermediateMp4,
            targetWidth = encWidth,
            targetHeight = encHeight,
            customMusicUri = if (includeBackgroundMusic) customMusicUri else null,
            outputFile = finalTransformed,
            onProgress = { pct -> onProgress(0.76f + pct * 0.22f, "Media3 Transformer export ${(pct * 100).toInt()}%...") }
        )

        if (media3Res.isSuccess && finalTransformed.exists() && finalTransformed.length() > 0) {
            finalTransformed.copyTo(outputMp4File, overwrite = true)
            finalTransformed.delete()
            intermediateMp4.delete()
            onProgress(1f, "MP4 export ready (Media3 Transformer)")
            Result.success(
                VideoAssemblyResult(
                    outputFile = outputMp4File,
                    rendererLabel = "Media3 Transformer (24fps Cinema FX + Animated Slides)",
                    actualWidth = encWidth,
                    actualHeight = encHeight
                )
            )
        } else {
            // Safe local fallback if Media3 Transformer hits a device-specific codec edge case
            intermediateMp4.copyTo(outputMp4File, overwrite = true)
            intermediateMp4.delete()
            finalTransformed.delete()
            onProgress(1f, "MP4 export ready (24fps Cinema FX Assembler)")
            Result.success(
                VideoAssemblyResult(
                    outputFile = outputMp4File,
                    rendererLabel = "Local H.264/AAC Cinema FX (24fps Animated Slides)",
                    actualWidth = encWidth,
                    actualHeight = encHeight
                )
            )
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runMedia3TransformerComposition(
        baseVideoFile: File,
        targetWidth: Int,
        targetHeight: Int,
        customMusicUri: String?,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Main) {
        try {
            val baseItem = MediaItem.fromUri(Uri.fromFile(baseVideoFile))
            val presentation = Presentation.createForWidthAndHeight(
                targetWidth,
                targetHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
            val editedBase = EditedMediaItem.Builder(baseItem)
                .setEffects(Effects(emptyList(), listOf(presentation)))
                .build()
            val videoSequence = EditedMediaItemSequence.Builder(editedBase).build()

            val composition = if (!customMusicUri.isNullOrBlank()) {
                val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(customMusicUri))).build()
                val musicSequence = EditedMediaItemSequence.Builder(musicItem)
                    .setIsLooping(true)
                    .build()
                Composition.Builder(videoSequence, musicSequence).build()
            } else {
                Composition.Builder(videoSequence).build()
            }

            suspendCancellableCoroutine<Result<File>> { cont ->
                var progressJob: kotlinx.coroutines.Job? = null
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            progressJob?.cancel()
                            if (cont.isActive) cont.resume(Result.success(outputFile))
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            progressJob?.cancel()
                            Log.w(TAG, "Media3 Transformer fallback triggered: ${exportException.message}")
                            if (cont.isActive) cont.resume(Result.failure(exportException))
                        }
                    })
                    .build()

                transformer.start(composition, outputFile.absolutePath)

                val holder = ProgressHolder()
                progressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    while (cont.isActive) {
                        val state = transformer.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                        }
                        delay(350L)
                    }
                }

                cont.invokeOnCancellation {
                    progressJob.cancel()
                    runCatching { transformer.cancel() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Media3 Transformer unavailable in current environment: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun renderAnimatedSlidesToMp4(
        segments: List<TimelineSegment>,
        encWidth: Int,
        encHeight: Int,
        includeAmbientFallback: Boolean,
        outputFile: File,
        voiceEngine: VoiceStudioEngine,
        isPaused: AtomicBoolean,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        val fps = 24
        val sampleRate = 44100
        val channels = 1

        var videoCodec: MediaCodec? = null
        var audioCodec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var prevBitmap: Bitmap? = null

        try {
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encWidth, encHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, if (encWidth >= 1080) 4_500_000 else 2_600_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }

            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoCodec.createInputSurface()
            videoCodec.start()

            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioCodec.start()

            val activeMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false
            var videoOutFrameCounter = 0L

            data class PendingMuxSample(val isVideo: Boolean, val data: ByteArray, val info: MediaCodec.BufferInfo)
            val pendingSamples = mutableListOf<PendingMuxSample>()

            fun maybeStartMuxerAndFlush() {
                if (!muxerStarted && videoTrackIndex != -1 && audioTrackIndex != -1) {
                    activeMuxer.start()
                    muxerStarted = true
                    for (sample in pendingSamples) {
                        val trackIdx = if (sample.isVideo) videoTrackIndex else audioTrackIndex
                        activeMuxer.writeSampleData(trackIdx, ByteBuffer.wrap(sample.data), sample.info)
                    }
                    pendingSamples.clear()
                }
            }

            fun writeOrQueueSample(isVideo: Boolean, outBuf: ByteBuffer, info: MediaCodec.BufferInfo) {
                val sampleInfo = MediaCodec.BufferInfo().apply {
                    val fixedPts = if (isVideo) {
                        (videoOutFrameCounter++ * 1_000_000L) / fps
                    } else {
                        info.presentationTimeUs
                    }
                    set(0, info.size, fixedPts, info.flags)
                }
                outBuf.position(info.offset)
                outBuf.limit(info.offset + info.size)
                if (muxerStarted) {
                    val trackIdx = if (isVideo) videoTrackIndex else audioTrackIndex
                    activeMuxer.writeSampleData(trackIdx, outBuf, sampleInfo)
                } else {
                    val copy = ByteArray(info.size)
                    outBuf.get(copy)
                    pendingSamples.add(PendingMuxSample(isVideo, copy, sampleInfo))
                }
            }

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            onProgress(0.05f, "Normalizing narration & audio mix")
            val totalDurationMs = segments.sumOf { it.durationMs }.coerceAtLeast(2000L)
            val totalAudioSamples = ((totalDurationMs * sampleRate) / 1000L).toInt()
            val masterPcm = buildMasterAudioPcm(
                segments = segments,
                totalSamples = totalAudioSamples,
                sampleRate = sampleRate,
                includeBackgroundMusic = includeAmbientFallback,
                voiceEngine = voiceEngine
            )

            var totalFrames = 0
            val segmentFrames = segments.map { ((it.durationMs * fps) / 1000L).toInt().coerceAtLeast(fps) }
            val expectedFrames = segmentFrames.sum().coerceAtLeast(1)
            var audioSampleCursor = 0
            val samplesPerVideoFrame = sampleRate / fps
            val transitionFrames = 10

            val paints = FramePaintCache(subtitleTypeface)
            for ((segIndex, segment) in segments.withIndex()) {
                currentCoroutineContext().ensureActive()
                while (isPaused.get()) {
                    delay(150L)
                    currentCoroutineContext().ensureActive()
                }

                val bmp = decodeSampledBitmap(segment.imageFile, encWidth, encHeight)
                    ?: Bitmap.createBitmap(encWidth, encHeight, Bitmap.Config.ARGB_8888)
                val framesForSeg = segmentFrames[segIndex]

                val subtitleChunks = if (!segment.isBrandingCard && segment.subtitleText.isNotBlank()) {
                    segment.subtitleText.trim().split(Regex("\\s+")).chunked(10).map { it.joinToString(" ") }
                } else {
                    emptyList()
                }

                for (f in 0 until framesForSeg) {
                    currentCoroutineContext().ensureActive()
                    while (isPaused.get()) {
                        delay(150L)
                        currentCoroutineContext().ensureActive()
                    }

                    val segProgress = f.toFloat() / framesForSeg.toFloat().coerceAtLeast(1f)
                    val globalTimeSec = totalFrames / fps.toFloat()
                    val chunkProgress = segProgress * subtitleChunks.size.coerceAtLeast(1)
                    val chunkIdx = chunkProgress.toInt().coerceIn(0, (subtitleChunks.size - 1).coerceAtLeast(0))
                    val subtitle = if (subtitleChunks.isNotEmpty()) subtitleChunks[chunkIdx] else ""
                    val subWordFraction = (chunkProgress - chunkIdx).coerceIn(0f, 1f)

                    val dissolveAlpha = if (prevBitmap != null && f < transitionFrames) {
                        1f - (f.toFloat() / transitionFrames.toFloat())
                    } else 0f

                    val endSample = (audioSampleCursor + samplesPerVideoFrame).coerceAtMost(masterPcm.size)
                    val audioLevel = computeWindowRmsLevel(masterPcm, audioSampleCursor, endSample)

                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        drawAnimatedCinemaFrame(
                            canvas = canvas,
                            bitmap = bmp,
                            previousBitmap = if (dissolveAlpha > 0.01f) prevBitmap else null,
                            dissolveAlpha = dissolveAlpha,
                            segment = segment,
                            segIndex = segIndex,
                            width = encWidth,
                            height = encHeight,
                            segProgress = segProgress,
                            globalTimeSec = globalTimeSec,
                            audioLevel = audioLevel,
                            subtitle = subtitle,
                            subtitleProgress = subWordFraction,
                            introFadeBlack = if (segIndex == 0 && f < 6) (6 - f) / 6f * 0.85f else 0f,
                            outroFadeBlack = if (segIndex == segments.lastIndex && f > framesForSeg - 8) {
                                (f - (framesForSeg - 8)) / 8f * 0.85f
                            } else 0f,
                            paints = paints
                        )
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    val vResult = drainCodec(
                        codec = videoCodec,
                        bufferInfo = videoBufferInfo,
                        onFormatChanged = { fmt ->
                            if (videoTrackIndex == -1) {
                                videoTrackIndex = activeMuxer.addTrack(fmt)
                                maybeStartMuxerAndFlush()
                            }
                        },
                        onEncodedSample = { buf, info ->
                            writeOrQueueSample(isVideo = true, outBuf = buf, info = info)
                        }
                    )

                    if (endSample > audioSampleCursor) {
                        audioSampleCursor = feedAudioSamples(
                            audioCodec = audioCodec,
                            pcm = masterPcm,
                            startSample = audioSampleCursor,
                            endSample = endSample,
                            sampleRate = sampleRate
                        )
                    }

                    drainCodec(
                        codec = audioCodec,
                        bufferInfo = audioBufferInfo,
                        onFormatChanged = { fmt ->
                            if (audioTrackIndex == -1) {
                                audioTrackIndex = activeMuxer.addTrack(fmt)
                                maybeStartMuxerAndFlush()
                            }
                        },
                        onEncodedSample = { buf, info ->
                            writeOrQueueSample(isVideo = false, outBuf = buf, info = info)
                        }
                    )

                    totalFrames++
                    if (totalFrames % 12 == 0 || totalFrames == expectedFrames) {
                        val pct = 0.10f + 0.85f * (totalFrames.toFloat() / expectedFrames.toFloat())
                        onProgress(pct.coerceIn(0.1f, 0.96f), "Rendering 24fps Cinema FX • Scene ${segIndex + 1}/${segments.size}")
                    }
                    if (!vResult) break
                }

                prevBitmap?.recycle()
                prevBitmap = bmp
            }
            prevBitmap?.recycle()
            prevBitmap = null

            var audioFlushGuard = 0
            while (audioSampleCursor < masterPcm.size && audioFlushGuard++ < 400) {
                audioSampleCursor = feedAudioSamples(
                    audioCodec = audioCodec,
                    pcm = masterPcm,
                    startSample = audioSampleCursor,
                    endSample = masterPcm.size,
                    sampleRate = sampleRate
                )
                drainCodec(
                    codec = audioCodec,
                    bufferInfo = audioBufferInfo,
                    onFormatChanged = { fmt ->
                        if (audioTrackIndex == -1) {
                            audioTrackIndex = activeMuxer.addTrack(fmt)
                            maybeStartMuxerAndFlush()
                        }
                    },
                    onEncodedSample = { buf, info ->
                        writeOrQueueSample(isVideo = false, outBuf = buf, info = info)
                    }
                )
            }

            videoCodec.signalEndOfInputStream()
            val eosInIndex = audioCodec.dequeueInputBuffer(10_000L)
            if (eosInIndex >= 0) {
                val ptsUs = (masterPcm.size.toLong() * 1_000_000L) / sampleRate
                audioCodec.queueInputBuffer(eosInIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            drainUntilEos(
                codec = videoCodec,
                bufferInfo = videoBufferInfo,
                onFormatChanged = { fmt ->
                    if (videoTrackIndex == -1) {
                        videoTrackIndex = activeMuxer.addTrack(fmt)
                        maybeStartMuxerAndFlush()
                    }
                },
                onEncodedSample = { buf, info ->
                    writeOrQueueSample(isVideo = true, outBuf = buf, info = info)
                }
            )
            drainUntilEos(
                codec = audioCodec,
                bufferInfo = audioBufferInfo,
                onFormatChanged = { fmt ->
                    if (audioTrackIndex == -1) {
                        audioTrackIndex = activeMuxer.addTrack(fmt)
                        maybeStartMuxerAndFlush()
                    }
                },
                onEncodedSample = { buf, info ->
                    writeOrQueueSample(isVideo = false, outBuf = buf, info = info)
                }
            )

            if (muxerStarted) activeMuxer.stop()
            activeMuxer.release()
            muxer = null

            Result.success(outputFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Slide MP4 encoding failed", e)
            Result.failure(e)
        } finally {
            try { prevBitmap?.recycle() } catch (_: Exception) {}
            try { videoCodec?.stop() } catch (_: Exception) {}
            try { videoCodec?.release() } catch (_: Exception) {}
            try { audioCodec?.stop() } catch (_: Exception) {}
            try { audioCodec?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun buildMasterAudioPcm(
        segments: List<TimelineSegment>,
        totalSamples: Int,
        sampleRate: Int,
        includeBackgroundMusic: Boolean,
        voiceEngine: VoiceStudioEngine
    ): ShortArray {
        val mix = ShortArray(totalSamples)
        var sampleCursor = 0

        for (seg in segments) {
            val segSamples = ((seg.durationMs * sampleRate) / 1000L).toInt().coerceAtLeast(1)
            val audioFile = seg.audioFile
            if (audioFile != null && audioFile.exists() && audioFile.length() > 44) {
                val bytes = audioFile.readBytes()
                val info = voiceEngine.parseWavHeader(bytes)
                val rawBytes = bytes.copyOfRange(info.dataOffset, bytes.size)
                val srcBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
                val srcTotal = rawBytes.size / (2 * info.channels)
                if (srcTotal > 0) {
                    val copyLen = minOf(segSamples, ((srcTotal.toLong() * sampleRate) / info.sampleRate).toInt())
                    for (i in 0 until copyLen) {
                        val destIdx = sampleCursor + i
                        if (destIdx >= totalSamples) break
                        val srcIdx = ((i.toLong() * info.sampleRate) / sampleRate).toInt().coerceIn(0, srcTotal - 1)
                        mix[destIdx] = srcBuf.getShort(srcIdx * 2 * info.channels)
                    }
                }
            }
            sampleCursor += segSamples
        }

        var peak = 1
        for (s in mix) {
            val abs = kotlin.math.abs(s.toInt())
            if (abs > peak) peak = abs
        }
        val gain = if (peak > 1500) (24000f / peak).coerceIn(0.8f, 1.8f) else 1f

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val voice = mix[i] * gain
            val pad = if (includeBackgroundMusic) {
                val env = (0.5 + 0.5 * sin(2.0 * PI * 0.08 * t))
                ((sin(2.0 * PI * 55.0 * t) * 0.45 +
                    sin(2.0 * PI * 110.0 * t) * 0.35 +
                    sin(2.0 * PI * 164.81 * t) * 0.20) * 1400.0 * env).toFloat()
            } else 0f
            mix[i] = (voice + pad).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return mix
    }

    internal fun computeWindowRmsLevel(pcm: ShortArray, start: Int, end: Int): Float {
        if (start >= end || pcm.isEmpty()) return 0f
        var sumSq = 0.0
        var count = 0
        var i = start.coerceAtLeast(0)
        val limit = end.coerceAtMost(pcm.size)
        while (i < limit) {
            val v = pcm[i] / 32768.0
            sumSq += v * v
            count++
            i += 4
        }
        if (count == 0) return 0f
        val rms = sqrt(sumSq / count).toFloat()
        return (rms * 3.4f).coerceIn(0f, 1f)
    }

    /**
     * Public helper for rendering & testing an animated Cinema FX frame at any timestamp.
     */
    fun renderCinemaPreviewFrame(
        bitmap: Bitmap,
        previousBitmap: Bitmap? = null,
        dissolveAlpha: Float = 0f,
        segment: TimelineSegment,
        segIndex: Int,
        width: Int,
        height: Int,
        segProgress: Float,
        globalTimeSec: Float,
        audioLevel: Float,
        subtitle: String,
        subtitleProgress: Float = 0.5f
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawAnimatedCinemaFrame(
            canvas = canvas,
            bitmap = bitmap,
            previousBitmap = previousBitmap,
            dissolveAlpha = dissolveAlpha,
            segment = segment,
            segIndex = segIndex,
            width = width,
            height = height,
            segProgress = segProgress,
            globalTimeSec = globalTimeSec,
            audioLevel = audioLevel,
            subtitle = subtitle,
            subtitleProgress = subtitleProgress,
            introFadeBlack = 0f,
            outroFadeBlack = 0f,
            paints = FramePaintCache(subtitleTypeface)
        )
        return out
    }

    private fun drawAnimatedCinemaFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        previousBitmap: Bitmap?,
        dissolveAlpha: Float,
        segment: TimelineSegment,
        segIndex: Int,
        width: Int,
        height: Int,
        segProgress: Float,
        globalTimeSec: Float,
        audioLevel: Float,
        subtitle: String,
        subtitleProgress: Float,
        introFadeBlack: Float,
        outroFadeBlack: Float,
        paints: FramePaintCache
    ) {
        canvas.drawColor(Color.BLACK)
        val eased = easeInOutCubic(segProgress.coerceIn(0f, 1f))
        val preset = resolveMotionPreset(segment.motionPreset, segIndex, segment.isBrandingCard)
        val dstRect = computeKenBurnsRect(bitmap.width, bitmap.height, width, height, eased, preset, segIndex)
        canvas.drawBitmap(bitmap, null, dstRect, paints.bitmapPaint)

        // True cross-dissolve transition from previous scene + gentle light flash
        if (previousBitmap != null && dissolveAlpha > 0.01f) {
            val prevRect = computeKenBurnsRect(
                previousBitmap.width, previousBitmap.height, width, height, 1f,
                resolveMotionPreset(MotionPreset.AUTO, (segIndex - 1).coerceAtLeast(0), false),
                (segIndex - 1).coerceAtLeast(0)
            )
            paints.dissolvePaint.alpha = (dissolveAlpha.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawBitmap(previousBitmap, null, prevRect, paints.dissolvePaint)

            val flashStrength = sin(dissolveAlpha * PI).toFloat()
            if (flashStrength > 0.05f) {
                canvas.drawColor(Color.argb((flashStrength * 52).toInt().coerceIn(0, 80), 64, 196, 255))
            }
        }

        // Real-time procedural Cinema VFX layers
        drawCinematicVfxOverlay(
            canvas = canvas,
            segment = segment,
            segIndex = segIndex,
            width = width,
            height = height,
            segProgress = segProgress,
            globalTimeSec = globalTimeSec,
            audioLevel = audioLevel,
            paints = paints
        )

        // Highlighted documentary subtitles
        if (subtitle.isNotBlank()) {
            drawSynchronizedSubtitles(canvas, width, height, subtitle, subtitleProgress, paints)
        }

        val edgeFade = maxOf(introFadeBlack, outroFadeBlack)
        if (edgeFade > 0.01f) {
            canvas.drawColor(Color.argb((edgeFade * 255).toInt().coerceIn(0, 255), 3, 5, 14))
        }
    }

    private fun resolveMotionPreset(preset: MotionPreset, segIndex: Int, isBranding: Boolean): MotionPreset {
        if (isBranding) return MotionPreset.PUSH_IN
        if (preset != MotionPreset.AUTO) return preset
        return when (segIndex % 4) {
            0 -> MotionPreset.PUSH_IN
            1 -> MotionPreset.DIAGONAL_DRIFT
            2 -> MotionPreset.PULL_OUT
            else -> MotionPreset.ORBITAL_PARALLAX
        }
    }

    internal fun computeKenBurnsRect(
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        eased: Float,
        preset: MotionPreset,
        segIndex: Int
    ): RectF {
        val coverScale = maxOf(
            dstW.toFloat() / srcW.toFloat().coerceAtLeast(1f),
            dstH.toFloat() / srcH.toFloat().coerceAtLeast(1f)
        )
        val (zoom, panXPct, panYPct) = when (preset) {
            MotionPreset.PUSH_IN -> Triple(1.02f + 0.12f * eased, 0.5f + 0.18f * (eased - 0.5f), 0.5f)
            MotionPreset.PULL_OUT -> Triple(1.15f - 0.12f * eased, 0.5f - 0.16f * (eased - 0.5f), 0.48f + 0.06f * eased)
            MotionPreset.DIAGONAL_DRIFT -> {
                val dir = if (segIndex % 2 == 0) 1f else -1f
                Triple(1.10f + 0.04f * sin(eased * PI).toFloat(), 0.5f + dir * 0.35f * (eased - 0.5f), 0.5f - dir * 0.25f * (eased - 0.5f))
            }
            MotionPreset.ORBITAL_PARALLAX, MotionPreset.AUTO -> {
                Triple(1.06f + 0.06f * sin(eased * PI).toFloat(), 0.5f + 0.22f * cos(eased * PI).toFloat(), 0.5f + 0.14f * sin(eased * PI).toFloat())
            }
        }
        val drawW = srcW * coverScale * zoom
        val drawH = srcH * coverScale * zoom
        val slackX = (drawW - dstW).coerceAtLeast(0f)
        val slackY = (drawH - dstH).coerceAtLeast(0f)
        val left = -slackX * panXPct.coerceIn(0f, 1f)
        val top = -slackY * panYPct.coerceIn(0f, 1f)
        return RectF(left, top, left + drawW, top + drawH)
    }

    private fun drawCinematicVfxOverlay(
        canvas: Canvas,
        segment: TimelineSegment,
        segIndex: Int,
        width: Int,
        height: Int,
        segProgress: Float,
        globalTimeSec: Float,
        audioLevel: Float,
        paints: FramePaintCache
    ) {
        val scale = width / 1280f
        val accentColor = parseColorSafe(segment.accentHex, Color.parseColor("#2979FF"))
        val cyanColor = Color.parseColor("#40C4FF")
        val goldColor = Color.parseColor("#F5C451")
        val styleLower = segment.videoStyle.lowercase()

        // 1. Volumetric diagonal light ray sheen gliding smoothly across the frame
        val sweepOffset = ((globalTimeSec * 0.14f) % 1.8f) - 0.4f
        val sweepX = width * sweepOffset
        paints.rayPaint.shader = LinearGradient(
            sweepX - width * 0.18f, 0f, sweepX + width * 0.24f, height.toFloat(),
            intArrayOf(
                Color.TRANSPARENT,
                withAlpha(if (segIndex % 2 == 0) cyanColor else goldColor, if (segment.isBrandingCard) 42 else 28),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paints.rayPaint)

        // 2. Parallax moving bokeh / starlight particles
        val rng = Random(7717 + segIndex * 131)
        val particlePaint = paints.particlePaint
        val particleCount = if (segment.isBrandingCard) 48 else 38
        for (i in 0 until particleCount) {
            val baseX = rng.nextFloat() * width
            val baseY = rng.nextFloat() * height
            val depth = 0.4f + rng.nextFloat() * 1.4f
            val speedX = (18f + rng.nextFloat() * 28f) * depth * scale
            val speedY = (-8f - rng.nextFloat() * 18f) * depth * scale
            val px = ((baseX + globalTimeSec * speedX) % (width + 40f) + (width + 40f)) % (width + 40f) - 20f
            val py = ((baseY + globalTimeSec * speedY) % (height + 40f) + (height + 40f)) % (height + 40f) - 20f
            val radius = (1.6f + rng.nextFloat() * 3.4f * depth) * scale
            val twinkle = (0.45f + 0.55f * sin(globalTimeSec * (2.2f + depth) + i).toFloat()).coerceIn(0.2f, 1f)
            val baseCol = when {
                i % 5 == 0 -> goldColor
                i % 2 == 0 -> cyanColor
                else -> Color.WHITE
            }
            particlePaint.color = withAlpha(baseCol, (twinkle * 165).toInt())
            canvas.drawCircle(px, py, radius, particlePaint)
        }

        // 3. Style-matched telemetry / orbital / branding motion rings
        if (segment.isBrandingCard) {
            val pulse = 0.85f + 0.18f * sin(globalTimeSec * 2.5f).toFloat()
            paints.ringPaint.strokeWidth = 2.4f * scale
            paints.ringPaint.color = withAlpha(goldColor, 110)
            paints.ringPaint.pathEffect = DashPathEffect(floatArrayOf(24f * scale, 16f * scale), globalTimeSec * 45f * scale)
            canvas.drawCircle(width * 0.5f, height * 0.5f, height * 0.36f * pulse, paints.ringPaint)
        } else if (styleLower.contains("space") || styleLower.contains("sci") || styleLower.contains("tech")) {
            val orbitCx = width * 0.80f
            val orbitCy = height * 0.34f
            paints.ringPaint.strokeWidth = 2.0f * scale
            paints.ringPaint.color = withAlpha(cyanColor, 110)
            paints.ringPaint.pathEffect = DashPathEffect(floatArrayOf(18f * scale, 14f * scale), -globalTimeSec * 55f * scale)
            canvas.drawCircle(orbitCx, orbitCy, 115f * scale, paints.ringPaint)
            paints.ringPaint.color = withAlpha(goldColor, 95)
            paints.ringPaint.pathEffect = DashPathEffect(floatArrayOf(30f * scale, 22f * scale), globalTimeSec * 38f * scale)
            canvas.drawCircle(orbitCx, orbitCy, 155f * scale, paints.ringPaint)
        }

        // 4. Subtle cinema vignette for depth (cached shader per size)
        paints.ensureVignette(width, height)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paints.vignettePaint)

        // 5. Animated lower-third chapter card entrance during opening ~3.0s of scene
        val sceneDurSec = (segment.durationMs / 1000f).coerceAtLeast(3f)
        val elapsedSec = segProgress * sceneDurSec
        if (!segment.isBrandingCard && elapsedSec in 0.08f..3.3f) {
            val animAlpha = when {
                elapsedSec < 0.55f -> easeOutCubic((elapsedSec - 0.08f) / 0.47f)
                elapsedSec > 2.65f -> 1f - ((elapsedSec - 2.65f) / 0.65f).coerceIn(0f, 1f)
                else -> 1f
            }.coerceIn(0f, 1f)
            val slideOffset = (1f - animAlpha) * (-75f * scale)
            val pillTop = height * 0.64f
            val pillLeft = width * 0.06f + slideOffset
            val pillWidth = (width * 0.54f).coerceAtLeast(340f * scale)
            val pillHeight = 74f * scale
            paints.workRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

            paints.pillBgPaint.color = Color.argb((animAlpha * 205).toInt(), 8, 15, 36)
            paints.pillBorderPaint.strokeWidth = 2f * scale
            paints.pillBorderPaint.color = withAlpha(goldColor, (animAlpha * 210).toInt())
            canvas.drawRoundRect(paints.workRect, 14f * scale, 14f * scale, paints.pillBgPaint)
            canvas.drawRoundRect(paints.workRect, 14f * scale, 14f * scale, paints.pillBorderPaint)

            paints.pillAccentPaint.color = withAlpha(accentColor, (animAlpha * 255).toInt())
            paints.workRect2.set(
                paints.workRect.left + 8f * scale,
                paints.workRect.top + 10f * scale,
                paints.workRect.left + 14f * scale,
                paints.workRect.bottom - 10f * scale
            )
            canvas.drawRoundRect(paints.workRect2, 4f * scale, 4f * scale, paints.pillAccentPaint)

            val factLabel = when (segment.factType) {
                "speculation_labeled" -> "HYPOTHESIS / OPEN QUESTION"
                "scientific_context" -> "SCIENTIFIC CONTEXT"
                else -> "VERIFIED CONTEXT"
            }
            paints.metaPaint.color = withAlpha(goldColor, (animAlpha * 255).toInt())
            paints.metaPaint.textSize = 15f * scale
            canvas.drawText(
                "CHAPTER ${segment.sceneNumber} / ${segment.totalScenes} • $factLabel",
                paints.workRect.left + 26f * scale,
                paints.workRect.top + 28f * scale,
                paints.metaPaint
            )

            paints.chTitlePaint.color = Color.argb((animAlpha * 255).toInt(), 248, 250, 252)
            paints.chTitlePaint.textSize = 22f * scale
            canvas.drawText(
                segment.title.take(36),
                paints.workRect.left + 26f * scale,
                paints.workRect.top + 56f * scale,
                paints.chTitlePaint
            )
        }

        // 6. Audio-reactive PCM voice waveform equalizer (top-right corner)
        if (!segment.isBrandingCard) {
            val barCount = 14
            val barW = 5f * scale
            val barGap = 4f * scale
            val totalEqW = barCount * barW + (barCount - 1) * barGap
            val startX = width * 0.94f - totalEqW
            val baseY = height * 0.11f + 26f * scale
            val barPaint = paints.barPaint
            for (b in 0 until barCount) {
                val wave = 0.45f + 0.55f * sin(globalTimeSec * 9.5f + b * 0.72f).toFloat()
                val energy = (0.15f + audioLevel * 0.95f * wave).coerceIn(0.15f, 1.0f)
                val barH = (6f + 28f * energy) * scale
                barPaint.color = if (b % 3 == 0) withAlpha(goldColor, 220) else withAlpha(cyanColor, 210)
                val bx = startX + b * (barW + barGap)
                paints.workRect.set(bx, baseY - barH, bx + barW, baseY)
                canvas.drawRoundRect(paints.workRect, 3f * scale, 3f * scale, barPaint)
            }
        }
    }

    private fun drawSynchronizedSubtitles(
        canvas: Canvas,
        width: Int,
        height: Int,
        subtitle: String,
        chunkProgress: Float,
        paints: FramePaintCache
    ) {
        val scale = width / 1280f
        val subPaint = paints.subTextPaint
        subPaint.textSize = 29f * scale
        subPaint.setShadowLayer(8f * scale, 0f, 2f * scale, Color.BLACK)
        val boxW = (width * 0.82f).toInt()
        val layout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, subPaint, boxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .build()
        val padY = 14f * scale
        val topY = height * 0.87f - layout.height
        val bgLeft = width * 0.09f
        val bgTop = topY - padY
        val bgRight = width * 0.91f
        val bgBottom = topY + layout.height + padY
        paints.workRect.set(bgLeft, bgTop, bgRight, bgBottom)
        paints.subBorderPaint.strokeWidth = 1.6f * scale
        canvas.drawRoundRect(paints.workRect, 16f * scale, 16f * scale, paints.subBoxPaint)
        canvas.drawRoundRect(paints.workRect, 16f * scale, 16f * scale, paints.subBorderPaint)

        // Subtle animated speech progress accent bar along top edge of caption pill
        val progressW = (paints.workRect.width() - 28f * scale) * chunkProgress.coerceIn(0.05f, 1f)
        paints.workRect2.set(
            bgLeft + 14f * scale,
            bgTop + 3f * scale,
            bgLeft + 14f * scale + progressW,
            bgTop + 6f * scale
        )
        canvas.drawRoundRect(paints.workRect2, 2f * scale, 2f * scale, paints.subProgPaint)

        canvas.save()
        canvas.translate(bgLeft, topY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sampleSize = 1
            while ((bounds.outHeight / 2) / sampleSize >= reqHeight && (bounds.outWidth / 2) / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Exception) {
            null
        }
    }

    private class FramePaintCache(subtitleTypeface: Typeface) {
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val dissolvePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val vignettePaint = Paint()
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val pillAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = subtitleTypeface }
        val chTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = subtitleTypeface }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = subtitleTypeface
        }
        val subBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 6, 10, 26)
        }
        val subBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(150, 64, 196, 255)
        }
        val subProgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 245, 196, 81)
        }
        val workRect = RectF()
        val workRect2 = RectF()
        private var vignetteW = -1
        private var vignetteH = -1

        fun ensureVignette(width: Int, height: Int) {
            if (vignetteW != width || vignetteH != height) {
                vignetteW = width
                vignetteH = height
                vignettePaint.shader = RadialGradient(
                    width * 0.5f, height * 0.5f, maxOf(width, height) * 0.72f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(120, 3, 5, 14)),
                    floatArrayOf(0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t else 1f - ((-2f * t + 2f) * (-2f * t + 2f) * (-2f * t + 2f)) / 2f
    }

    private fun easeOutCubic(t: Float): Float {
        val u = (1f - t.coerceIn(0f, 1f))
        return 1f - u * u * u
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun feedAudioSamples(
        audioCodec: MediaCodec,
        pcm: ShortArray,
        startSample: Int,
        endSample: Int,
        sampleRate: Int
    ): Int {
        var cursor = startSample
        while (cursor < endSample) {
            val inIdx = audioCodec.dequeueInputBuffer(5_000L)
            if (inIdx < 0) break
            val inBuf = audioCodec.getInputBuffer(inIdx) ?: break
            inBuf.clear()
            val maxShorts = inBuf.remaining() / 2
            val count = minOf(maxShorts, endSample - cursor)
            if (count <= 0) break
            val byteBuf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                byteBuf.putShort(pcm[cursor + i])
            }
            inBuf.put(byteBuf.array())
            val ptsUs = (cursor.toLong() * 1_000_000L) / sampleRate
            audioCodec.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
            cursor += count
        }
        return cursor
    }

    private fun drainCodec(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        onFormatChanged: (MediaFormat) -> Unit,
        onEncodedSample: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ): Boolean {
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, 0L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> return true
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormatChanged(codec.outputFormat)
                outIdx >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        onEncodedSample(outBuf, bufferInfo)
                    }
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIdx, false)
                    if (eos) return false
                }
            }
        }
    }

    private fun drainUntilEos(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        onFormatChanged: (MediaFormat) -> Unit,
        onEncodedSample: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        var attempts = 0
        while (attempts < 40) {
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> attempts++
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormatChanged(codec.outputFormat)
                outIdx >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        onEncodedSample(outBuf, bufferInfo)
                    }
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIdx, false)
                    if (eos) break
                }
            }
        }
    }

    private fun formatSrtTime(ms: Long): String {
        val totalSec = ms / 1000
        val remMs = ms % 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return String.format("%02d:%02d:%02d,%03d", hours, mins, secs, remMs)
    }
}
