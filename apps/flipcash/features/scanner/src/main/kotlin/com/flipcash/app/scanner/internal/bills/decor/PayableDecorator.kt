package com.flipcash.app.scanner.internal.bills.decor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.scanner.internal.bills.BillManagementOptions
import com.flipcash.app.scanner.internal.ui.modals.ReceivedFundsConfirmation
import com.getcode.ui.core.measured
import com.getcode.ui.utils.AnimationUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Decorator owned by a [Scannable.Payable]: the bill management options row
 * (send / send-as-link / share / cancel) and the "you received funds" confirmation modal.
 *
 * [bill] is the retained scannable so the confirmation modal keeps its content through the
 * exit animation while [ScannableDecoratorContext.liveBill] flips to null on dismiss.
 */
internal data class PayableDecorator(private val bill: Scannable.Payable) : ScannableDecorator {
    @Composable
    override fun BoxScope.Content(context: ScannableDecoratorContext) {
        val billState = context.billState

        // Bill management options
        AnimatedScannableDecorator(
            visible = context.liveBill is Scannable.Payable && context.showManagementOptions,
            enter = fadeIn(),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .align(BottomCenter)
                .measured { context.onManagementHeightMeasured(it.height) },
        ) {
            var canCancel by remember {
                mutableStateOf(false)
            }
            BillManagementOptions(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars),
                primaryAction = billState.primaryAction,
                secondaryAction = billState.secondaryAction,
                isSending = context.isRemoteSendLoading,
                isInteractable = canCancel,
            )

            LaunchedEffect(transition.isRunning, transition.targetState) {
                // wait for spring settle to enable cancel to not prematurely cancel
                // the enter. doing so causing the exit of the bill to not run, or run its own dismiss animation
                if (transition.targetState == EnterExitState.Visible && transition.currentState == transition.targetState) {
                    delay(500.milliseconds)
                    canCancel = true
                }
            }

            BackHandler(canCancel) {
                context.onDismiss()
            }
        }

        // Bill Received Bottom Dialog
        AnimatedScannableDecorator(
            visible = (context.liveBill as? Scannable.Payable)?.didReceive == true,
            enter = AnimationUtils.modalEnter(billState.confirmationDelayMillis),
            exit = AnimationUtils.modalExit,
            modifier = Modifier.align(BottomCenter),
        ) {
            Box(
                contentAlignment = BottomCenter
            ) {
                ReceivedFundsConfirmation(
                    bill = bill,
                    onClaim = { context.onDismiss() }
                )
            }
        }
    }
}
