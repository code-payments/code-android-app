package com.getcode.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationError
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.getcode.R
import com.getcode.network.repository.TransactionRepository.ErrorSubmitIntent
import timber.log.Timber
import java.util.concurrent.Executors

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
