package com.kik.kikx.kikcodes.implementation

import android.util.Base64
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.kikcodes.ScanQuality
import com.kik.kikx.models.GroupInviteCode
import com.kik.kikx.models.ScannableKikCode
import com.kik.scan.GroupKikCode
import com.kik.scan.KikCode
import com.kik.scan.RemoteKikCode
import com.kik.scan.Scanner
import com.kik.scan.UsernameKikCode

class KikCodeScannerImpl : KikCodeScanner {

    private fun KikCode.toModelKikCode(): ScannableKikCode {
        return when (this) {
            is GroupKikCode -> {
                val inviteCodeAsBase64 = Base64.encodeToString(
                    inviteCode,
                    Base64.URL_SAFE or Base64.NO_PADDING
                ).trim() // for some reason adds a line break at the end
                ScannableKikCode.GroupKikCode(GroupInviteCode(GroupInviteCode.Id(inviteCodeAsBase64.toByteArray())), colour)
            }
            is UsernameKikCode -> ScannableKikCode.UsernameKikCode(username, nonce, colour)
            is RemoteKikCode -> ScannableKikCode.RemoteKikCode(payloadId, colour)
            else -> throw KikCodeScanner.UnsupportedKikCodeFoundException(this)
        }
    }

    override suspend fun scanKikCode(imageData: ByteArray, width: Int, height: Int, quality: ScanQuality): Result<ScannableKikCode> {
        val source = PlanarYUVLuminanceSource(imageData, width, height)

        try {
            val scanResult = Scanner.scan(source.matrix, width, height, quality.headerValue)
                ?: return Result.failure(KikCodeScanner.NoKikCodeFoundException())

            val kikCode = KikCode.parse(scanResult.data)
                ?: return Result.failure(KikCodeScanner.FailedToParseCodeException(scanResult))

            val scannable = kikCode.toModelKikCode() // will throw UnsupportedKikCodeFoundException

            return Result.success(scannable)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
