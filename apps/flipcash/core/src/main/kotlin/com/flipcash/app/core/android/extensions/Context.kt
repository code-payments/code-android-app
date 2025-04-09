package com.flipcash.app.core.android.extensions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore.Images.Media
import androidx.core.content.ContextCompat
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.core.R


fun Context.launchAppSettings() {
    val intent = IntentUtils.appSettings(this)
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
        Media.getBitmap(contentResolver, uri)
    }
}