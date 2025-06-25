package com.flipcash.app.payments.internal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.bill.ConfirmationState
import com.flipcash.app.core.bill.PoolResolutionConfirmation
import com.flipcash.app.payments.PoolBidPaymentMetadata
import com.flipcash.shared.payments.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.components.Modal
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun PoolResolutionConfirmation(
    modifier: Modifier = Modifier,
    confirmation: PoolResolutionConfirmation?,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by remember(confirmation?.state) {
        derivedStateOf { confirmation?.state }
    }

    val isSending by remember(state) {
        derivedStateOf { state is ConfirmationState.Sending }
    }

    BackHandler {
        onCancel()
    }

    Modal(modifier) {
        PoolResolutionContent(
            isSending = isSending,
            state = state,
            onApproved = onSend,
            label = when (confirmation?.metadata) {
                is PoolBidPaymentMetadata -> stringResource(R.string.action_swipeToBuyIn)
                else -> stringResource(id = R.string.action_swipeToPay)
            }
        )
        val enabled = state !is ConfirmationState.Sending && state !is ConfirmationState.Sent
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

@Composable
private fun PoolResolutionContent(
    isSending: Boolean,
    state: ConfirmationState?,
    label: String,
    onApproved: () -> Unit
) {

}