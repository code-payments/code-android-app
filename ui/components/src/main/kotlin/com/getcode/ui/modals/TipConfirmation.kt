package com.getcode.ui.modals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.error
import com.getcode.model.TwitterUser
import com.getcode.models.ConfirmationState
import com.getcode.models.SocialUserPaymentConfirmation
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.theme.bolded
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.components.Modal
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.components.R
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.components.TwitterUsernameDisplay

@Composable
fun TipConfirmation(
    modifier: Modifier = Modifier,
    confirmation: SocialUserPaymentConfirmation?,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by remember(confirmation?.state) {
        derivedStateOf { confirmation?.state }
    }

    val isSending by remember(state) {
        derivedStateOf { state is ConfirmationState.Sending }
    }

    Modal(modifier) {
        AsyncImage(
            modifier = Modifier
                .padding(top = CodeTheme.dimens.grid.x12)
                .size(72.dp)
                .clip(CircleShape),
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(confirmation?.imageUrl)
                .error(R.drawable.ic_placeholder_user)
                .placeholderMemoryCacheKey(confirmation?.metadata?.username)
                .build(),
            contentDescription = null,
        )

        TwitterUsernameDisplay(
            modifier = Modifier.fillMaxWidth(),
            username = confirmation?.metadata?.username.orEmpty(),
            verificationStatus = (confirmation?.metadata as? TwitterUser)?.verificationStatus
        )
        if (confirmation?.followerCountFormatted != null) {
            Text(
                text = "${confirmation.followerCountFormatted} Followers",
                color = CodeTheme.colors.textSecondary,
                style = CodeTheme.typography.textSmall
            )
        }

        Divider(
            modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x8),
            color = White10,
        )

        val amount by remember(confirmation?.amount) {
            derivedStateOf { confirmation?.amount }
        }

        amount?.let {
            PriceWithFlag(
                currencyCode = it.rate.currency,
                amount = it,
                iconSize = 24.dp
            ) { text ->
                Text(
                    text = text,
                    color = Color.White,
                    style = CodeTheme.typography.displayMedium.bolded()
                )
            }
        }

        SlideToConfirm(
            isLoading = isSending,
            isSuccess = state is ConfirmationState.Sent,
            onConfirm = { onSend() },
            label = stringResource(R.string.action_swipeToTip)
        )

        val enabled = !isSending && state !is ConfirmationState.Sent
        val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0f, label = "alpha")
        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha),
            enabled = enabled,
            buttonState = ButtonState.Subtle,
            onClick = onCancel,
            text = stringResource(id = android.R.string.cancel),
        )
    }
}