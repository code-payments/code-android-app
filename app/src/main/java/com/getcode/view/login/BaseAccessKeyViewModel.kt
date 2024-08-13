package com.getcode.view.login

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.MnemonicManager
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.media.MediaScanner
import com.getcode.network.repository.TransactionRepository
import com.getcode.network.repository.DeniedReason
import com.getcode.network.repository.ErrorSubmitIntent
import com.getcode.network.repository.ErrorSubmitIntentException
import com.getcode.network.repository.decodeBase64
import com.getcode.theme.Alert
import com.getcode.theme.Brand
import com.getcode.theme.White
import com.getcode.ui.utils.toAGColor
import com.getcode.util.generateQrCode
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils
import com.getcode.vendor.Base58
import com.getcode.view.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
) : BaseViewModel(resources) {
    val uiFlow = MutableStateFlow(AccessKeyUiModel())

    fun init() {
        viewModelScope.launch {
            SessionManager.authState
                .distinctUntilChangedBy { it.entropyB64 }
                .collect { it.entropyB64?.let { e -> initWithEntropy(e) } }
        }
    }

    fun initWithEntropy(entropyB64: String) {
        if (uiFlow.value.entropyB64 == entropyB64) return
        Timber.d("entropy=$entropyB64")
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

    private val logoWidth = 270
    private val logoHeight = 88
    private val qrCodeSize = 480

    private val bgTopOffset = 550
    private val logoTopOffset = 770
    private val qrTopOffset = 980
    private val keyTextTopOffset = 1600
    private val topTextTopOffset = 200
    private val bottomTextTopOffset = 2000


    internal fun saveBitmapToFile(): Boolean {
        val bitmap = uiFlow.value.accessKeyBitmap ?: return false
        val date: DateFormat = SimpleDateFormat("yyy-MM-dd-h-mm", Locale.CANADA)

        val filename = "Code-Recovery-${date.format(Date())}.png"
        val sd: File =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dest = File(sd, filename)

        try {
            val out = FileOutputStream(dest)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            return false
        }
        mediaScanner.scan(sd)
        return true
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
            resources.getDrawable(R.drawable.ic_code_logo_white)
                ?.toBitmap(logoWidth, logoHeight)!!

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
        val url = "${resources.getString(R.string.root_url_app)}/login?data=$base58"

        return generateQrCode(url, qrCodeSize)
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

    private fun getSomethingWentWrongError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_failedToCreateAccount),
        resources.getString(R.string.error_description_failedToCreateAccount),
    )

    private fun getTooManyAccountsPerPhoneError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_tooManyAccountsPerPhone),
        resources.getString(R.string.error_description_tooManyAccountsPerPhone)
    )

    private fun getTooManyAccountsPerDeviceError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_tooManyAccountsPerDevice),
        resources.getString(R.string.error_description_tooManyAccountsPerDevice)
    )

    private fun getUnsupportedCountryError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_countryNotSupported),
        resources.getString(R.string.error_description_countryNotSupported)
    )

    private fun getUnsupportedDeviceError() = TopBarManager.TopBarMessage(
        resources.getString(R.string.error_title_deviceNotSupported),
        resources.getString(R.string.error_description_deviceNotSupported)
    )

    internal fun onSubmitError(e: Throwable) {
        when (e) {
            is ErrorSubmitIntentException -> {
                when (val intent = e.errorSubmitIntent) {
                    is ErrorSubmitIntent.Denied -> {
                        if (intent.reasons.isEmpty() || intent.reasons.first() == DeniedReason.Unspecified) {
                            getSomethingWentWrongError()
                        } else {
                            val reason = intent.reasons.first()
                            when (reason) {
                                DeniedReason.Unspecified -> getSomethingWentWrongError()
                                DeniedReason.TooManyFreeAccountsForPhoneNumber -> getTooManyAccountsPerPhoneError()
                                DeniedReason.TooManyFreeAccountsForDevice -> getTooManyAccountsPerDeviceError()
                                DeniedReason.UnsupportedCountry -> getUnsupportedCountryError()
                                DeniedReason.UnsupportedDevice -> getUnsupportedDeviceError()
                            }
                        }
                    }
                    else -> getSomethingWentWrongError()
                }
            }
            is IllegalStateException -> getAccessKeySaveError()
            else -> getSomethingWentWrongError()
        }.let { m -> TopBarManager.showMessage(m) }
    }
}
