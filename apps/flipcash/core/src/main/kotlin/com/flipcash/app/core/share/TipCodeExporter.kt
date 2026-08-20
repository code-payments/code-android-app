package com.flipcash.app.core.share

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import com.flipcash.core.R
import com.flipcash.app.core.bill.Scannable
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.codes.kikcode.KikCodeSvg
import com.getcode.codes.kikcode.kikCodeBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports a tip card's scannable code as a PNG or an SVG.
 *
 * Code-only, matching what the share preview already shows: no display name, no avatar, no card
 * chrome. That keeps the SVG honest (nothing to embed a font for) and makes both formats render the
 * same thing.
 *
 * Both formats come off the *same* shared geometry (`:libs:codes:kikcode`), which is also what the
 * on-screen `KikCodeContentView` and iOS draw — so an exported file and the code the user is looking
 * at cannot disagree. Rasterisation stays native (Android [Bitmap] here, `CGContext` on iOS); only
 * the maths is shared.
 */
@Singleton
class TipCodeExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * Writes [card]'s code in [format] and returns a `content://` handle to it, or `null` if the
     * write failed — callers should degrade (share the URL alone) rather than surface an error.
     *
     * [sizePx] applies to [TipCodeExportFormat.Png] only; the SVG carries a `viewBox` and scales.
     */
    suspend fun export(
        card: Scannable.TipCard,
        format: TipCodeExportFormat,
        sizePx: Int = DEFAULT_PNG_SIZE_PX,
    ): TipCodeExport? = withContext(dispatchers.IO) {
        val payload = card.data.toByteArray()
        if (payload.isEmpty()) return@withContext null

        runCatching {
            val file = TipCodeExportStorage.file(
                context = context,
                signature = tipCodePreviewSignature(card),
                format = format,
            )
            when (format) {
                TipCodeExportFormat.Png -> writePng(payload, sizePx, file)
                TipCodeExportFormat.Svg -> writeSvg(payload, file)
            }
            TipCodeExport(uri = TipCodeExportStorage.uriFor(context, file), format = format)
        }.getOrNull()
    }

    private fun writePng(payload: ByteArray, sizePx: Int, file: File) {
        val badge = ContextCompat.getDrawable(context, R.drawable.ic_logo_round_white)
        val bitmap = kikCodeBitmap(payload = payload, size = sizePx, badge = badge)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeSvg(payload: ByteArray, file: File) {
        file.writeText(KikCodeSvg.render(payload))
    }

    private companion object {
        /**
         * Large enough that the code stays crisp when a share target scales it up, small enough to
         * stay well under a megabyte -- the graphic is flat white on transparent, so PNG compresses
         * it hard.
         */
        const val DEFAULT_PNG_SIZE_PX = 1024

        // PNG is lossless; the parameter only controls compression effort.
        const val PNG_QUALITY = 100
    }
}
