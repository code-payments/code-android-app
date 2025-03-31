package com.getcode.libs.biometrics

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.getcode.libs.biometrics.Biometrics.TEST_AUTH
import kotlinx.coroutines.delay
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

    constructor(code: Int, message: CharSequence) : this(BiometricsError.fromValue(code), message)
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
        fun fromValue(code: Int): BiometricsError {
            return entries.toList().getOrNull(code - 1) ?: Unknown
        }
    }
}

object Biometrics {
    var promptActive by mutableStateOf(false)
        private set

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    const val TEST_AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun canAuthenticate(context: Context): Boolean {
        val state = BiometricManager.from(context).canAuthenticate(TEST_AUTH)

        return !(state == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                state == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                state == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ||
                state == BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED)
    }

    fun hasNoneEnrolled(context: Context): Boolean {
        val state = BiometricManager.from(context).canAuthenticate(TEST_AUTH)

        return state == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    suspend fun prompt(
        context: Context,
        delay: Long = 0L,
        authenticators: Int = AUTHENTICATORS,
    ): Result<Unit> {
        promptActive = true
        delay(delay)
        return suspendCancellableCoroutine { cont ->
            val activity = context as FragmentActivity
            val executor = Executors.newSingleThreadExecutor()
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        Timber.d("Biometric Authentication successful")
                        promptActive = false
                        cont.resume(Result.success(Unit))
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val error = BiometricsException(errorCode, errString)
                        Timber.e(t = error.cause, message = "onAuthenticationErrorForBiometrics")
                        promptActive = false
                        cont.resume(Result.failure(error))
                    }

                    override fun onAuthenticationFailed() {
                        Timber.e("onAuthenticationFailedForBiometrics")
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.title_biometricAuthentication))
                .setDescription(context.getString(R.string.description_biometricAuthentication))
                .setAllowedAuthenticators(authenticators)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }
}