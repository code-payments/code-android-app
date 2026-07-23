package com.flipcash.app.scanner.internal.bills.decor

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.core.tipping.LocalTipCoordinator
import com.flipcash.app.core.tipping.TipEvent
import com.flipcash.app.scanner.internal.ui.modals.TipUserModal
import com.flipcash.app.session.LocalSessionController
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.core.measured
import com.getcode.ui.utils.AnimationUtils

/**
 * Decorator owned by a [Scannable.TipCard]. Placeholder home for the tip card's bottom modal
 * (analogous to the payable "received funds" confirmation). Wire it here, gating `visible` on
 * `context.liveBill is Scannable.TipCard` so it animates out on dismiss, and use the retained
 * [tipCard] for content so it persists through the exit:
 *
 * ```
 * AnimatedVisibility(
 *     modifier = Modifier.align(BottomCenter),
 *     visible = context.liveBill is Scannable.TipCard,
 *     enter = AnimationUtils.modalEnter(0),
 *     exit = AnimationUtils.modalExit,
 * ) {
 *     TipCardModal(tipCard = tipCard, onDone = { context.onDismiss() })
 * }
 * ```
 */
internal data class TipCardDecorator(private val tipCard: Scannable.TipCard) : ScannableDecorator {
    @Composable
    override fun BoxScope.Content(context: ScannableDecoratorContext) {
        val billState = context.billState
        val session = LocalSessionController.current
        val navigator = LocalCodeNavigator.current
        val tipCoordinator = LocalTipCoordinator.current
        val selection by tipCoordinator.selection.collectAsState()

        val tipPresented = context.liveBill is Scannable.TipCard
        // Can't afford the minimum tip → the modal stays hidden (gated by
        // showManagementOptions); prompt to add money instead. The navigator lives here in
        // the UI, so we route directly rather than plumbing a callback through the session.
        LaunchedEffect(tipPresented, selection.canTip) {
            if (tipPresented && !selection.canTip) {
                session?.presentDepositOptions { route ->
                    navigator.openAsSheet(route)
                    // No giveable balance to tip with → steer to add-money / discover, and dismiss the
                    // tip card so we don't leave it stranded behind the prompt.
                    context.onDismiss()
                }
            }
        }

        // Handle the coordinator's one-shot UI events (e.g. open the add money flow after the
        // insufficient-balance prompt's "Add Money"). Collected here, not in the modal, so it
        // fires whether the tip modal is on screen or not.
        LaunchedEffect(tipCoordinator) {
            tipCoordinator.events.collect { event ->
                when (event) {
                    is TipEvent.OpenRoute -> {
                        navigator.openAsSheet(event.route)
                    }
                    // Open the completed tip's chat with the tips list beneath it (navigateAll packs
                    // the chat into the tips sheet's back stack), so back returns to the list.
                    is TipEvent.LaunchChat -> {
                        navigator.navigateAll(
                            listOf(
                                AppRoute.Sheets.Tips(),
                                AppRoute.Messaging.Chat(event.identifier),
                            )
                        )
                        context.onDismiss()
                    }
                }
            }
        }

        AnimatedScannableDecorator(
            visible = tipPresented && context.showManagementOptions,
            enter = AnimationUtils.modalEnter(billState.confirmationDelayMillis),
            exit = AnimationUtils.modalExit,
            modifier = Modifier.align(BottomCenter),
        ) {
            // Report the modal's height as soon as it starts entering (target Visible), not only
            // once it settles — this lets the container move the tip card in sync with the modal's
            // slide (both share the same enter timing). Reports 0 on exit so the card re-centers.
            var modalHeight by remember { mutableStateOf(0.dp) }
            val entering = transition.targetState == EnterExitState.Visible
            LaunchedEffect(modalHeight, entering) {
                context.onManagementHeightMeasured(if (entering) modalHeight else 0.dp)
            }

            Box(
                modifier = Modifier.measured { modalHeight = it.height },
                contentAlignment = BottomCenter,
            ) {
                TipUserModal(
                    card = tipCard,
                )
            }
        }
    }
}
