package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.ResolveState
import com.flipcash.app.messenger.internal.ChatViewModel.State
import com.flipcash.shared.chat.UnreadBoundary
import com.getcode.view.LoadingSuccessState

/** Sending: the message and group-send flows, resolve outcomes, and the cash-link taps. Null when [event] belongs to another area. */
internal fun reduceSend(event: Event, lifetime: UnreadDividerLifetime): ((State) -> State)? =
    when (event) {
        Event.OnSendCash -> { state -> state }
        Event.OnStartMessageInput -> { state -> state.copy(messageInputRequested = true) }
        Event.OnStopMessageInput -> { state -> state }
        Event.OnMessageInputConsumed -> { state -> state.copy(messageInputRequested = false) }
        Event.ResolveCompleted -> { state ->
            state.copy(resolveState = ResolveState.Resolved)
        }
        is Event.ResolveFailed -> { state ->
            state.copy(resolveState = ResolveState.Failed)
        }
        is Event.SendMessage -> { state ->
            val clears = lifetime == UnreadDividerLifetime.UntilSend &&
                state.unreadBoundary is UnreadBoundary.At
            val sent = state.copy(returnStack = emptyList())
            if (clears) sent.copy(unreadBoundary = UnreadBoundary.None) else sent
        }
        is Event.RetryMessage -> { state -> state }
        is Event.NavigateToAmountEntry -> { state -> state.copy(sendProgress = LoadingSuccessState()) }
        Event.NavigateToRequestMoney -> { state -> state }
        is Event.PresentDepositOptions -> { state -> state }
        is Event.OpenScreen -> { state -> state }
        is Event.MentionTapped -> { state -> state }
        is Event.OpenMention -> { state -> state }
        is Event.OnConfirmRequested -> { state -> state }
        is Event.OnSendRequested -> { state -> state }
        is Event.GroupSendUpdated -> { state -> state.copy(groupSend = event.groupSend) }
        is Event.OnGroupSendMemberToggled -> { state -> state }
        Event.OnGroupSendBack -> { state -> state }
        Event.OnGroupSendPeopleConfirmed -> { state -> state }
        Event.OnGroupSendRequested -> { state -> state }
        is Event.SendStateUpdated -> { state ->
            state.copy(
                sendProgress = LoadingSuccessState(
                    event.loading,
                    event.success,
                )
            )
        }
        is Event.SendComplete -> { state -> state }
        // Nothing on screen moves when a voucher is tapped -- the link leaves, the card
        // keeps saying what it said, and the claim comes back as its own signal.
        is Event.CashLinkOpened -> { state -> state }
        is Event.CashLinkRefused -> { state -> state }
        else -> null
    }
