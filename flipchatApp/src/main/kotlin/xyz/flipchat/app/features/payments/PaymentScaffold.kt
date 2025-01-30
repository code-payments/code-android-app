package xyz.flipchat.app.features.payments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.getcode.models.BillState
import com.getcode.theme.Black40
import com.getcode.ui.modals.TipConfirmation
import com.getcode.ui.utils.AnimationUtils
import com.getcode.ui.utils.ModalAnimationSpeed
import com.getcode.ui.utils.rememberedClickable
import xyz.flipchat.services.LocalPaymentController

@Composable
fun PaymentScaffold(content: @Composable () -> Unit) {
    val payments = LocalPaymentController.current ?: error("CompositionLocal is null")

    val state by payments.state.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        val scrimDetails by rememberConfirmationDetails(state.billState)

        val scrimAlpha by animateFloatAsState(if (scrimDetails.show) 1f else 0f, label = "scrim visibility")

        if (scrimDetails.show) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(scrimAlpha)
                    .background(Black40)
                    .rememberedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (scrimDetails.cancellable) {
                            payments.cancelPayment()
                        }
                    }
            )
        }

        // public payments
        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = state.billState.publicPaymentConfirmation?.amount, // amount is constant across state changes
            transitionSpec = AnimationUtils.modalAnimationSpec(speed = ModalAnimationSpeed.Fast),
            label = "public payments",
        ) {
            if (it != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    PublicPaymentConfirmation(
                        confirmation = state.billState.publicPaymentConfirmation,
                        onSend = { payments.completePublicPayment() },
                        onCancel = { payments.cancelPayment() }
                    )
                }
            }
        }

        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = state.billState.messageTipPaymentConfirmation?.balance,
            transitionSpec = AnimationUtils.modalAnimationSpec(speed = ModalAnimationSpeed.Fast),
            label = "message tip payments",
        ) {
            if (it != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    MessageTipPaymentConfirmation(
                        confirmation = state.billState.messageTipPaymentConfirmation,
                        onSend = { amount -> payments.completeMessageTip(amount) },
                        onCancel = { payments.cancelPayment() }
                    )
                }
            }
        }
    }
}

data class ScrimDetails(val show: Boolean, val cancellable: Boolean)

@Composable
private fun rememberConfirmationDetails(billState: BillState): State<ScrimDetails> {
    return remember(billState) {
        derivedStateOf {
            val publicPaymentConfirmation = billState.publicPaymentConfirmation
            val messageTipPaymentConfirmation = billState.messageTipPaymentConfirmation

            listOf(
                publicPaymentConfirmation,
                messageTipPaymentConfirmation
            ).firstNotNullOfOrNull { it }?.let { conf ->
                ScrimDetails(conf.showScrim, conf.cancellable)
            } ?: ScrimDetails(show = false, cancellable = false)
        }
    }
}