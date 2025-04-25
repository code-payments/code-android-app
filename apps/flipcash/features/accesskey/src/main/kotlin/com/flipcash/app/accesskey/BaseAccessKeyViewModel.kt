package com.flipcash.app.accesskey

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.internal.extensions.save
import com.flipcash.app.core.storage.MediaScanner
import com.flipcash.features.accesskey.R
import com.flipcash.services.user.UserManager
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.manager.TopBarManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.theme.Alert
import com.getcode.theme.Brand
import com.getcode.theme.White
import com.getcode.ui.utils.toAGColor
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.decodeBase64
import com.getcode.view.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kin.sdk.base.tools.Base58
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.getcode.theme.R as themeR


data class AccessKeyUiModel(
    val entropyB64: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val isEnabled: Boolean = true,
    val words: List<String> = listOf(),
    val wordsFormatted: String = "",
    val accessKeyBitmap: Bitmap? = null,
    val accessKeyCroppedBitmap: Bitmap? = null,
)

abstract class BaseAccessKeyViewModel(
    private val resources: ResourceHelper,
    private val mnemonicManager: MnemonicManager,
    private val mediaScanner: MediaScanner,
    userManager: UserManager,
    private val qrCodeGenerator: QRCodeGenerator
) : BaseViewModel(resources) {
    val uiFlow = MutableStateFlow(AccessKeyUiModel())

    init {
        userManager.state
            .distinctUntilChangedBy { it.entropy }
            .map { it.entropy }
            .filterNotNull()
            .take(1)
            .onEach { initWithEntropy(it) }
            .launchIn(viewModelScope)
    }

    private fun initWithEntropy(entropyB64: String) {
        if (uiFlow.value.entropyB64 == entropyB64) return
        val words = mnemonicManager.fromEntropyBase64(entropyB64).words
        val wordsFormatted = getAccessKeyText(words).joinToString("\n")

        uiFlow.value = uiFlow.value.copy(
            entropyB64 = entropyB64,
            words = words,
            wordsFormatted = wordsFormatted
        )

        CoroutineScope(Dispatchers.IO).launch {
            val accessKeyBitmap = createBitmapForExport(words = words, entropyB64 = entropyB64)
            val accessKeyBitmapDisplay =
                createBitmapForExport(drawBackground = false, words, entropyB64)
            val accessKeyCroppedBitmap =
                Bitmap.createBitmap(accessKeyBitmapDisplay, 0, 500, 1200, 1450)

            uiFlow.value = uiFlow.value.copy(
                accessKeyBitmap = accessKeyBitmap,
                accessKeyCroppedBitmap = accessKeyCroppedBitmap
            )
        }
    }

    private fun getAccessKeyText(words: List<String>): List<String> {
        return listOf(
            words.subList(0, 6).joinToString(" "),
            words.subList(6, 12).joinToString(" ")
        )
    }

    private val targetWidth = 1200
    private val targetHeight = 2500

    private val logoWidth = 92.4f
    private val logoHeight = 132
    private val qrCodeSize = 360

    private val bgTopOffset = 550
    private val logoTopOffset = 770
    private val qrTopOffset = 980
    private val keyTextTopOffset = 1600
    private val topTextTopOffset = 200
    private val bottomTextTopOffset = 2000

    protected suspend fun saveBitmapToFile(): Result<Boolean> {
        uiFlow.update { it.copy(isLoading = true) }
        val bitmap = uiFlow.value.accessKeyBitmap
            ?: return Result.failure(IllegalStateException("No access key?"))
        val destination =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

        return withContext(Dispatchers.IO) {
            runCatching {
                val result = bitmap.save(
                    destination = destination,
                    name = {
                        val date: DateFormat = SimpleDateFormat("yyy-MM-dd-h-mm", Locale.CANADA)
                        "Flipchat-Recovery-${date.format(Date())}.png"
                    }
                )
                if (result) {
                    mediaScanner.scan(destination)
                }
                result
            }
        }.onFailure {
            getAccessKeySaveError()
            uiFlow.update { it.copy(isLoading = false, isSuccess = false) }
        }.onSuccess {
            uiFlow.update { it.copy(isLoading = false, isSuccess = true) }
        }
    }

    private fun createBitmapForExport(
        drawBackground: Boolean = true,
        words: List<String>,
        entropyB64: String
    ): Bitmap {
        val accessKeyText = getAccessKeyText(words)

        val accessKeyBg = resources.getDrawable(R.drawable.ic_access_key_bg)
            ?.toBitmap(812, 1353)!!

        val imageLogo =
            resources.getDrawable(R.drawable.ic_flipcash_logo_access_key)
                ?.toBitmap(logoWidth.roundToInt(), logoHeight)!!

        val imageOut = Bitmap.createBitmap(
            targetWidth, targetHeight,
            Bitmap.Config.ARGB_8888
        ).applyCanvas {
            val accessBgActualWidth =
                accessKeyBg.getScaledWidth(resources.displayMetrics)

            if (drawBackground) {
                val paintBackground = Paint()
                paintBackground.color = Brand.toAGColor()
                paintBackground.style = Paint.Style.FILL
                drawPaint(paintBackground)
            }

            val topTextChunks = getString(R.string.subtitle_accessKeySnapshotWarning)
                .split(" ", "\n")
                .chunked(7)
                .map { it.joinToString(" ") }

            topTextChunks.forEachIndexed { index, text ->
                drawText(
                    canvas = this,
                    y = topTextTopOffset + (60 * (index + 1)),
                    sizePx = 40,
                    color = Alert.toAGColor(),
                    text = text
                )
            }


            drawBitmap(
                accessKeyBg,
                (((targetWidth - accessBgActualWidth) / 2)).toFloat(),
                bgTopOffset.toFloat(),
                null
            )

            drawBitmap(
                imageLogo,
                ((targetWidth - logoWidth) / 2).toFloat(),
                logoTopOffset.toFloat(),
                null
            )

            getQrCode(entropyB64)?.let { bitmap ->
                drawBitmap(
                    bitmap,
                    ((targetWidth - qrCodeSize) / 2).toFloat(),
                    qrTopOffset.toFloat(),
                    null
                )
            }

            drawText(
                canvas = this,
                y = keyTextTopOffset,
                sizePx = 32,
                color = White.toAGColor(),
                text = accessKeyText[0]
            )

            drawText(
                canvas = this,
                y = keyTextTopOffset + 40,
                sizePx = 32,
                color = White.toAGColor(),
                text = accessKeyText[1]
            )

            val bottomTextChunks = getString(R.string.subtitle_accessKeySnapshotDescription)
                .split(" ")
                .chunked(8)
                .map { it.joinToString(" ") }

            bottomTextChunks.forEachIndexed { index, text ->
                drawText(
                    canvas = this,
                    y = bottomTextTopOffset + (60 * (index + 1)),
                    sizePx = 40,
                    color = White.toAGColor(),
                    text = text
                )
            }

        }
        return imageOut
    }

    private fun getQrCode(entropyB64: String): Bitmap? {
        val base58 = Base58.encode(entropyB64.decodeBase64())
        val url = "${resources.getString(R.string.app_root_url)}/login?data=$base58"

        return qrCodeGenerator.generate(url, qrCodeSize)
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
        val xV: Int = x ?: ((targetWidth - bounds1.width()) / 2)
        canvas.drawText(text, xV.toFloat(), y.toFloat(), textPaint)
    }

    private fun getAccessKeySaveError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_failedToSave),
        resources.getString(R.string.error_description_failedToSave),
    )
}
