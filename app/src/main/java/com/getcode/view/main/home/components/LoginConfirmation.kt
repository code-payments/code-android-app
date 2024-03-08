package com.getcode.view.main.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.models.LoginConfirmation
import com.getcode.models.LoginState
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.Modal
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.components.SlideToConfirmDefaults

@Composable
internal fun LoginConfirmation(
    modifier: Modifier = Modifier,
    confirmation: LoginConfirmation?,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by remember(confirmation?.state) {
        derivedStateOf { confirmation?.state }
    }

    val isSending by remember(state) {
        derivedStateOf { state is LoginState.Sending }
    }

    val domain by remember(confirmation?.domain) {
        derivedStateOf {
            confirmation?.domain?.urlString?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
    }

    Modal(modifier, backgroundColor = Color.Black) {
        domain?.let {
            Text(
                text = it,
                color = Color.White,
                style = CodeTheme.typography.h3
            )
            SlideToConfirm(
                isLoading = isSending,
                trackColor = SlideToConfirmDefaults.BlackTrackColor,
                isSuccess = state is LoginState.Sent,
                onConfirm = { onSend() },
                label = stringResource(R.string.action_swipeToLogin)
            )
        }

        val enabled = !isSending && state !is LoginState.Sent
        val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0f, label = "alpha")
        CodeButton(
            modifier = Modifier.fillMaxWidth().alpha(alpha),
            enabled = enabled,
            buttonState = ButtonState.Subtle,
            onClick = onCancel,
            text = stringResource(id = android.R.string.cancel),
        )
    }
}