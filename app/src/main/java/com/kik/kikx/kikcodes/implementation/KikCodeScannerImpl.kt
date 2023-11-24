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
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
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

    override fun scanKikCode(imageData: ByteArray, width: Int, height: Int): Single<ScannableKikCode> {
        return Single.fromCallable {
            val source = PlanarYUVLuminanceSource(imageData, width, height, 0, 0, width, height, false)

            val yuv = YuvImage(imageData, ImageFormat.NV21, width, height, null)

            val bos = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, yuv.width, yuv.height), 100, bos)

            val scanResult = Scanner.scan(source.matrix, width, height, SCAN_QUALITY)
                ?: throw KikCodeScanner.NoKikCodeFoundException()

            KikCode.parse(scanResult.data)?.toModelKikCode()
                ?: throw KikCodeScanner.NoKikCodeFoundException()
        }.subscribeOn(Schedulers.computation())
    }
}
