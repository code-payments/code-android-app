package com.flipchat.features.payments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PaymentScaffold(content: @Composable () -> Unit) {
//    val payments = LocalPaymentController.current ?: error("CompositionLocal is null")
//
//    val state by payments.state.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        content()
//        val showScrim by remember(state.billState) {
//            derivedStateOf {
//                val loginConfirmation = state.billState.loginConfirmation
//                val paymentConfirmation = state.billState.paymentConfirmation
//                val socialPaymentConfirmation = state.billState.socialUserPaymentConfirmation
//
//                listOf(loginConfirmation, paymentConfirmation, socialPaymentConfirmation).any {
//                    it?.showScrim == true
//                }
//            }
//        }
//
//        val scrimAlpha by animateFloatAsState(if (showScrim) 1f else 0f, label = "scrim visibility")
//
//        if (showScrim) {
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .alpha(scrimAlpha)
//                    .background(Black40)
//                    .rememberedClickable(indication = null,
//                        interactionSource = remember { MutableInteractionSource() }) {}
//            )
//        }
//
//        // Tip Confirmation container
//        AnimatedContent(
//            modifier = Modifier.align(BottomCenter),
//            targetState = state.billState.socialUserPaymentConfirmation?.payload, // payload is constant across state changes
//            transitionSpec = AnimationUtils.modalAnimationSpec(speed = ModalAnimationSpeed.Fast),
//            label = "tip confirmation",
//        ) {
//            if (it != null) {
//                Box(
//                    contentAlignment = BottomCenter
//                ) {
//                    TipConfirmation(
//                        confirmation = state.billState.socialUserPaymentConfirmation,
//                        trackColor = SlideToConfirmDefaults.PurpleTrackColor,
//                        onSend = {
//                            payments.completePrivatePayment(
//                                onSuccess = {
//                                    // TODO: fetch chats
//                                },
//                                onError = {}
//                            )
//                        },
//                        onCancel = { payments.cancelPayment() }
//                    )
//                }
//            }
//        }
    }
}