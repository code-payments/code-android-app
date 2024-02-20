package com.getcode.view.main.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.Text
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.model.KinAmount
import com.getcode.models.LoginConfirmation
import com.getcode.models.LoginState
import com.getcode.models.PaymentConfirmation
import com.getcode.models.PaymentState
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.components.SlideToConfirmDefaults
import java.util.Locale

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(
                CodeTheme.shapes.medium.copy(
                    bottomStart = ZeroCornerSize,
                    bottomEnd = ZeroCornerSize
                )
            )
            .background(Brand)
            .padding(horizontal = 20.dp, vertical = 30.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        domain?.let {
            Text(
                text = it,
                color = Color.White,
                style = CodeTheme.typography.h3
            )
            SlideToConfirm(
                isLoading = isSending,
                trackColor = SlideToConfirmDefaults.BlueTrackColor,
                isSuccess = state is LoginState.Sent,
                onConfirm = { onSend() },
                label = stringResource(R.string.swipe_to_login)
            )
        }

        AnimatedContent(
            targetState = !isSending && state !is LoginState.Sent,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "show/hide cancel button"
        ) { show ->
            if (show) {
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonState = ButtonState.Subtle,
                    onClick = onCancel,
                    text = stringResource(id = android.R.string.cancel),
                )
            } else {
                Spacer(modifier = Modifier.minimumInteractiveComponentSize())
            }
        }
    }
}