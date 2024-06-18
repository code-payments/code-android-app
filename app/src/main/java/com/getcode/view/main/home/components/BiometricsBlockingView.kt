package com.getcode.view.main.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.getcode.R
import com.getcode.theme.BrandOverlay
import com.getcode.theme.CodeTheme
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
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(vertical = CodeTheme.dimens.grid.x3)
                    .padding(horizontal = CodeTheme.dimens.grid.x3)
                    .align(Alignment.TopStart),
                painter = painterResource(
                    R.drawable.ic_code_logo_white
                ),
                contentDescription = "",
            )

            Column(
                modifier = Modifier
                    .background(BrandOverlay, RoundedCornerShape(8.dp))
                    .clickable { state.request() }
                    .padding(CodeTheme.dimens.grid.x3),
                verticalArrangement = Arrangement.spacedBy(
                    CodeTheme.dimens.grid.x3,
                    Alignment.CenterVertically
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x10),
                    painter = painterResource(id = R.drawable.ic_biometrics),
                    contentDescription = null
                )

                Text(
                    text = stringResource(R.string.action_biometricsClickToAuthenticate),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.onBackground
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