package com.flipcash.app.core.storage

import android.content.Context
import android.graphics.Bitmap
import com.flipcash.app.core.internal.extensions.save
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun saveBitmap(bitmap: Bitmap, filename: String): Boolean {
        return bitmap.save(context.contentResolver) { filename }
    }
}
