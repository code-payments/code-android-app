package com.getcode.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.getcode.R
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.coroutines.resume

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

object Biometrics {
    suspend fun prompt(context: Context): Result<Unit> = suspendCancellableCoroutine { cont ->
        val activity = context as FragmentActivity
        val executor = Executors.newSingleThreadExecutor()
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Timber.d("Biometric Authentication successful")
                    cont.resume(Result.success(Unit))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val error = BiometricsException(errorCode, errString)
                    Timber.e(t = error.cause, message = "onAuthenticationErrorForBiometrics")
                    cont.resume(Result.failure(error))
                }

                override fun onAuthenticationFailed() {
                    Timber.e("onAuthenticationFailedForBiometrics")
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.title_biometricAuthentication))
            .setDescription(context.getString(R.string.description_biometricAuthentication))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
                        or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}