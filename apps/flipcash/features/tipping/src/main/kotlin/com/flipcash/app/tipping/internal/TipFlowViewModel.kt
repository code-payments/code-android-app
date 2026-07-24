package com.flipcash.app.tipping.internal

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.toConversationReference
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class TipFlowViewModel @Inject constructor(
    chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    tippingCoordinator: TippingCoordinator,
    tokenCoordinator: TokenCoordinator,
    shareable: ShareSheetController,
    private val resources: ResourceHelper,
) : BaseViewModel<TipFlowViewModel.State, TipFlowViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        val steps: List<TipStep> = listOf(TipStep.Tips),
        // true when re-entering right after the user-profile setup handoff (Tips(resumed = true)).
        val resumed: Boolean = false,
        val currentStep: TipStep? = null,
        val tipChats: List<ConversationReference> = emptyList(),
        val tipCard: Scannable.TipCard? = null,
    )

    sealed interface Event {
        data class StepsUpdated(val steps: List<TipStep>) : Event
        data class OnStepChanged(val step: TipStep) : Event
        data class ChatsUpdated(val tips: List<ConversationReference>) : Event

        /** How the flow was entered — [resumed] is true for the post-setup handoff re-entry. */
        data class OnResumed(val resumed: Boolean) : Event
        data class OnTipCardPopulated(val card: Scannable.TipCard) : Event
        data object ShareTipCard: Event
    }

    init {
        combine(
            userManager.state.map { it.userProfile }.distinctUntilChanged(),
            stateFlow.map { it.resumed }.distinctUntilChanged(),
        ) { profile, resumed ->
            buildList {
                val hasProfile = profile?.displayName != null && profile.profilePicture != null
                when {
                    // Post-setup handoff: land on TipCard alone, with a close affordance. No Tips
                    // underneath — closing exits, and reopening from home lands on the Tips list.
                    resumed -> add(TipStep.TipCard)
                    // Fresh open, incomplete profile: start setup (Intro → user-profile flow).
                    !hasProfile -> add(TipStep.Intro)
                    // Fresh open, set up: the Tips list.
                    else -> add(TipStep.Tips)
                }
            }
        }.onEach {
            dispatchEvent(Event.StepsUpdated(it))
        }.launchIn(viewModelScope)

        // Rebuild the current user's tip card whenever their profile becomes available/changes.
        userManager.state
            .mapNotNull { it.userProfile }
            .distinctUntilChanged()
            .map { tippingCoordinator.resolveTipCard() }
            .onResult(onSuccess = { card -> dispatchEvent(Event.OnTipCardPopulated(card)) })
            .launchIn(viewModelScope)

        combine(
            chatCoordinator.feed(ChatType.TIP_DM),
            tokenCoordinator.tokens,
        ) { summaries, tokens ->
            val selfId = userManager.accountId
            val tokensByMint = tokens.associateBy { it.address }
            summaries.map { it.toConversationReference(selfId, tokensByMint, resources) }
        }.onEach { dispatchEvent(Event.ChatsUpdated(it)) }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ShareTipCard>()
            .mapNotNull { tippingCoordinator.currentUserId }
            .map { shareable.present(Shareable.TipCard(it)) }
            .launchIn(viewModelScope)
    }

    companion object {
        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.OnStepChanged -> { state -> state.copy(currentStep = event.step) }
                is Event.StepsUpdated -> { state -> state.copy(steps = event.steps) }
                is Event.ChatsUpdated -> { state -> state.copy(tipChats = event.tips) }
                is Event.OnResumed -> { state -> state.copy(resumed = event.resumed) }
                is Event.OnTipCardPopulated -> { state -> state.copy(tipCard = event.card) }
                is Event.ShareTipCard -> { state -> state }
            }
        }
    }
}
