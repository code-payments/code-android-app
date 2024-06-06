package com.getcode.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.getcode.R

fun Context.launchAppSettings() {
    val intent = IntentUtils.appSettings()
    ContextCompat.startActivity(this, intent, null)
}

fun Context.launchSmsIntent(phoneValue: String, message: String) {
    val intent = IntentUtils.sendSms(phoneValue, message)
    ContextCompat.startActivity(this, intent, null)
}

fun Context.shareDownloadLink() {
    val url = getString(R.string.app_download_link)
    val intent = IntentUtils.share(url)
    ContextCompat.startActivity(this, intent, null)
}
