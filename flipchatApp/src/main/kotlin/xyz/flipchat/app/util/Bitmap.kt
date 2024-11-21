package xyz.flipchat.app.util

import android.graphics.Bitmap
import com.getcode.utils.ErrorUtils
import com.getcode.utils.timedTrace
import java.io.File
import java.io.FileOutputStream

internal fun Bitmap.save(destination: File, name: () -> String): Boolean {
    val filename = name()
    if (!destination.exists()) {
        if (!destination.mkdirs()) {
            return false
        }
    }
    val dest = File(destination, filename)

    return timedTrace("saving bitmap") {
        try {
            FileOutputStream(dest).buffered().use { out ->
                compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            return@timedTrace false
        }
        return@timedTrace true
    }
}