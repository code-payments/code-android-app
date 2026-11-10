package com.getcode.libs.biometrics

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class BiometricsException(
    val error: BiometricsError,
    val messageString: CharSequence = ""
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

    /**
     * True when the device has no way left to authenticate the user, so prompting again can never
     * succeed. A lockout is not one of these: the device credential clears it.
     */
    val isUnrecoverable: Boolean
        get() = this == NoBiometrics || this == HardwareNotPresent || this == NoDeviceCredential

    /** True when the user dismissed the prompt, which needs no message. */
    val isDismissal: Boolean
        get() = this == Cancelled || this == UserCancelled || this == NegativeButton

    companion object {
        // Mapped by name: the prompt's codes skip 6, so indexing into entries misread codes 1-5
        // (ERROR_CANCELED came back as NoSpace).
        fun fromValue(code: Int): BiometricsError = when (code) {
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> HardwareUnavailable
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> UnableToProcess
            BiometricPrompt.ERROR_TIMEOUT -> Timeout
            BiometricPrompt.ERROR_NO_SPACE -> NoSpace
            BiometricPrompt.ERROR_CANCELED -> Cancelled
            BiometricPrompt.ERROR_LOCKOUT -> Lockout
            BiometricPrompt.ERROR_VENDOR -> Vendor
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> LockoutPermanent
            BiometricPrompt.ERROR_USER_CANCELED -> UserCancelled
            BiometricPrompt.ERROR_NO_BIOMETRICS -> NoBiometrics
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> HardwareNotPresent
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> NegativeButton
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> NoDeviceCredential
            else -> Unknown
        }
    }
}

object Biometrics {
    var promptActive by mutableStateOf(false)
        private set

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private const val TEST_AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun authenticationState(manager: BiometricManager): Int = manager.canAuthenticate(TEST_AUTH)

    fun canAuthenticate(context: Context): Boolean {
        val state = authenticationState(BiometricManager.from(context))

        return !(state == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                state == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                state == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ||
                state == BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED)
    }

    /**
     * Whether [prompt] can authenticate the user with the authenticators it allows: a biometric or
     * the device credential. [canAuthenticate] only tests biometrics, so it is false on a device whose
     * sensor is unavailable but whose PIN still works.
     */
    fun canPrompt(context: Context): Boolean =
        canPromptWith(BiometricManager.from(context).canAuthenticate(AUTHENTICATORS))

    /**
     * Maps a `canAuthenticate(AUTHENTICATORS)` status to whether a prompt can succeed. Only states
     * that leave no authenticator at all return false. A transient one, such as an unavailable sensor
     * or an unknown status, still prompts, since the device credential can carry it.
     */
    fun canPromptWith(status: Int): Boolean = when (status) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false
        else -> true
    }

    fun hasNoneEnrolled(context: Context): Boolean {
        val state = authenticationState(BiometricManager.from(context))

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
            val activity = context.findFragmentActivity()
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

private fun Context.findFragmentActivity(): FragmentActivity {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("Biometric prompt requires a FragmentActivity context")
}