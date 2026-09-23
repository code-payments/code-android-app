package com.flipcash.app.tipping.internal

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.flipcash.app.core.data.Loadable
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.toConversationReference
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/** Backs the "Chats" tab: the user's tip DMs and groups. */
@HiltViewModel
internal class ChatsViewModel @Inject constructor(
    chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    tokenCoordinator: TokenCoordinator,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : BaseViewModel<ChatsViewModel.State, ChatsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        // Loading until the chat feed emits — distinguishes "still loading" from "loaded, none", so
        // the empty state doesn't flash on top of a list that's about to arrive.
        val chats: Loadable<List<ConversationReference>> = Loadable.Loading(),
    )

    sealed interface Event {
        data class ChatsUpdated(val chats: Loadable<List<ConversationReference>>) : Event
    }

    init {
        fun conversations(summaries: List<ChatSummary>, tokens: List<Token>): List<ConversationReference> {
            val selfId = userManager.accountId
            val tokensByMint = tokens.associateBy { it.address }
            return summaries.map { it.toConversationReference(selfId, tokensByMint, resources) }
        }

        // On a cold launch the feed is usually built before this screen is, so draw it on the first
        // frame rather than waiting for the collector below to get a turn on the main thread.
        chatCoordinator.currentFeed(ChatType.TIP_DM, ChatType.GROUP)?.let { summaries ->
            dispatchEvent(
                Event.ChatsUpdated(Loadable.Loaded(conversations(summaries, tokenCoordinator.cachedTokens())))
            )
        }

        combine(
            chatCoordinator.feed(ChatType.TIP_DM, ChatType.GROUP),
            tokenCoordinator.tokens,
            ::conversations,
        )
            // Off the main thread: on a cold launch the first mapping lands while the main thread
            // is drawing the app's first screens, and waiting for it holds the Chats tab blank.
            .flowOn(dispatchers.Default)
            .onEach { dispatchEvent(Event.ChatsUpdated(Loadable.Loaded(it))) }.launchIn(viewModelScope)
    }

    internal companion object {
        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.ChatsUpdated -> { state -> state.copy(chats = event.chats) }
            }
        }
    }
}
