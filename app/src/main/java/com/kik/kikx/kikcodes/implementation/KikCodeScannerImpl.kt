package com.kik.kikx.kikcodes.implementation

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.models.GroupInviteCode
import com.kik.kikx.models.ScannableKikCode
import com.kik.scan.GroupKikCode
import com.kik.scan.KikCode
import com.kik.scan.RemoteKikCode
import com.kik.scan.Scanner
import com.kik.scan.UsernameKikCode
import java.io.ByteArrayOutputStream

class KikCodeScannerImpl : KikCodeScanner {

    companion object {
        private const val SCAN_QUALITY = 3
    }

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
            else -> throw Exception("Unsupported Kik code type")
        }
    }

    override suspend fun scanKikCode(imageData: ByteArray, width: Int, height: Int): Result<ScannableKikCode> {
        val source = PlanarYUVLuminanceSource(imageData, width, height, 0, 0, width, height, false)

        return try {
            val scanResult = Scanner.scan(source.matrix, width, height, SCAN_QUALITY) ?: throw KikCodeScanner.NoKikCodeFoundException()
            runCatching { KikCode.parse(scanResult.data)?.toModelKikCode() ?: throw KikCodeScanner.NoKikCodeFoundException() }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
