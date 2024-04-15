package com.kik.kikx.kikcodes

import com.kik.kikx.models.ScannableKikCode
import io.reactivex.rxjava3.core.Single

interface KikCodeScanner {
    class NoKikCodeFoundException : Exception("No Kik Code found in image buffer")

    suspend fun scanKikCode(imageData: ByteArray, width: Int, height: Int): Result<ScannableKikCode>
}
