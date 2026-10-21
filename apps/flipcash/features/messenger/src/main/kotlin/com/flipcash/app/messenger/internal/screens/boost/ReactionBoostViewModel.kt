package com.flipcash.app.messenger.internal.screens.boost

import androidx.lifecycle.viewModelScope
import com.flipcash.app.messenger.internal.payment.ReactionBoostPayer
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.MessageReactions
import com.flipcash.shared.chat.reactions.SelfReaction
import com.getcode.opencode.model.financial.Token
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The Paid Reactions sheet (node 10330:18091): which of the viewer's own reactions is boosted,
 * which amount they picked, and the payment once they swipe.
 *
 * Scoped to the sheet's own entry (`ChatStep.ReactionBoost`), so it starts empty each time the
 * sheet opens.
 *
 * Reacting stays free: the sheet only boosts a reaction the viewer has already made, and the
 * payment never adds or removes one. It follows the message's live reactions, so a reaction taken
 * back under the open sheet moves the selection to the viewer's next most recent one, and the
 * sheet closes ([Event.ReactionsGone]) once none are left.
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
        /** The viewer's own reactions on the message, most recent first. */
        val ownReactions: List<String> = emptyList(),
        /** The reaction being boosted, one of [ownReactions]. */
        val emoji: String? = null,
        val token: Token? = null,
        /** The picked amount, in whole US cents, or null before the viewer picks one. */
        val selectedCents: Long? = null,
        /** An amount entered on the keypad, kept on the "…" chip once chosen. */
        val customCents: Long? = null,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val canConfirm: Boolean
            get() = selectedCents != null && chatId != null && emoji != null && sendProgress.isIdle

        /** Whether tapping the emoji moves to another reaction. */
        val canCycle: Boolean get() = ownReactions.size > 1
    }

    sealed interface Event {
        data class Open(val chatId: ChatId, val messageId: Long) : Event

        /** Internal: the viewer's own reactions on the message, most recent first. */
        data class OwnReactionsChanged(val emoji: List<String>) : Event

        /** The emoji was tapped: boost the viewer's next reaction instead. */
        data object CycleEmoji : Event

        /** The viewer no longer has a reaction on the message to boost; the sheet closes. */
        data object ReactionsGone : Event
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

        observeOwnReactions()
    }

    /**
     * The viewer's reactions on the message, from the chat's live reactions, falling back to the
     * stored ones for a message the live overlay doesn't hold yet — the transcript's own fallback.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeOwnReactions() {
        stateFlow.map { state -> state.chatId?.let { chatId -> state.messageId?.let { chatId to it } } }
            .distinctUntilChanged()
            .flatMapLatest { target ->
                val (chatId, messageId) = target ?: return@flatMapLatest emptyFlow()
                val stored = flow {
                    emit(MessageReactions.from(chatCoordinator.getMessage(chatId, messageId)?.reactions))
                }
                combine(stored, chatCoordinator.observeChatReactions(chatId)) { fallback, live ->
                    boostable((live[messageId] ?: fallback).selfReactions)
                }
            }
            .distinctUntilChanged()
            .onEach { own ->
                dispatchEvent(Event.OwnReactionsChanged(own))
                if (own.isEmpty()) dispatchEvent(Event.ReactionsGone)
            }
            .launchIn(viewModelScope)
    }

    private fun boost() {
        val state = stateFlow.value
        val chatId = state.chatId ?: return
        val messageId = state.messageId ?: return
        val emoji = state.emoji?.takeIf { it in state.ownReactions } ?: return
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

            payer.boost(
                chatId = chatId,
                messageId = messageId,
                emoji = emoji,
                author = author,
                creator = creator,
                totalCents = cents,
                onAddMoney = { dispatchEvent(Event.AddMoney) },
            ).onSuccess {
                dispatchEvent(Event.SendProgressChanged(LoadingSuccessState(success = true)))
                delay(SUCCESS_HOLD_MS)
                dispatchEvent(Event.Completed)
            }.onFailure {
                dispatchEvent(Event.SendProgressChanged(LoadingSuccessState()))
            }
        }
    }

    internal companion object {
        private const val TAG = "ReactionBoost"

        /** How long the slider shows its check before the sheet closes. */
        private const val SUCCESS_HOLD_MS = 600L

        /** The viewer's [own] reactions the sheet can boost, most recent first. */
        fun boostable(own: List<SelfReaction>): List<String> =
            own.sortedByDescending { it.reactedAt }.map { it.emoji }

        /** The preset chips, in whole US cents (node 10330:18091). */
        val PresetCents = listOf(100L, 500L, 1_000L)

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.Open -> { state ->
                    if (state.chatId == event.chatId && state.messageId == event.messageId) {
                        state
                    } else {
                        State(chatId = event.chatId, messageId = event.messageId, token = state.token)
                    }
                }
                // Keeps the reaction the viewer chose while it's still theirs; otherwise their
                // most recent one.
                is Event.OwnReactionsChanged -> { state ->
                    state.copy(
                        ownReactions = event.emoji,
                        emoji = state.emoji?.takeIf { it in event.emoji } ?: event.emoji.firstOrNull(),
                    )
                }
                Event.CycleEmoji -> { state ->
                    if (!state.canCycle || !state.sendProgress.isIdle) {
                        state
                    } else {
                        val next = (state.ownReactions.indexOf(state.emoji) + 1) % state.ownReactions.size
                        state.copy(emoji = state.ownReactions[next])
                    }
                }
                is Event.TokenChanged -> { state -> state.copy(token = event.token) }
                is Event.SelectAmount -> { state -> state.copy(selectedCents = event.cents) }
                is Event.CustomAmountEntered -> { state ->
                    state.copy(selectedCents = event.cents, customCents = event.cents)
                }
                is Event.SendProgressChanged -> { state -> state.copy(sendProgress = event.progress) }
                Event.Confirm,
                Event.ReactionsGone,
                Event.Completed,
                Event.AddMoney -> { state -> state }
            }
        }
    }
}
