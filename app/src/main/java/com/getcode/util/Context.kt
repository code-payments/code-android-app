package com.getcode.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.getcode.BuildConfig
import com.getcode.utils.makeE164

fun Context.launchAppSettings() {
    ContextCompat.startActivity(
        this,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        },
        null
    )
}

fun Context.launchSmsIntent(phoneValue: String, message: String) {
    val uri: Uri = Uri.parse("smsto:${phoneValue.makeE164()}")
    val intent = Intent(Intent.ACTION_SENDTO, uri)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    intent.putExtra(
        "sms_body",
        message
    )
    ContextCompat.startActivity(this, intent, null)
}