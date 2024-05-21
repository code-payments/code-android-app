package com.getcode.util

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.getcode.BuildConfig
import com.getcode.network.repository.urlEncode
import com.getcode.utils.makeE164

object IntentUtils {

    fun appSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun sendSms(phoneValue: String, message: String) = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("smsto:${phoneValue.makeE164()}")
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        putExtra("sms_body", message)
    }

    fun tweet(message: String) = Intent(Intent.ACTION_VIEW).apply {
        val url = "https://www.twitter.com/intent/tweet?text=${message.urlEncode()}"
        setData(Uri.parse(url))
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}