package com.flipcash.app.messenger.internal.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.messenger.internal.ChatCashBillSlot
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.session.LocalSessionController
import com.flipcash.shared.chat.ArrivalSource
import com.flipcash.shared.chat.chatCashBillShare
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Shows the received bill over this chat when cash for the viewer arrives on the live stream, and
 * gives the keyboard back when the bill closes. See [chatCashBillShare] for when a payment earns a
 * bill; [nothingOnTop] is this screen's answer to whether the chat is the top thing on screen.
 *
 * The keyboard is not taken down here: the bill overlay clears focus as it presents. This only
 * remembers that it was up, and raises the composer's own input request once the bill is gone.
 */
@Composable
internal fun ChatCashBillEffect(viewModel: ChatViewModel, nothingOnTop: Boolean, keyboardUp: Boolean) {
    val session = LocalSessionController.current ?: return
    val flagOn by LocalFeatureFlags.current.observe(FeatureFlag.ChatCashBill).collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val slot = remember { ChatCashBillSlot() }

    val latestNothingOnTop by rememberUpdatedState(nothingOnTop)
    val latestFlagOn by rememberUpdatedState(flagOn)
    val latestKeyboardUp by rememberUpdatedState(keyboardUp)

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { slot.onAppStopped() }

    LaunchedEffect(session, slot, viewModel) {
        session.billState
            .map { it.bill != null }
            .distinctUntilChanged()
            .collect { showing ->
                val foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (slot.onSessionBill(showing, foreground, System.currentTimeMillis())) {
                    viewModel.dispatchEvent(ChatViewModel.Event.OnStartMessageInput)
                }
            }
    }

    LaunchedEffect(session, slot, viewModel) {
        // Taken one at a time: while one is being priced, the next waits, then meets the slot.
        viewModel.liveCashArrivals.collect { arrival ->
            fun decide() = chatCashBillShare(
                source = ArrivalSource.Live,
                flagOn = latestFlagOn,
                openChat = viewModel.openChatId(),
                chatId = arrival.chatId,
                selfId = viewModel.viewerId(),
                senderId = arrival.senderId,
                content = arrival.content,
                nothingOnTop = latestNothingOnTop,
                // Checked here, before the entry starts a give for this bill; the slot inside the
                // presenter is too late for that.
                billShowing = slot.taken(session.billState.value.bill != null, System.currentTimeMillis()),
            )

            val share = decide() ?: return@collect
            val bill = viewModel.buildChatCashBill(share) ?: return@collect
            // Pricing suspends, so what was on top of the chat a moment ago may not be now.
            if (decide() == null) return@collect

            slot.onRequested(keyboardUp = latestKeyboardUp, nowMs = System.currentTimeMillis())
            session.showBill(bill)
        }
    }
}
