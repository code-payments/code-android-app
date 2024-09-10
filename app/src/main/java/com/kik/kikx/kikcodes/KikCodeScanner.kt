package com.kik.kikx.kikcodes

import com.kik.kikx.models.ScannableKikCode
import com.kik.scan.KikCode
import com.kik.scan.Scanner.ScanResult

sealed class ScanQuality(val headerValue: Int) {
    data object Low : ScanQuality(0)
    data object Medium : ScanQuality(3)
    data object High : ScanQuality(8)
    data object Best : ScanQuality(10)

    companion object {
        private val values = listOf(Best, High, Medium, Low)

        fun iterator(): Iterator<ScanQuality> {
            return values.iterator()
        }
    }
}

open class ScannerError(override val message: String) : Exception(message)

interface KikCodeScanner {
    class NoKikCodeFoundException : ScannerError("No Kik Code found in image buffer")
    class FailedToParseCodeException(val scanResult: ScanResult) :
        ScannerError("Code found in image buffer, but failed to parse")

    class UnsupportedKikCodeFoundException(val kikCode: KikCode) : ScannerError("Code found in unsupported")

    suspend fun scanKikCode(
        imageData: ByteArray, width: Int, height: Int, quality: ScanQuality = ScanQuality.Medium
    ): Result<ScannableKikCode>
}
