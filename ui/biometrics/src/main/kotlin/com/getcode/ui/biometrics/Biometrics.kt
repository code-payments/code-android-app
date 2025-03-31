package com.getcode.ui.biometrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import com.getcode.libs.biometrics.Biometrics
import com.getcode.libs.biometrics.BiometricsError
import com.getcode.libs.biometrics.BiometricsException
import com.getcode.ui.components.OnLifecycleEvent

data class BiometricsState(
    val checking: Boolean = false,
    val passed: Boolean = false,
    val isSupported: Boolean = true,
    val hasEnrolled: Boolean = false,
    val request: () -> Unit = { },
) {
    val isAwaitingAuthentication: Boolean
        get() = (checking || !passed) && isSupported && hasEnrolled
}

@Composable
fun rememberBiometricsState(
    requireBiometrics: Boolean?,
    onError: (BiometricsError) -> Unit = { },
): BiometricsState {
    val context = LocalContext.current

    // Check if biometric authentication is available on the device
    val canAuthenticate = remember(context) {
        Biometrics.canAuthenticate(context)
    }

    val hasAnyEnrolled = remember(context) {
        !Biometrics.hasNoneEnrolled(context)
    }

    var biometricsPassed by remember(requireBiometrics, canAuthenticate) {
        mutableStateOf(requireBiometrics == false)
    }

    var checkBiometrics by remember(requireBiometrics, canAuthenticate) {
        mutableStateOf(requireBiometrics == true)
    }

    var stopped by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(checkBiometrics, requireBiometrics, canAuthenticate) {
        println("canAuth=$canAuthenticate")
        if (checkBiometrics && requireBiometrics == true && canAuthenticate) {
            Biometrics.prompt(context)
                .onFailure {
                    val error = it as? BiometricsException
                    error?.let { exception ->
                        onError(exception.error)
                    }
                    biometricsPassed = false
                    checkBiometrics = false
                }
                .onSuccess {
                    biometricsPassed = true
                    checkBiometrics = false
                }
        } else if (!canAuthenticate && requireBiometrics == false) {
            // If biometrics aren't supported and they're not required, consider it passed
            biometricsPassed = true
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
                if (requireBiometrics == true && stopped) {
                    checkBiometrics = true
                }
            }

            else -> Unit
        }
    }

    return remember(requireBiometrics, biometricsPassed) {
        BiometricsState(
            checking = requireBiometrics == null,
            passed = biometricsPassed,
            isSupported = canAuthenticate,
            hasEnrolled = hasAnyEnrolled,
            request = {
                if (canAuthenticate) {
                    checkBiometrics = true
                }
            }
        )
    }
}