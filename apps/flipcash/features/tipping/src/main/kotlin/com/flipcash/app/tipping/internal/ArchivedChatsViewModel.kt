package com.flipcash.app.tipping.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.archivedChatListFeed
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Archived screen: the archived chats, newest first, and unarchive. */
@HiltViewModel
internal class ArchivedChatsViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val archiveStore: ChatArchiveStore,
    userManager: UserManager,
    tokenCoordinator: TokenCoordinator,
    resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : BaseViewModel<ArchivedChatsViewModel.State, ArchivedChatsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        val chats: Loadable<List<ConversationReference>> = Loadable.Loading(),
    )

    sealed interface Event {
        data class ChatsUpdated(val chats: Loadable<List<ConversationReference>>) : Event
    }

    fun unarchive(chatId: ChatId) {
        viewModelScope.launch { archiveStore.unarchive(chatId) }
    }

    init {
        combine(
            chatCoordinator.archivedChatListFeed(),
            tokenCoordinator.tokens,
            chatCoordinator.observeSenderProfiles(),
        ) { summaries, tokens, senderProfiles ->
            mapConversations(summaries, tokens, senderProfiles, userManager.accountId, resources, chatCoordinator)
        }
            .flowOn(dispatchers.Default)
            .onEach { dispatchEvent(Event.ChatsUpdated(Loadable.Loaded(it))) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.ChatsUpdated -> { state -> state.copy(chats = event.chats) }
            }
        }
    }
}
