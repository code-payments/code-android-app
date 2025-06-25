package com.flipcash.app.payments.internal

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
import com.flipcash.app.core.ui.FlagWithFiat
import com.flipcash.app.payments.ConfirmationState
import com.flipcash.app.payments.PublicPaymentConfirmation
import com.flipcash.shared.payments.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Modal
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun PublicPaymentConfirmationModal(
    modifier: Modifier = Modifier,
    confirmation: PublicPaymentConfirmation,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by remember(confirmation.state) {
        derivedStateOf { confirmation.state }
    }

    val isSending by remember(state) {
        derivedStateOf { state is ConfirmationState.Sending }
    }

    val requestedAmount = remember {
        confirmation.amount
    }

    BackHandler {
        onCancel()
    }


    Modal(modifier) {
        val amount = requestedAmount
        PaymentConfirmationContent(
            amount = amount,
            isSending = isSending,
            state = state,
            onApproved = onSend,
            label = when (confirmation.metadata) {
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
private fun PaymentConfirmationContent(
    amount: Fiat,
    isSending: Boolean,
    state: ConfirmationState?,
    label: String,
    onApproved: () -> Unit
) {
    FlagWithFiat(
        fiat = amount,
        iconSize = CodeTheme.dimens.staticGrid.x5,
        textStyle = CodeTheme.typography.displayMedium,
    )
    SlideToConfirm(
        isLoading = isSending,
        isSuccess = state is ConfirmationState.Sent,
        label = label,
        onConfirm = { onApproved() },
    )
}