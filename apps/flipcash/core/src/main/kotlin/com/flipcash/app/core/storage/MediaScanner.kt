package com.flipcash.app.core.storage

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scan(directory: File) {
        MediaScannerConnection.scanFile(context, arrayOf(directory.toString()), null, null)
    }
}