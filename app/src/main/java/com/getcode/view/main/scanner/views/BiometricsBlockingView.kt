package com.getcode.view.main.scanner.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.getcode.R
import com.getcode.theme.Brand
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.utils.BiometricsState

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