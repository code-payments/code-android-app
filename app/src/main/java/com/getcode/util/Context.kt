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

fun Context.biometricPrompt(
    onSuccess: () -> Unit,
    onError: (BiometricsException) -> Unit,
) {
    println("biometrics prompt")
    val activity = this as FragmentActivity
    val executor = Executors.newSingleThreadExecutor()
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Timber.d("Biometric Authentication successful")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val error = BiometricsException(errorCode, errString)
                Timber.e(t = error.cause, message = "onAuthenticationErrorForBiometrics")
                onError(error)
            }

            override fun onAuthenticationFailed() {
                Timber.e("onAuthenticationFailedForBiometrics")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.title_biometricAuthentication))
        .setDescription(getString(R.string.description_biometricAuthentication))
        .setNegativeButtonText(getString(R.string.action_cancel))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

    biometricPrompt.authenticate(promptInfo)
}


class BiometricsException(
    val error: BiometricsError,
    private val messageString: CharSequence = ""
) : Exception(Throwable("$messageString (${error.ordinal})")) {
    override val message: String
        get() = "${error.javaClass.simpleName} $messageString"

    constructor(code: Int, message: CharSequence): this(BiometricsError.fromValue(code), message)
}

enum class BiometricsError {
    Unknown, // BIOMETRICS_SUCCESS is 0 so using this
    HardwareUnavailable,
    UnableToProcess,
    Timeout,
    NoSpace,
    Cancelled,
    Lockout,
    Vendor,
    LockoutPermanent,
    UserCancelled,
    NoBiometrics,
    HardwareNotPresent,
    NegativeButton,
    NoDeviceCredential;
    
    companion object {
        fun fromValue(code: Int): BiometricsError  {
            return entries.toList().getOrNull(code - 1) ?: Unknown
        }
    }
}
