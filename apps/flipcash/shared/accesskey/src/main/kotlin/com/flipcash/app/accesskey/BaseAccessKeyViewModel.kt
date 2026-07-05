package com.flipcash.app.accesskey

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.storage.MediaSaver
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.app.theme.internal.Flipcash2ColorSpec
import com.flipcash.services.user.UserManager
import com.flipcash.shared.accesskey.R
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.manager.TopBarManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.theme.Alert
import com.getcode.theme.GradientSpec
import com.getcode.theme.White
import com.getcode.ui.utils.toAGColor
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.decodeBase64
import com.getcode.utils.trace
import androidx.lifecycle.ViewModel
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.getcode.vendor.Base58
import kotlinx.coroutines.flow.mapNotNull
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.getcode.theme.R as themeR


data class AccessKeyUiModel(
    val entropyB64: String? = null,
    val exportState: LoadingSuccessState = LoadingSuccessState(),
    val skipState: LoadingSuccessState = LoadingSuccessState(),
    val words: List<String> = listOf(),
    val wordsFormatted: String = "",
    val accessKeyBitmap: Bitmap? = null,
    val accessKeyCroppedBitmap: Bitmap? = null,
)

abstract class BaseAccessKeyViewModel(
    private val resources: ResourceHelper,
    private val mnemonicManager: MnemonicManager,
    private val mediaSaver: MediaSaver,
    userManager: UserManager,
    private val qrCodeGenerator: QRCodeGenerator,
    protected val dispatchers: DispatcherProvider,
) : ViewModel() {
    val uiFlow = MutableStateFlow(AccessKeyUiModel())

    init {
        userManager.state
            .distinctUntilChangedBy { it.entropy }
            .mapNotNull { it.entropy }
            .take(1)
            .onEach { initWithEntropy(it) }
            .launchIn(viewModelScope)
    }

    private fun initWithEntropy(entropyB64: String) {
        if (uiFlow.value.entropyB64 == entropyB64) return
        val words = mnemonicManager.fromEntropyBase64(entropyB64).words
        val wordsFormatted = getAccessKeyText(words).joinToString("\n")

        uiFlow.update {
            it.copy(
                entropyB64 = entropyB64,
                words = words,
                wordsFormatted = wordsFormatted
            )
        }

        viewModelScope.launch(dispatchers.IO) {
            runCatching {
                val accessKeyBitmap = createBitmapForExport(drawBackground = true, words = words, entropyB64 = entropyB64)
                val accessKeyBitmapDisplay = createBitmapForExport(drawBackground = false, words, entropyB64)
                val accessKeyCroppedBitmap =
                    Bitmap.createBitmap(accessKeyBitmapDisplay, 0, 500, 1200, 1450)

                uiFlow.update {
                    it.copy(
                        accessKeyBitmap = accessKeyBitmap,
                        accessKeyCroppedBitmap = accessKeyCroppedBitmap
                    )
                }
            }.onFailure { error ->
                if (error is kotlin.coroutines.cancellation.CancellationException) throw error
                // Bitmap rendering can fail on some OEM devices with
                // "width and height must be > 0". Report as a non-fatal instead
                // of crashing; the UI handles null bitmaps. Refs Bugsnag 6a4528ca.
                trace(
                    tag = "access-key",
                    message = "Failed to render access key bitmap",
                    error = error,
                    type = TraceType.Error,
                )
            }
        }
    }

    private fun getAccessKeyText(words: List<String>): List<String> {
        return listOf(
            words.subList(0, 6).joinToString(" "),
            words.subList(6, 12).joinToString(" ")
        )
    }

    // These layout dimensions are compile-time constants so they are inlined at every
    // use site by kotlinc. They must NOT be instance fields: createBitmapForExport runs
    // on dispatchers.IO and can be kicked off from the init{} flow before the constructor
    // finishes initializing instance fields, so a background thread could read them as 0
    // (their default) with no happens-before — surfacing as
    // "IllegalArgumentException: width and height must be > 0" on release/R8 builds only.
    // Refs Bugsnag 6a457e5e.
    private companion object {
        const val TARGET_WIDTH = 1200
        const val TARGET_HEIGHT = 2500

        const val LOGO_WIDTH_RATIO = 92.4f
        const val LOGO_HEIGHT = 132
        const val QR_CODE_SIZE = 360

        const val BG_TOP_OFFSET = 550
        const val LOGO_TOP_OFFSET = 770
        const val QR_TOP_OFFSET = 980
        const val KEY_TEXT_TOP_OFFSET = 1600
        const val WARNING_TEXT_TOP_OFFSET = 200
        const val HELPER_TEXT_TOP_OFFSET = 2000
    }

    protected suspend fun saveBitmapToFile(): Result<Boolean> {
        uiFlow.update { it.copy(exportState = LoadingSuccessState(loading = true)) }
        val bitmap = uiFlow.value.accessKeyBitmap
            ?: return Result.failure(IllegalStateException("No access key?"))

        return withContext(dispatchers.IO) {
            runCatching {
                val date: DateFormat = SimpleDateFormat("yyy-MM-dd-h-mm", Locale.CANADA)
                val filename = "Flipcash-Recovery-${date.format(Date())}.png"
                mediaSaver.saveBitmap(bitmap, filename)
            }
        }.onFailure {
            getAccessKeySaveError()
            uiFlow.update { it.copy(exportState = LoadingSuccessState()) }
        }
    }

    private fun renderLogo(resId: Int): Bitmap? =
        resources.getDrawable(resId)?.toBitmap(LOGO_WIDTH_RATIO.roundToInt(), LOGO_HEIGHT)

    private fun createBitmapForExport(
        drawBackground: Boolean = true,
        words: List<String>,
        entropyB64: String
    ): Bitmap {
        val accessKeyText = getAccessKeyText(words)

        val spec = Flipcash2ColorSpec.accessKey
        val accessKeyBg = createGlowBitmap(spec, 812, 1353)

        // Rasterizing the vector logo can fail on some budget OEM devices with
        // "width and height must be > 0" — the failure originates inside
        // VectorDrawable's internal cache-bitmap allocation, not our dimensions.
        // Fall back to a pre-rasterized PNG, which goes through the far more
        // robust BitmapDrawable decode/scale path; if even that fails, render the
        // recovery image without the logo. Refs Bugsnag 6a47a1e0.
        val imageLogo: Bitmap? = runCatching { renderLogo(R.drawable.ic_flipcash_logo) }
            .recoverCatching { renderLogo(R.drawable.ic_flipcash_logo_raster) }
            .onFailure { error ->
                if (error is kotlin.coroutines.cancellation.CancellationException) throw error
                trace(
                    tag = "access-key",
                    message = "Failed to render access key logo; rendering without it",
                    error = error,
                    type = TraceType.Error,
                )
            }
            .getOrNull()

        val imageOut = createBitmap(TARGET_WIDTH, TARGET_HEIGHT).applyCanvas {
            val accessBgActualWidth =
                accessKeyBg.getScaledWidth(resources.displayMetrics)

            if (drawBackground) {
                val paintBackground = Paint()
                paintBackground.color = Flipcash2ColorSpec.primary.toAGColor()
                paintBackground.style = Paint.Style.FILL
                drawPaint(paintBackground)
            }

            val topTextChunks = resources.getString(R.string.subtitle_accessKeySnapshotWarning)
                .split(" ", "\n")
                .chunked(7)
                .map { it.joinToString(" ") }

            topTextChunks.forEachIndexed { index, text ->
                drawText(
                    canvas = this,
                    y = WARNING_TEXT_TOP_OFFSET + (60 * (index + 1)),
                    sizePx = 40,
                    color = Alert.toAGColor(),
                    text = text
                )
            }

            drawBitmap(
                accessKeyBg,
                (((TARGET_WIDTH - accessBgActualWidth) / 2)).toFloat(),
                BG_TOP_OFFSET.toFloat(),
                null
            )

            // Draw border
            spec.borderColor?.let { borderColor ->
                val borderPaint = Paint().apply {
                    color = borderColor.toAGColor()
                    style = Paint.Style.STROKE
                    strokeWidth = spec.borderWidth
                    isAntiAlias = true
                }

                val borderInset = spec.borderWidth / 2
                val borderRect = RectF(
                    ((TARGET_WIDTH - accessBgActualWidth) / 2f) + borderInset,
                    BG_TOP_OFFSET.toFloat() + borderInset,
                    ((TARGET_WIDTH + accessBgActualWidth) / 2f) - borderInset,
                    BG_TOP_OFFSET.toFloat() + accessKeyBg.height - borderInset
                )

                // Adjust corner radius to match your card
                val cornerRadius = 40f
                drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)
            }

            imageLogo?.let { logo ->
                drawBitmap(
                    logo,
                    ((TARGET_WIDTH - LOGO_WIDTH_RATIO) / 2),
                    LOGO_TOP_OFFSET.toFloat(),
                    null
                )
            }

            getQrCode(entropyB64)?.let { bitmap ->
                drawBitmap(
                    bitmap,
                    ((TARGET_WIDTH - QR_CODE_SIZE) / 2).toFloat(),
                    QR_TOP_OFFSET.toFloat(),
                    null
                )
            }

            drawText(
                canvas = this,
                y = KEY_TEXT_TOP_OFFSET,
                sizePx = 32,
                color = White.toAGColor(),
                text = accessKeyText[0]
            )

            drawText(
                canvas = this,
                y = KEY_TEXT_TOP_OFFSET + 40,
                sizePx = 32,
                color = White.toAGColor(),
                text = accessKeyText[1]
            )

            val bottomTextChunks = resources.getString(R.string.subtitle_accessKeySnapshotDescription)
                .split(" ")
                .chunked(8)
                .map { it.joinToString(" ") }

            bottomTextChunks.forEachIndexed { index, text ->
                drawText(
                    canvas = this,
                    y = HELPER_TEXT_TOP_OFFSET + (60 * (index + 1)),
                    sizePx = 40,
                    color = White.toAGColor(),
                    text = text
                )
            }

        }
        return imageOut
    }

    fun createGlowBitmap(
        gradientSpec: GradientSpec,
        width: Int,
        height: Int,
        cornerRadius: Float = 40f
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val clipPath = Path().apply {
            addRoundRect(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )
        }
        canvas.clipPath(clipPath)

        val diagonalGradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height.toFloat(),
                width.toFloat(), 0f,
                gradientSpec.colorsArray,
                gradientSpec.stopsArray,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), diagonalGradient)

        return bitmap
    }

    private fun getQrCode(entropyB64: String): Bitmap? {
        val base58 = Base58.encode(entropyB64.decodeBase64())
        val url = "${resources.getString(R.string.app_root_url)}/login?data=$base58"

        return qrCodeGenerator.generate(url, QR_CODE_SIZE)
    }

    private fun drawText(
        canvas: Canvas,
        y: Int,
        x: Int? = null,
        sizePx: Int,
        color: Int,
        text: String
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = color
        textPaint.textSize = sizePx.toFloat()
        textPaint.typeface = Typeface.create(
            resources.getFont(themeR.font.avenir_next_demi),
            Typeface.BOLD
        )

        val bounds1 = android.graphics.Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds1)
        val xV: Int = x ?: ((TARGET_WIDTH - bounds1.width()) / 2)
        canvas.drawText(text, xV.toFloat(), y.toFloat(), textPaint)
    }

    private fun getAccessKeySaveError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_failedToSave),
        resources.getString(R.string.error_description_failedToSave),
    )
}
