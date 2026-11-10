package com.getcode.ui.biometrics

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import com.getcode.libs.biometrics.Biometrics
import com.getcode.libs.biometrics.BiometricsError
import com.getcode.libs.biometrics.BiometricsException
import com.getcode.ui.components.OnLifecycleEvent

private const val BIOMETRICS_COOLDOWN_MS = 90_000L

data class BiometricsState(
    val checking: Boolean = false,
    val passed: Boolean = false,
    /** Whether the device can show the prompt, with a biometric or the device credential. */
    val isSupported: Boolean = true,
    val hasEnrolled: Boolean = false,
    val request: () -> Unit = { },
) {
    val isAwaitingAuthentication: Boolean
        get() = isSupported && (checking || !passed)

    /**
     * Whether the app is locked behind the unlock overlay. Unlike [isAwaitingAuthentication] this is
     * false while the setting is still loading, so the overlay doesn't flash on every cold start.
     */
    val isLocked: Boolean
        get() = !checking && !passed
}

/**
 * @param onUnavailable called when biometrics are required but the device has no authenticator left
 * to satisfy them. The state lets the user in; the caller should turn the requirement off.
 * @param onError called when a prompt fails for a reason the user can act on, such as a lockout. The
 * user stays locked and can retry from the overlay.
 */
@Composable
fun rememberBiometricsState(
    requireBiometrics: Boolean?,
    onUnavailable: () -> Unit = { },
    onError: (BiometricsException) -> Unit = { },
): BiometricsState {
    val context = LocalContext.current
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val currentOnError by rememberUpdatedState(onError)

    // Re-read on resume: the user can enrol or remove a fingerprint, or set a screen lock, while the
    // app is in the background.
    var canPrompt by remember(context) { mutableStateOf(Biometrics.canPrompt(context)) }
    var hasAnyEnrolled by remember(context) { mutableStateOf(!Biometrics.hasNoneEnrolled(context)) }

    var biometricsPassed by remember(requireBiometrics) {
        mutableStateOf(requireBiometrics == false)
    }

    // Each request bumps this key, so a tap always starts a fresh prompt, even when an earlier one
    // never called back.
    var promptRequests by remember(requireBiometrics) { mutableIntStateOf(0) }

    var stopped by remember { mutableStateOf(false) }
    var lastAuthTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(promptRequests, requireBiometrics, canPrompt) {
        if (requireBiometrics != true || biometricsPassed) return@LaunchedEffect

        if (!canPrompt) {
            biometricsPassed = true
            currentOnUnavailable()
            return@LaunchedEffect
        }

        Biometrics.prompt(context)
            .onFailure {
                val exception = it as? BiometricsException
                    ?: BiometricsException(BiometricsError.Unknown, it.message.orEmpty())
                if (exception.error.isUnrecoverable) {
                    biometricsPassed = true
                    currentOnUnavailable()
                } else {
                    biometricsPassed = false
                    currentOnError(exception)
                }
            }
            .onSuccess {
                biometricsPassed = true
                lastAuthTimestamp = SystemClock.elapsedRealtime()
            }
    }

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if (requireBiometrics == true) {
                    biometricsPassed = false
                }
                stopped = true
            }

            Lifecycle.Event.ON_RESUME -> {
                canPrompt = Biometrics.canPrompt(context)
                hasAnyEnrolled = !Biometrics.hasNoneEnrolled(context)
                if (requireBiometrics == true && stopped) {
                    val elapsed = SystemClock.elapsedRealtime() - lastAuthTimestamp
                    if (elapsed <= BIOMETRICS_COOLDOWN_MS) {
                        biometricsPassed = true
                    } else if (!Biometrics.promptActive) {
                        // The device-credential fallback can stop the activity mid-prompt; leave
                        // that prompt running so its result still lands. A tap on the overlay
                        // always starts a fresh one.
                        promptRequests++
                    }
                }
            }

            else -> Unit
        }
    }

    return remember(requireBiometrics, biometricsPassed, canPrompt, hasAnyEnrolled) {
        BiometricsState(
            checking = requireBiometrics == null,
            passed = biometricsPassed,
            isSupported = canPrompt,
            hasEnrolled = hasAnyEnrolled,
            request = { promptRequests++ },
        )
    }
}

val LocalBiometricsState: ProvidableCompositionLocal<BiometricsState> = staticCompositionLocalOf { BiometricsState() }
