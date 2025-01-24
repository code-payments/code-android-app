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
import androidx.compose.ui.res.stringResource
import com.getcode.model.Currency
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.kin
import com.getcode.models.ConfirmationState
import com.getcode.models.MessageTipPaymentConfirmation
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Modal
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.components.picker.Picker
import com.getcode.ui.components.picker.PickerState
import com.getcode.ui.components.picker.rememberPickerState
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.resources.LocalResources
import com.getcode.utils.Kin
import com.getcode.utils.formatAmountString
import xyz.flipchat.app.R
import com.getcode.ui.components.R as uiR

private val tipOptions = (1 until 100).map { KinAmount.newInstance(it.kin, Rate.oneToOne) }

@Composable
internal fun MessageTipPaymentConfirmation(
    modifier: Modifier = Modifier,
    confirmation: MessageTipPaymentConfirmation?,
    onSend: (KinAmount) -> Unit,
    onCancel: () -> Unit,
) {
    val state by remember(confirmation?.state) {
        derivedStateOf { confirmation?.state }
    }

    val isSending by remember(state) {
        derivedStateOf { state is ConfirmationState.Sending }
    }

    val resources = LocalResources.current!!

    val pickerState = rememberPickerState(items = tipOptions, prefix = "⬢") { item ->
        formatAmountString(
            resources = resources,
            currency = Currency.Kin,
            amount = item.kin.toKinValueDouble(),
            suffix = resources.getKinSuffix()
        )
    }

    Modal(modifier) {
        if (state != null) {
            MessageTipConfirmationContent(
                pickerState = pickerState,
                balance = confirmation?.balance,
                isSending = isSending,
                state = state,
                onApproved = {
                    pickerState.selectedItem?.let {
                        onSend(it)
                    }
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
}

@Composable
private fun MessageTipConfirmationContent(
    pickerState: PickerState<KinAmount>,
    balance: String?,
    isSending: Boolean,
    state: ConfirmationState?,
    onApproved: () -> Unit
) {
    Picker(
        modifier = Modifier.fillMaxWidth(),
        state = pickerState,
        textStyle = CodeTheme.typography.displayMedium
    )
    Text(
        text = stringResource(
            R.string.subtitle_balance,
            balance.orEmpty(),
        ),
        style = CodeTheme.typography.textSmall.copy(color = CodeTheme.colors.tertiary),
    )
    SlideToConfirm(
        isLoading = isSending,
        isSuccess = state is ConfirmationState.Sent,
        onConfirm = onApproved,
        label = stringResource(uiR.string.action_swipeToTip)
    )
}