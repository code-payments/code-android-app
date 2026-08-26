package com.flipcash.app.tipping.internal

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.bills.share.TipCodePreviewCache
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.tipping.R
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
    tipCodePreviewCache: TipCodePreviewCache,
    private val resources: ResourceHelper,
) : BaseViewModel<TipFlowViewModel.State, TipFlowViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        /**
         * The steps the flow opens on.
         *
         * This flow *is* the "Chats" root tab: it always shows the list (and its empty state)
         * whatever the profile looks like, so a nameless account gets the same chrome as any other,
         * and the claim-your-tip-card prompt lives on the You tab instead. Swapping a root tab out
         * for a setup screen would also strand that screen's Close, which has no sheet to dismiss.
         * The post-setup handoff is the one exception, and it is seeded from the route
         * (see TippingFlowScreen).
         */
        val steps: List<TipStep> = listOf(TipStep.Tips),
        // true when re-entering right after the user-profile setup handoff (Tips(resumed = true)).
        val resumed: Boolean = false,
        val currentStep: TipStep? = null,
        // Loading until the chat feed emits — distinguishes "still loading" from "loaded, none", so
        // the empty state doesn't flash on top of a list that's about to arrive.
        val tipChats: Loadable<List<ConversationReference>> = Loadable.Loading(),
        val tipCard: Scannable.TipCard? = null,
    )

    sealed interface Event {
        data class OnStepChanged(val step: TipStep) : Event
        data class ChatsUpdated(val tips: Loadable<List<ConversationReference>>) : Event

        /** How the flow was entered — [resumed] is true for the post-setup handoff re-entry. */
        data class OnResumed(val resumed: Boolean) : Event
        data class OnTipCardPopulated(val card: Scannable.TipCard) : Event
        data object ShareTipCard: Event
    }

    init {
        // Rebuild the current user's tip card whenever their profile becomes available/changes.
        userManager.state
            .mapNotNull { it.userProfile }
            .distinctUntilChanged()
            .map { tippingCoordinator.resolveTipCard() }
            .onResult(onSuccess = { card ->
                dispatchEvent(Event.OnTipCardPopulated(card))
                // Render the Sharesheet preview eagerly so it's ready by the time the user taps share.
                tippingCoordinator.currentUserId?.let { tipCodePreviewCache.prepare(it, card) }
            })
            .launchIn(viewModelScope)

        combine(
            chatCoordinator.feed(ChatType.TIP_DM),
            tokenCoordinator.tokens,
        ) { summaries, tokens ->
            val selfId = userManager.accountId
            val tokensByMint = tokens.associateBy { it.address }
            summaries.map { it.toConversationReference(selfId, tokensByMint, resources) }
        }.onEach { dispatchEvent(Event.ChatsUpdated(Loadable.Loaded(it))) }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ShareTipCard>()
            .mapNotNull { tippingCoordinator.currentUserId }
            .map { userId ->
                // Title shown above the link, e.g. "Tip X" (same label as the card).
                val title = stateFlow.value.tipCard?.user?.displayName
                    ?.let { resources.getString(R.string.label_tipUser, it) }
                // Attach the eagerly-rendered preview if it's ready; null shares the URL alone.
                shareable.present(
                    Shareable.TipCard(
                        userId = userId,
                        preview = tipCodePreviewCache.get(userId),
                        title = title,
                        username = stateFlow.value.tipCard?.user?.username,
                    )
                )
            }
            .launchIn(viewModelScope)
    }

    internal companion object {
        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.OnStepChanged -> { state -> state.copy(currentStep = event.step) }
                is Event.ChatsUpdated -> { state -> state.copy(tipChats = event.tips) }
                is Event.OnResumed -> { state -> state.copy(resumed = event.resumed) }
                is Event.OnTipCardPopulated -> { state -> state.copy(tipCard = event.card) }
                is Event.ShareTipCard -> { state -> state }
            }
        }
    }
}
