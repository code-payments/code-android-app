package com.getcode.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import androidx.core.content.ContextCompat
import com.getcode.R
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.floor


fun Context.launchAppSettings() {
    val intent = IntentUtils.appSettings()
    ContextCompat.startActivity(this, intent, null)
}

fun Context.launchSmsIntent(phoneValue: String, message: String) {
    val intent = IntentUtils.sendSms(phoneValue, message)
    ContextCompat.startActivity(this, intent, null)
}

fun Context.shareDownloadLink() {
    val shareRef = getString(R.string.app_download_link_share_ref)
    val url = getString(R.string.app_download_link_with_ref, shareRef)
    val intent = IntentUtils.share(url)
    ContextCompat.startActivity(this, intent, null)
}

fun Context.uriToBitmap(uri: Uri): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = true
        }
    } else {
        MediaStore.Images.Media.getBitmap(contentResolver, uri)
    }
}

private fun resizeBitmap(source: Bitmap, maxLength: Int): Bitmap {
    try {
        if (source.height >= source.width) {
            if (source.height <= maxLength) {
                return source
            }

            val aspectRatio = source.width.toDouble() / source.height.toDouble()
            val targetWidth = (maxLength * aspectRatio).toInt()
            return Bitmap.createScaledBitmap(source, targetWidth, maxLength, false)
        } else {
            if (source.width <= maxLength) {
                return source
            }

            val aspectRatio = source.height.toDouble() / source.width.toDouble()
            val targetHeight = (maxLength * aspectRatio).toInt()
            return Bitmap.createScaledBitmap(source, maxLength, targetHeight, false)
        }
    } catch (e: Exception) {
        return source
    }
}


@Throws(FileNotFoundException::class, IOException::class)
fun Context.getThumbnail(uri: Uri): Bitmap? {
    val onlyBoundsOptions = BitmapFactory.Options()
    onlyBoundsOptions.inJustDecodeBounds = true
    onlyBoundsOptions.inDither = true //optional
    onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 //optional
    getContentResolver().openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, onlyBoundsOptions)
    }

    if ((onlyBoundsOptions.outWidth == -1) || (onlyBoundsOptions.outHeight == -1)) {
        return null
    }

    val bitmapOptions = BitmapFactory.Options()
    bitmapOptions.inDither = true //optional
    bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 //
    return getContentResolver().openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, bitmapOptions)
    }
}