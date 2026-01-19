package com.flipcash.app.payments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.flipcash.app.payments.internal.PublicPaymentConfirmationModal
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.utils.AnimationUtils
import com.getcode.ui.utils.ModalAnimationSpeed

@Composable
fun PaymentScaffold(content: @Composable () -> Unit) {
    val payments = LocalPaymentController.current

    val state by payments.state.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        val scrimDetails by rememberConfirmationDetails(state)

        val scrimAlpha by animateFloatAsState(if (scrimDetails.show) 1f else 0f, label = "scrim visibility")

        if (scrimDetails.show) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(scrimAlpha)
                    .background(CodeTheme.colors.scrim)
                    .rememberedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (scrimDetails.cancellable) {
                            payments.cancelRequest(fromUser = true)
                        }
                    }
            )
        }

        // public payments
        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = state.poolBidConfirmation,
            contentKey = { it?.amount },
            transitionSpec = AnimationUtils.modalAnimationSpec(speed = ModalAnimationSpeed.Fast()),
            label = "public payments",
        ) { confirmation ->
            if (confirmation != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = BottomCenter
                ) {
                    PublicPaymentConfirmationModal(
                        confirmation = confirmation,
                        onSend = { payments.completeRequest() },
                        onCancel = { payments.cancelRequest(fromUser = true) }
                    )
                }
            } else {
                Spacer(Modifier.fillMaxWidth())
            }
        }
    }
}

data class ScrimDetails(val show: Boolean, val cancellable: Boolean)

@Composable
private fun rememberConfirmationDetails(state: PaymentState): State<ScrimDetails> {
    return remember(state) {
        derivedStateOf {
            val publicPaymentConfirmation = state.poolBidConfirmation

            listOf(
                publicPaymentConfirmation,
            ).firstNotNullOfOrNull { it }?.let { conf ->
                ScrimDetails(conf.showScrim, conf.cancellable)
            } ?: ScrimDetails(show = false, cancellable = false)
        }
    }
}