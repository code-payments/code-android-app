package xyz.flipchat.app.features.payments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.getcode.model.KinAmount
import com.getcode.models.ConfirmationState
import com.getcode.models.PublicPaymentConfirmation
import com.getcode.theme.CodeTheme
import com.getcode.theme.bolded
import com.getcode.ui.components.Modal
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun PublicPaymentConfirmation(
    modifier: Modifier = Modifier,
    confirmation: PublicPaymentConfirmation?,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by remember(confirmation?.state) {
        derivedStateOf { confirmation?.state }
    }

    val isSending by remember(state) {
        derivedStateOf { state is ConfirmationState.Sending }
    }

    val requestedAmount by remember(confirmation?.amount?.kin?.quarks) {
        derivedStateOf { confirmation?.amount }
    }

    Modal(modifier) {
        val amount = requestedAmount
        if (state != null && amount != null) {
            PaymentConfirmationContent(
                amount = amount,
                isSending = isSending,
                state = state,
                onApproved = onSend
            )
            val enabled = state !is ConfirmationState.Sending && state !is ConfirmationState.Sent
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
}

@Composable
private fun PaymentConfirmationContent(
    amount: KinAmount,
    isSending: Boolean,
    state: ConfirmationState?,
    onApproved: () -> Unit
) {
    PriceWithFlag(
        currencyCode = amount.rate.currency,
        amount = amount,
        iconSize = 24.dp
    ) {
        Text(
            text = it,
            color = Color.White,
            style = CodeTheme.typography.displayMedium.bolded()
        )
    }
    SlideToConfirm(
        isLoading = isSending,
        isSuccess = state is ConfirmationState.Sent,
        onConfirm = { onApproved() },
    )
}