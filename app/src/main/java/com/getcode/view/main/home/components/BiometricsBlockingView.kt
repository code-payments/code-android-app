package com.getcode.view.main.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.util.Biometrics
import com.getcode.util.BiometricsError
import com.getcode.util.BiometricsException
import timber.log.Timber

@Composable
internal fun BiometricsBlockingView(
    modifier: Modifier = Modifier,
    state: BiometricsState,
) {
    AnimatedVisibility(
        visible = !state.passed && !state.checking,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = modifier,
            color = Brand.copy(alpha = 0.87f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CodeButton(
                    onClick = { state.request() },
                    text = stringResource(id = R.string.action_unlockCode),
                    contentPadding = PaddingValues(),
                    shape = CircleShape,
                    buttonState = ButtonState.Filled
                )
            }
        }
    }
}

data class BiometricsState(
    val checking: Boolean = false,
    val passed: Boolean = false,
    val request: () -> Unit = { },
) {
    val isAwaitingAuthentication = checking || !passed
}

@Composable
internal fun rememberBiometricsState(
    requireBiometrics: Boolean?,
    onBiometricsNotEnrolled: () -> Unit,
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
                        if (exception.error == BiometricsError.NoBiometrics) {
                            Timber.e("missing biometrics")
                            onBiometricsNotEnrolled()
                        }
                    }
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