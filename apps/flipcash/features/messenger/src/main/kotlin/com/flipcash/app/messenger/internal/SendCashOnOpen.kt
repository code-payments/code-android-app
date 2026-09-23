package com.flipcash.app.messenger.internal

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.flipcash.app.core.chat.ChatParticipant
import com.getcode.opencode.model.financial.Fiat

/**
 * Whether [ChatViewModel.Event.OnSendCash] would now pick the step it will keep picking.
 *
 * The handler drops the event while there is no participant, and otherwise branches on the fee to
 * open the chat: the fee sheet when there is one, the keypad when there isn't. Before the fee and
 * the chat's existence are both known, that branch can land on the keypad for a payment that
 * should have been the fee sheet. So a tip DM is ready only once the two agree:
 * - the chat exists and there is no opening fee, or
 * - the chat doesn't exist and the fee has resolved.
 *
 * [chatExists] is null until the member store has answered. A contact DM never has a fee, so it is
 * ready as soon as it has a participant.
 */
internal fun isSendCashReady(
    participant: ChatParticipant?,
    chatExists: Boolean?,
    openingFee: Fiat?,
): Boolean = when (participant) {
    null -> false
    is ChatParticipant.Contact -> true
    is ChatParticipant.TipUser -> when (chatExists) {
        null -> false
        true -> openingFee == null
        false -> openingFee != null
    }
}

/**
 * Calls [onStart] once, the first time [ready] is true while [requested] is.
 *
 * The flag is saved, so recomposition, rotation, process death, and coming back from the step
 * [onStart] opened don't call it again.
 */
@VisibleForTesting
@Composable
internal fun StartSendCashOnceReady(
    requested: Boolean,
    ready: Boolean,
    onStart: () -> Unit,
) {
    var started by rememberSaveable { mutableStateOf(false) }
    val currentOnStart by rememberUpdatedState(onStart)
    LaunchedEffect(requested, ready) {
        if (requested && ready && !started) {
            started = true
            currentOnStart()
        }
    }
}
