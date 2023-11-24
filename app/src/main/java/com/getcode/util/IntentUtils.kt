package com.getcode.util

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.getcode.App
import com.getcode.BuildConfig
import com.getcode.utils.PhoneUtils

object IntentUtils {
    fun launchAppSettings() {
        ContextCompat.startActivity(
            App.getInstance(),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            null
        )
    }

    fun launchSmsIntent(phoneValue: String, message: String) {
        val uri: Uri = Uri.parse("smsto:${PhoneUtils.makeE164(phoneValue)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.putExtra(
            "sms_body",
            message
        )
        ContextCompat.startActivity(App.getInstance(), intent, null)
    }
}