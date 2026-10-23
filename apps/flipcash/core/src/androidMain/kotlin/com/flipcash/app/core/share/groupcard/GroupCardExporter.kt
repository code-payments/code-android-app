package com.flipcash.app.core.share.groupcard

import android.content.Context
import android.graphics.Bitmap
import com.flipcash.app.core.share.TipCodeExport
import com.flipcash.app.core.share.TipCodeExportFormat
import com.flipcash.app.core.share.TipCodeExportStorage
import com.flipcash.app.core.share.exportFileBaseName
import com.flipcash.libs.coroutines.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports a group's invite QR, styled as on the group card and without the card around it, as a
 * PNG or an SVG. Files go to [TipCodeExportStorage], which the share sheet reads.
 */
@Singleton
class GroupCardExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    /** Null when the write fails. [baseName] names the file; the extension is added. */
    suspend fun export(
        matrix: QrMatrix,
        format: TipCodeExportFormat,
        baseName: String?,
    ): TipCodeExport? = withContext(dispatchers.IO) {
        runCatching {
            // Exports are re-creatable and nothing else clears this folder, so each one tidies up
            // after the last. The chooser has long finished with those.
            TipCodeExportStorage.prune(context)
            val file = TipCodeExportStorage.file(
                context = context,
                signature = "group-card",
                format = format,
                baseName = baseName,
            )
            when (format) {
                TipCodeExportFormat.Png -> {
                    val bitmap = StyledQrBitmap.render(matrix)
                    try {
                        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally {
                        bitmap.recycle()
                    }
                }
                TipCodeExportFormat.Svg -> file.writeText(StyledQrGeometry.toSvg(matrix))
            }
            TipCodeExport(uri = TipCodeExportStorage.uriFor(context, file), format = format)
        }.getOrNull()
    }
}

/**
 * The export's file name before its extension: `Join {title}`, or the fallback when the title is
 * empty or sanitises to nothing. `/` and `:` are dropped (the sanitiser also drops the other
 * characters a file system rejects).
 */
fun groupCardBaseName(title: String?, joinFormat: (String) -> String, fallback: String): String {
    val safe = exportFileBaseName(title) ?: return fallback
    return joinFormat(safe)
}
