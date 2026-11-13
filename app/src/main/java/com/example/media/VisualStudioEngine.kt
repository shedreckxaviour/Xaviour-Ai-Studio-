package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.LogoPositionOption
import com.example.data.model.LogoSizeOption
import com.example.data.model.VideoScene
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class VisualStudioEngine(private val context: Context) {

    private val displayTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT_BOLD
    }

    private val bodyTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.SANS_SERIF
    }

    fun renderSceneVisualFrame(
        scene: VideoScene,
        totalScenes: Int,
        videoStyle: String,
        width: Int,
        height: Int,
        outputFile: File
    ): Pair<File, String> {
        val userImage = scene.userImagePath.takeIf { it.isNotBlank() }?.let { loadUserImageFromPathOrUri(it, width, height) }
        return if (userImage != null) {
            renderUserPhotoSlide(scene, totalScenes, videoStyle, userImage, width, height, outputFile)
            userImage.recycle()
            outputFile to "user_uploaded_image"
        } else {
            renderFreeMotionSlide(scene, totalScenes, videoStyle, width, height, outputFile)
            outputFile to "free_motion_slide"
        }
    }

    private fun renderUserPhotoSlide(
        scene: VideoScene,
        totalScenes: Int,
        videoStyle: String,
        photo: Bitmap,
        width: Int,
        height: Int,
        outputFile: File
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = width / 1280f
        val accentColor = parseHexSafe(scene.accentHex, Color.parseColor("#2979FF"))
        val goldColor = Color.parseColor("#F5C451")

        // Draw user's uploaded photo scaled to fill frame cleanly
        drawBitmapCenterCrop(canvas, photo, RectF(0f, 0f, width.toFloat(), height.toFloat()))

        // Cinematic dark gradient scrim + subtle edge glow for rich depth
        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.argb(190, 4, 7, 18), Color.argb(55, 4, 7, 18), Color.argb(215, 4, 7, 18)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)

        // Subtle color-grade accent rim
        val rimGlow = Paint().apply {
            shader = RadialGradient(
                width * 0.8f, height * 0.2f, width * 0.55f,
                intArrayOf(withAlpha(accentColor, 55), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), rimGlow)

        drawSceneBadgeAndTitle(canvas, scene, totalScenes, videoStyle, width, height, scale, accentColor, goldColor)
        saveBitmapPng(bitmap, outputFile)
        bitmap.recycle()
    }

    fun renderFreeMotionSlide(
        scene: VideoScene,
        totalScenes: Int,
        videoStyle: String,
        width: Int,
        height: Int,
        outputFile: File
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val accentColor = parseHexSafe(scene.accentHex, Color.parseColor("#2979FF"))
        val goldColor = Color.parseColor("#F5C451")
        val cyanColor = Color.parseColor("#40C4FF")
        val scale = width / 1280f

        // 1. Style-matched high-res documentary backdrop + deep cosmic scrim
        val backdropRes = resolveStyleBackdropRes(videoStyle, scene.sceneIndex)
        val backdropBmp = decodeSampledResourceBitmap(backdropRes, width, height)
        if (backdropBmp != null) {
            canvas.save()
            if (scene.sceneIndex % 2 == 1) {
                // Alternate subtle crop/shift across scenes for visual variety while keeping text unflipped
                canvas.scale(-1f, 1f, width * 0.5f, height * 0.5f)
            }
            drawBitmapCenterCrop(canvas, backdropBmp, RectF(0f, 0f, width.toFloat(), height.toFloat()))
            canvas.restore()
            backdropBmp.recycle()

            val leftReadabilityScrim = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), 0f,
                    intArrayOf(Color.argb(225, 3, 6, 17), Color.argb(150, 5, 10, 26), Color.argb(55, 5, 10, 26)),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), leftReadabilityScrim)
        } else {
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.parseColor("#030611"), Color.parseColor("#0B1636"), Color.parseColor("#040814")),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        // 2. Dual-layer volumetric nebula glow
        val rng = Random(scene.sceneIndex * 9973 + scene.title.hashCode())
        val glowX = width * (0.55f + rng.nextFloat() * 0.25f)
        val glowY = height * (0.28f + rng.nextFloat() * 0.30f)
        val nebulaPaint = Paint().apply {
            shader = RadialGradient(
                glowX, glowY, width * 0.62f,
                intArrayOf(withAlpha(accentColor, 130), withAlpha(cyanColor, 50), Color.TRANSPARENT),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), nebulaPaint)

        val secondaryGlow = Paint().apply {
            shader = RadialGradient(
                width * 0.22f, height * 0.72f, width * 0.45f,
                intArrayOf(withAlpha(goldColor, 65), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), secondaryGlow)

        // 3. Multi-depth starfield & constellation links
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val points = ArrayList<Pair<Float, Float>>(24)
        repeat(140) { idx ->
            val sx = rng.nextFloat() * width
            val sy = rng.nextFloat() * height
            val r = (1.0f + rng.nextFloat() * 2.6f) * scale
            starPaint.color = when {
                idx % 8 == 0 -> withAlpha(goldColor, 195)
                idx % 4 == 0 -> withAlpha(cyanColor, 175)
                else -> withAlpha(Color.WHITE, 85 + rng.nextInt(155))
            }
            canvas.drawCircle(sx, sy, r, starPaint)
            if (idx < 24 && sx > width * 0.42f) {
                points.add(sx to sy)
            }
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * scale
            color = withAlpha(cyanColor, 65)
        }
        for (i in 0 until points.size - 1) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[i + 1]
            val distSq = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
            if (distSq < (width * 0.24f) * (width * 0.24f)) {
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }

        // 4. Style-tuned focal geometry on right visual focal area
        val centerX = width * 0.74f
        val centerY = height * 0.44f
        val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * scale
        }
        val styleLower = videoStyle.lowercase()
        when {
            styleLower.contains("science") || styleLower.contains("technology") || styleLower.contains("educational") -> {
                // Concentric quantum / wave arcs + node halo
                for (ring in 1..5) {
                    val radius = (width * 0.085f) * ring
                    orbitPaint.color = if (ring % 2 == 0) withAlpha(goldColor, 95) else withAlpha(cyanColor, 95)
                    canvas.drawCircle(centerX, centerY, radius, orbitPaint)
                }
                val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.6f * scale
                    color = withAlpha(goldColor, 130)
                }
                for (waveIdx in 0..2) {
                    val baseY = height * (0.36f + waveIdx * 0.09f)
                    var prevX = width * 0.45f
                    var prevY = baseY
                    var step = 0
                    while (step <= 32) {
                        val t = step / 32f
                        val x = width * (0.45f + t * 0.50f)
                        val y = baseY + sin(t * 9.42f + waveIdx * 1.2f + scene.sceneIndex) * (24f * scale)
                        if (step > 0) canvas.drawLine(prevX, prevY, x, y, wavePaint)
                        prevX = x
                        prevY = y
                        step++
                    }
                }
            }
            else -> {
                // Planetary / cosmic horizon rings + glowing celestial core
                val coreGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        centerX, centerY, width * 0.14f,
                        intArrayOf(withAlpha(goldColor, 150), withAlpha(accentColor, 90), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(centerX, centerY, width * 0.14f, coreGlow)
                for (ring in 1..4) {
                    val radius = (width * 0.11f) * ring
                    orbitPaint.color = if (ring % 2 == 0) withAlpha(goldColor, 90) else withAlpha(accentColor, 95)
                    canvas.drawCircle(centerX, centerY, radius, orbitPaint)
                    val angle = rng.nextFloat() * 6.28f
                    val dotX = centerX + cos(angle) * radius
                    val dotY = centerY + sin(angle) * radius
                    starPaint.color = if (ring % 2 == 0) goldColor else cyanColor
                    canvas.drawCircle(dotX, dotY, 6.5f * scale, starPaint)
                }
            }
        }

        drawSceneBadgeAndTitle(canvas, scene, totalScenes, videoStyle, width, height, scale, accentColor, goldColor)

        saveBitmapPng(bitmap, outputFile)
        bitmap.recycle()
        return outputFile
    }

    private fun drawSceneBadgeAndTitle(
        canvas: Canvas,
        scene: VideoScene,
        totalScenes: Int,
        videoStyle: String,
        width: Int,
        height: Int,
        scale: Float,
        accentColor: Int,
        goldColor: Int
    ) {
        val marginX = width * 0.08f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accentColor, 72)
        }
        val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            color = withAlpha(goldColor, 190)
        }
        val badgeText = "SCENE ${scene.sceneIndex + 1} / $totalScenes • ${videoStyle.uppercase().take(22)}"
        val badgeWidth = (360f + badgeText.length * 6.5f).coerceAtMost(width * 0.72f / scale) * scale
        val badgeRect = RectF(marginX, height * 0.10f, marginX + badgeWidth, height * 0.10f + 42f * scale)
        canvas.drawRoundRect(badgeRect, 14f * scale, 14f * scale, badgePaint)
        canvas.drawRoundRect(badgeRect, 14f * scale, 14f * scale, badgeBorder)

        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldColor
            textSize = 18f * scale
            typeface = displayTypeface
        }
        canvas.drawText(
            badgeText,
            badgeRect.left + 16f * scale,
            badgeRect.centerY() + 6.5f * scale,
            badgeTextPaint
        )

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f * scale
            typeface = displayTypeface
            setShadowLayer(14f * scale, 0f, 4f * scale, Color.BLACK)
        }
        val titleWidth = (width * 0.62f).toInt()
        val titleLayout = StaticLayout.Builder.obtain(scene.title, 0, scene.title.length, titlePaint, titleWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(marginX, height * 0.21f)
        titleLayout.draw(canvas)
        canvas.restore()

        val promptTop = height * 0.21f + titleLayout.height + 20f * scale
        val promptPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCE6F5")
            textSize = 23f * scale
            typeface = bodyTypeface
            setShadowLayer(8f * scale, 0f, 2f * scale, Color.BLACK)
        }
        val visualExcerpt = scene.visualPrompt.take(150)
        val promptLayout = StaticLayout.Builder.obtain(visualExcerpt, 0, visualExcerpt.length, promptPaint, (width * 0.56f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(marginX, promptTop)
        promptLayout.draw(canvas)
        canvas.restore()
    }

    fun renderBrandingIntroFrame(
        videoTitle: String,
        channelName: String,
        logoUriString: String?,
        logoPosition: String = "top_center",
        logoSize: String = "medium",
        width: Int,
        height: Int,
        outputFile: File
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = width / 1280f

        val bgPaint = Paint().apply {
            shader = RadialGradient(
                width * 0.5f, height * 0.45f, width * 0.7f,
                intArrayOf(Color.parseColor("#13234E"), Color.parseColor("#080E22"), Color.parseColor("#040610")),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawGalaxyParticles(canvas, width, height, seed = 42, goldHighlight = true)

        val sizeOpt = LogoSizeOption.entries.firstOrNull { it.code == logoSize } ?: LogoSizeOption.MEDIUM
        val logoBitmap = loadUserLogoOrPlaceholder(logoUriString)
        if (logoBitmap != null) {
            val boxSize = 115f * scale * sizeOpt.introDpScale
            val target = resolveLogoRect(logoPosition, width, height, boxSize, isOutro = false)
            drawBitmapAspectFit(canvas, logoBitmap, target)
            logoBitmap.recycle()
        }

        val channelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5C451")
            textSize = 24f * scale
            typeface = displayTypeface
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
        canvas.drawText(channelName.uppercase(), width * 0.5f, height * 0.44f, channelPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f * scale
            typeface = displayTypeface
            setShadowLayer(16f * scale, 0f, 4f * scale, Color.BLACK)
        }
        val layout = StaticLayout.Builder.obtain(videoTitle, 0, videoTitle.length, titlePaint, (width * 0.8f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(width * 0.1f, height * 0.52f)
        layout.draw(canvas)
        canvas.restore()

        saveBitmapPng(bitmap, outputFile)
        bitmap.recycle()
        return outputFile
    }

    fun renderBrandingOutroFrame(
        tagline: String,
        channelName: String = "XAVIOUR EXPLAINS",
        logoUriString: String?,
        logoPosition: String = "top_center",
        logoSize: String = "medium",
        width: Int,
        height: Int,
        outputFile: File
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = width / 1280f

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.parseColor("#040611"), Color.parseColor("#0D1B3E"), Color.parseColor("#050713")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val goldLight = Paint().apply {
            shader = RadialGradient(
                width * 0.5f, height * 0.44f, width * 0.45f,
                intArrayOf(Color.argb(95, 245, 196, 81), Color.argb(65, 41, 121, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), goldLight)
        drawGalaxyParticles(canvas, width, height, seed = 777, goldHighlight = true)

        val sizeOpt = LogoSizeOption.entries.firstOrNull { it.code == logoSize } ?: LogoSizeOption.MEDIUM
        val logoBitmap = loadUserLogoOrPlaceholder(logoUriString)
        if (logoBitmap != null) {
            val boxSize = 220f * scale * sizeOpt.outroDpScale
            val target = resolveLogoRect(logoPosition, width, height, boxSize, isOutro = true)
            drawBitmapAspectFit(canvas, logoBitmap, target)
            logoBitmap.recycle()
        }

        val chPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f * scale
            typeface = displayTypeface
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
        }
        canvas.drawText(channelName.uppercase(), width * 0.5f, height * 0.65f, chPaint)

        val taglinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5C451")
            textSize = 36f * scale
            typeface = displayTypeface
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
            setShadowLayer(12f * scale, 0f, 2f * scale, Color.BLACK)
        }
        canvas.drawText(tagline.uppercase(), width * 0.5f, height * 0.74f, taglinePaint)

        saveBitmapPng(bitmap, outputFile)
        bitmap.recycle()
        return outputFile
    }

    fun renderBrandingPreviewPair(
        channelName: String,
        tagline: String,
        logoUriString: String?,
        logoPosition: String,
        logoSize: String,
        previewDir: File
    ): Pair<File, File> {
        previewDir.mkdirs()
        val intro = File(previewDir, "branding_preview_intro.png")
        val outro = File(previewDir, "branding_preview_outro.png")
        renderBrandingIntroFrame(
            videoTitle = "Sample Documentary Opening Title",
            channelName = channelName,
            logoUriString = logoUriString,
            logoPosition = logoPosition,
            logoSize = logoSize,
            width = 960,
            height = 540,
            outputFile = intro
        )
        renderBrandingOutroFrame(
            tagline = tagline,
            channelName = channelName,
            logoUriString = logoUriString,
            logoPosition = logoPosition,
            logoSize = logoSize,
            width = 960,
            height = 540,
            outputFile = outro
        )
        return intro to outro
    }

    private fun resolveLogoRect(
        positionCode: String,
        width: Int,
        height: Int,
        boxSize: Float,
        isOutro: Boolean
    ): RectF {
        val margin = width * 0.06f
        return when (positionCode) {
            LogoPositionOption.TOP_LEFT.code -> RectF(margin, margin, margin + boxSize, margin + boxSize)
            LogoPositionOption.TOP_RIGHT.code -> RectF(width - margin - boxSize, margin, width - margin, margin + boxSize)
            LogoPositionOption.BOTTOM_RIGHT.code -> RectF(width - margin - boxSize, height - margin - boxSize, width - margin, height - margin)
            LogoPositionOption.CENTER.code -> RectF(width * 0.5f - boxSize / 2f, height * 0.36f - boxSize / 2f, width * 0.5f + boxSize / 2f, height * 0.36f + boxSize / 2f)
            else -> {
                val topY = if (isOutro) height * 0.20f else height * 0.16f
                RectF(width * 0.5f - boxSize / 2f, topY, width * 0.5f + boxSize / 2f, topY + boxSize)
            }
        }
    }

    fun renderYoutubeThumbnail(
        titleText: String,
        subtitleBadge: String,
        subjectImagePath: String?,
        outputFile: File
    ): File {
        val width = 1280
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bg = subjectImagePath?.let { path ->
            loadUserImageFromPathOrUri(path, width, height)
        } ?: decodeSampledResourceBitmap(resolveStyleBackdropRes(subtitleBadge, 0), width, height)

        if (bg != null) {
            drawBitmapCenterCrop(canvas, bg, RectF(0f, 0f, width.toFloat(), height.toFloat()))
            bg.recycle()
        } else {
            canvas.drawColor(Color.parseColor("#060B1E"))
        }

        val overlay = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(Color.argb(235, 5, 8, 20), Color.argb(175, 5, 8, 20), Color.argb(45, 5, 8, 20)),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlay)

        val stripePaint = Paint().apply { color = Color.parseColor("#F5C451") }
        canvas.drawRect(56f, 110f, 68f, 610f, stripePaint)

        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40C4FF")
            textSize = 28f
            typeface = displayTypeface
            letterSpacing = 0.1f
        }
        canvas.drawText(subtitleBadge.uppercase().take(36), 96f, 155f, badgePaint)

        val headline = titleText.uppercase().ifBlank { "THE MYSTERY EXPLAINED" }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (headline.length > 28) 68f else 84f
            typeface = displayTypeface
            setShadowLayer(18f, 0f, 6f, Color.BLACK)
        }
        val layout = StaticLayout.Builder.obtain(headline, 0, headline.length, titlePaint, 820)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(4)
            .build()
        canvas.save()
        canvas.translate(96f, 205f)
        layout.draw(canvas)
        canvas.restore()

        saveBitmapPng(bitmap, outputFile)
        bitmap.recycle()
        return outputFile
    }

    private fun resolveStyleBackdropRes(videoStyle: String, sceneIndex: Int): Int {
        val style = videoStyle.lowercase()
        return when {
            style.contains("space") || style.contains("sci") || style.contains("mystery") -> {
                if (sceneIndex % 3 == 2) R.drawable.img_galaxy_studio_hero else R.drawable.img_backdrop_cosmos
            }
            style.contains("science") || style.contains("tech") || style.contains("educat") -> {
                if (sceneIndex % 3 == 2) R.drawable.img_backdrop_cosmos else R.drawable.img_backdrop_science
            }
            else -> {
                if (sceneIndex % 3 == 2) R.drawable.img_backdrop_cosmos else R.drawable.img_backdrop_cinema
            }
        }
    }

    private fun loadUserImageFromPathOrUri(
        pathOrUri: String,
        reqWidth: Int = 1280,
        reqHeight: Int = 720
    ): Bitmap? {
        return try {
            val file = File(pathOrUri)
            if (file.exists()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } else {
                val uri = Uri.parse(pathOrUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledResourceBitmap(resId: Int, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(context.resources, resId, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun loadUserLogoOrPlaceholder(logoUriString: String?, targetSize: Int = 512): Bitmap? {
        if (!logoUriString.isNullOrBlank()) {
            try {
                loadUserImageFromPathOrUri(logoUriString, targetSize, targetSize)?.let { return it }
            } catch (_: Exception) {
            }
        }
        return decodeSampledResourceBitmap(R.drawable.img_xaviour_logo_placeholder, targetSize, targetSize)
    }

    private fun drawBitmapAspectFit(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val dstRatio = target.width() / target.height().coerceAtLeast(1f)
        val drawRect = if (srcRatio > dstRatio) {
            val h = target.width() / srcRatio
            val top = target.centerY() - h / 2f
            RectF(target.left, top, target.right, top + h)
        } else {
            val w = target.height() * srcRatio
            val left = target.centerX() - w / 2f
            RectF(left, target.top, left + w, target.bottom)
        }
        canvas.drawBitmap(bitmap, null, drawRect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawBitmapCenterCrop(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val scale = maxOf(
            target.width() / bitmap.width.toFloat().coerceAtLeast(1f),
            target.height() / bitmap.height.toFloat().coerceAtLeast(1f)
        )
        val scaledW = bitmap.width * scale
        val scaledH = bitmap.height * scale
        val left = target.centerX() - scaledW / 2f
        val top = target.centerY() - scaledH / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + scaledW, top + scaledH),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun drawGalaxyParticles(canvas: Canvas, width: Int, height: Int, seed: Int, goldHighlight: Boolean) {
        val rng = Random(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(140) { idx ->
            val x = rng.nextFloat() * width
            val y = rng.nextFloat() * height
            val r = (1.2f + rng.nextFloat() * 3.0f) * (width / 1280f)
            paint.color = when {
                goldHighlight && idx % 6 == 0 -> Color.argb(200, 245, 196, 81)
                idx % 3 == 0 -> Color.argb(170, 64, 196, 255)
                else -> Color.argb(110 + rng.nextInt(120), 255, 255, 255)
            }
            canvas.drawCircle(x, y, r, paint)
        }
    }

    private fun saveBitmapPng(bitmap: Bitmap, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
    }

    private fun parseHexSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
