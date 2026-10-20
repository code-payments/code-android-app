package com.flipcash.app.messenger.internal.screens.boost

import androidx.lifecycle.viewModelScope
import com.flipcash.app.messenger.internal.payment.ReactionBoostPayer
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.payments.boost.ReactionBoostPlan
import com.getcode.opencode.model.financial.Token
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The Paid Reactions sheet (node 10330:18091): which amount the viewer picked for [State.emoji],
 * and the payment once they swipe.
 *
 * Scoped to the sheet's own entry (`ChatStep.ReactionBoost`), so it starts empty each time the
 * sheet opens.
 *
 * A boost is two calls: the reaction is added first, then the payment names it. A payment that
 * fails takes back the reaction it added, so a failed swipe leaves the message as it was.
 */
@HiltViewModel
internal class ReactionBoostViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val payer: ReactionBoostPayer,
    startChattingPayer: StartChattingPayer,
    dispatchers: DispatcherProvider,
) : BaseViewModel<ReactionBoostViewModel.State, ReactionBoostViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        val chatId: ChatId? = null,
        val messageId: Long? = null,
        val emoji: String = "",
        val token: Token? = null,
        /** The picked amount, in whole US cents, or null before the viewer picks one. */
        val selectedCents: Long? = null,
        /** An amount entered on the keypad, kept on the "…" chip once chosen. */
        val customCents: Long? = null,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val canConfirm: Boolean get() = selectedCents != null && chatId != null && sendProgress.isIdle
    }

    sealed interface Event {
        data class Open(val chatId: ChatId, val messageId: Long, val emoji: String) : Event
        data class TokenChanged(val token: Token) : Event

        /** A preset chip. */
        data class SelectAmount(val cents: Long) : Event

        /** The keypad behind the "…" chip returned [cents]. */
        data class CustomAmountEntered(val cents: Long) : Event

        data object Confirm : Event

        /** Internal: the swipe's progress moved. */
        data class SendProgressChanged(val progress: LoadingSuccessState) : Event

        /** The boost went through; the sheet closes. */
        data object Completed : Event

        /** The balance can't cover the boost and the viewer chose to add money. */
        data object AddMoney : Event
    }

    init {
        startChattingPayer.observeSelectedToken()
            .onEach { dispatchEvent(Event.TokenChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.Confirm>()
            .onEach { boost() }
            .launchIn(viewModelScope)
    }

    private fun boost() {
        val state = stateFlow.value
        val chatId = state.chatId ?: return
        val messageId = state.messageId ?: return
        val cents = state.selectedCents ?: return
        if (!state.sendProgress.isIdle) return

        viewModelScope.launch {
            dispatchEvent(Event.SendProgressChanged(LoadingSuccessState(loading = true)))

            val author = chatCoordinator.getMessage(chatId, messageId)?.senderId
            val creator = chatCoordinator.observeMetadata(chatId).first()?.metadata?.creator
            if (author == null || creator == null) {
                trace(
                    tag = TAG,
                    message = "No author or creator for message $messageId; boost not sent",
                    type = TraceType.Error,
                )
                dispatchEvent(Event.SendProgressChanged(LoadingSuccessState()))
                return@launch
            }
            // Checked before the reaction goes on, so a boost the split rejects adds nothing; the
            // payer explains the rejection.
            if (payer.plan(author, creator, cents) !is ReactionBoostPlan.Pay) {
                payer.boost(chatId, messageId, state.emoji, author, creator, cents, onAddMoney = {})
                dispatchEvent(Event.SendProgressChanged(LoadingSuccessState()))
                return@launch
            }

            val added = chatCoordinator.addReactionForBoost(chatId, messageId, state.emoji)
                .getOrElse {
                    dispatchEvent(Event.SendProgressChanged(LoadingSuccessState()))
                    return@launch
                }

            payer.boost(
                chatId = chatId,
                messageId = messageId,
                emoji = state.emoji,
                author = author,
                creator = creator,
                totalCents = cents,
                onAddMoney = { dispatchEvent(Event.AddMoney) },
            ).onSuccess {
                dispatchEvent(Event.SendProgressChanged(LoadingSuccessState(success = true)))
                delay(SUCCESS_HOLD_MS)
                dispatchEvent(Event.Completed)
            }.onFailure {
                if (added) chatCoordinator.removeUnpaidBoostReaction(chatId, messageId, state.emoji)
                dispatchEvent(Event.SendProgressChanged(LoadingSuccessState()))
            }
        }
    }

    internal companion object {
        private const val TAG = "ReactionBoost"

        /** How long the slider shows its check before the sheet closes. */
        private const val SUCCESS_HOLD_MS = 600L

        /** The preset chips, in whole US cents (node 10330:18091). */
        val PresetCents = listOf(100L, 500L, 1_000L)

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.Open -> { state ->
                    if (state.chatId == event.chatId && state.messageId == event.messageId && state.emoji == event.emoji) {
                        state
                    } else {
                        State(chatId = event.chatId, messageId = event.messageId, emoji = event.emoji, token = state.token)
                    }
                }
                is Event.TokenChanged -> { state -> state.copy(token = event.token) }
                is Event.SelectAmount -> { state -> state.copy(selectedCents = event.cents) }
                is Event.CustomAmountEntered -> { state ->
                    state.copy(selectedCents = event.cents, customCents = event.cents)
                }
                is Event.SendProgressChanged -> { state -> state.copy(sendProgress = event.progress) }
                Event.Confirm,
                Event.Completed,
                Event.AddMoney -> { state -> state }
            }
        }
    }
}
