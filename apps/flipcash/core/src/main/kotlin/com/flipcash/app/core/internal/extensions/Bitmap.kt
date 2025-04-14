package com.flipcash.app.core.internal.extensions

import android.graphics.Bitmap
import com.getcode.utils.ErrorUtils
import com.getcode.utils.timedTrace
import java.io.File
import java.io.FileOutputStream

fun Bitmap.save(destination: File, name: () -> String): Boolean {
    val filename = name()
    if (!destination.exists()) {
        if (!destination.mkdirs()) {
            return false
        }
    }
    val dest = File(destination, filename)

    return timedTrace("saving bitmap") {
        try {
            FileOutputStream(dest).use { out ->
                compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            return@timedTrace false
        }
        return@timedTrace true
    }
}