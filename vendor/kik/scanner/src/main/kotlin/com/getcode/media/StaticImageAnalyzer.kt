package com.getcode.media

import android.net.Uri
import com.kik.kikx.models.ScannableKikCode

interface StaticImageAnalyzer {
    suspend fun analyze(uri: Uri): Result<ScannableKikCode>
}