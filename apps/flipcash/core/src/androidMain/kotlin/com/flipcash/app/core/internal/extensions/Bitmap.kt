package com.flipcash.app.core.internal.extensions

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import com.getcode.utils.ErrorUtils
import com.getcode.utils.timedTrace

fun Bitmap.save(contentResolver: ContentResolver, name: () -> String): Boolean {
    val filename = name()

    return timedTrace("saving bitmap") {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@timedTrace false

            contentResolver.openOutputStream(uri)?.use { out ->
                compress(Bitmap.CompressFormat.PNG, 90, out)
            } ?: run {
                contentResolver.delete(uri, null, null)
                return@timedTrace false
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)

            return@timedTrace true
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            return@timedTrace false
        }
    }
}