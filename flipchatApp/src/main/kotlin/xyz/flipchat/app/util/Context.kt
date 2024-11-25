package xyz.flipchat.app.util

import android.content.Context
import androidx.core.content.ContextCompat

fun Context.launchAppSettings() {
    val intent = IntentUtils.appSettings()
    ContextCompat.startActivity(this, intent, null)
}

fun Context.dialNumber(number: String) {
    val intent = IntentUtils.dialNumber(number)
    ContextCompat.startActivity(this, intent, null)
}