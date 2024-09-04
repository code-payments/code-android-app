package com.getcode.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.util.Biometrics
import com.getcode.util.BiometricsError
import com.getcode.util.BiometricsException

data class BiometricsState(
    val checking: Boolean = false,
    val passed: Boolean = false,
    val request: () -> Unit = { },
) {
    val isAwaitingAuthentication: Boolean
        get() = checking || !passed
}

@Composable
internal fun rememberBiometricsState(
    requireBiometrics: Boolean?,
    onError: (BiometricsError) -> Unit = { },
): BiometricsState {
    val context = LocalContext.current

    var biometricsPassed by remember(requireBiometrics) {
        mutableStateOf(requireBiometrics == false)
    }

    var checkBiometrics by remember(requireBiometrics) {
        mutableStateOf(requireBiometrics == true)
    }

    var stopped by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(checkBiometrics, requireBiometrics) {
        if (checkBiometrics && requireBiometrics == true) {
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
            request = {
                checkBiometrics = true
            }
        )
    }
}