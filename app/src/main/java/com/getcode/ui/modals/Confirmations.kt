package com.getcode.ui.modals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.currentOrThrow
import com.getcode.LocalSession
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.BuyMoreKinModal
import com.getcode.navigation.screens.BuySellScreen
import com.getcode.theme.Black40
import com.getcode.ui.utils.AnimationUtils
import com.getcode.ui.utils.ModalAnimationSpeed
import com.getcode.ui.utils.rememberedClickable

@Composable
fun ConfirmationModals(
    modifier: Modifier = Modifier,
) {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val session = LocalSession.currentOrThrow
    val sessionState by session.state.collectAsState()
    Box(modifier = modifier) {
        val billState by rememberUpdatedState(sessionState.billState)

        val showScrim by remember(billState) {
            derivedStateOf {
                val loginConfirmation = billState.loginConfirmation
                val paymentConfirmation = billState.privatePaymentConfirmation
                val socialPaymentConfirmation = billState.socialUserPaymentConfirmation

                listOf(loginConfirmation, paymentConfirmation, socialPaymentConfirmation).any {
                    it?.showScrim == true
                }
            }
        }

        val scrimAlpha by animateFloatAsState(if (showScrim) 1f else 0f, label = "scrim visibility")

        if (showScrim) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(scrimAlpha)
                    .background(Black40)
                    .rememberedClickable(indication = null,
                        interactionSource = remember { MutableInteractionSource() }) {}
            )
        }

        // Payment Confirmation container
        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = sessionState.billState.privatePaymentConfirmation?.payload, // payload is constant across state changes
            transitionSpec = AnimationUtils.modalAnimationSpec(),
            label = "payment confirmation",
        ) {
            if (it != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    PaymentConfirmation(
                        confirmation = sessionState.billState.privatePaymentConfirmation,
                        balance = sessionState.balance,
                        onAddKin = {
                            session.rejectPayment()
                            if (sessionState.buyModule.enabled) {
                                if (sessionState.buyModule.available) {
                                    navigator.show(BuyMoreKinModal(showClose = true))
                                } else {
                                    TopBarManager.showMessage(
                                        TopBarManager.TopBarMessage(
                                            title = context.getString(R.string.error_title_buyModuleUnavailable),
                                            message = context.getString(R.string.error_description_buyModuleUnavailable),
                                            type = TopBarManager.TopBarMessageType.ERROR
                                        )
                                    )
                                }
                            } else {
                                navigator.show(BuySellScreen)
                            }
                        },
                        onSend = { session.completePayment() },
                        onCancel = {
                            session.rejectPayment()
                        }
                    )
                }
            }
        }

        // Login Confirmation container
        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = sessionState.billState.loginConfirmation?.payload, // payload is constant across state changes
            transitionSpec = AnimationUtils.modalAnimationSpec(),
            label = "login confirmation",
        ) {
            if (it != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    LoginConfirmation(
                        confirmation = sessionState.billState.loginConfirmation,
                        onSend = { session.completeLogin() },
                        onCancel = {
                            session.rejectLogin()
                        }
                    )
                }
            }
        }

        // Social Payment Confirmation container
        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = sessionState.billState.socialUserPaymentConfirmation?.payload, // payload is constant across state changes
            transitionSpec = AnimationUtils.modalAnimationSpec(speed = ModalAnimationSpeed.Fast),
            label = "tip confirmation",
        ) {
            if (it != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    TipConfirmation(
                        confirmation = sessionState.billState.socialUserPaymentConfirmation,
                        onSend = {
                            if (sessionState.billState.socialUserPaymentConfirmation?.isPrivate == true) {
                                session.completePrivatePayment()
                            } else {
                                session.completeTipPayment()
                            }
                        },
                        onCancel = { session.cancelTip() }
                    )
                }
            }
        }
    }
}