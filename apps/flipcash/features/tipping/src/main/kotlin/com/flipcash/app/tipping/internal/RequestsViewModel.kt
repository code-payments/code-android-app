package com.flipcash.app.tipping.internal

import com.getcode.opencode.exchange.Exchange
import androidx.lifecycle.viewModelScope
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.HostLobbies
import com.flipcash.shared.chat.LobbyRequests
import com.flipcash.shared.chat.chatListFeeds
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.toConversationReference
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Backs the Requests screen: join requests in both directions. Private groups the viewer hosts with
 * people waiting, and groups the viewer has asked to join.
 */
@HiltViewModel
internal class RequestsViewModel @Inject constructor(
    private val exchange: Exchange,
    chatCoordinator: ChatCoordinator,
    private val chatController: ChatController,
    userManager: UserManager,
    resources: ResourceHelper,
    private val lobbyRequests: LobbyRequests,
    private val hostLobbies: HostLobbies,
    dispatchers: DispatcherProvider,
) : BaseViewModel<RequestsViewModel.State, RequestsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        val hosted: List<HostedRequest> = emptyList(),
        val sent: List<SentRequest> = emptyList(),
        // False until every sent request's group has been asked about, so the empty message does
        // not flash over a Sent row that is about to arrive.
        val isLoaded: Boolean = false,
    ) {
        val isEmpty: Boolean get() = hosted.isEmpty() && sent.isEmpty()
    }

    sealed interface Event {
        data class Updated(val hosted: List<HostedRequest>, val sent: List<SentRequest>, val isLoaded: Boolean) : Event
    }

    // Groups the viewer asked to join are not in the feed, so each one's metadata is fetched here.
    // A failed fetch is remembered so the screen still settles, with that group left out.
    private val fetched = MutableStateFlow<Map<ChatId, ChatMetadata?>>(emptyMap())

    init {
        // Drops requests that were answered while the app was away, as the Chats list does.
        viewModelScope.launch { lobbyRequests.refresh() }

        val selfId = { userManager.accountId }
        fun reference(metadata: ChatMetadata) =
            ChatSummary(metadata, unreadCount = 0).toConversationReference(selfId(), emptyMap(), resources, exchange = exchange)

        val hostedGroups = chatCoordinator.chatListFeeds()
            .map { feeds ->
                val all = feeds.main + feeds.archived
                val ids = hostedPrivateGroups(all, selfId()).keys
                all.map { it.metadata }.filter { it.chatId in ids }.associateBy { it.chatId }
            }
            .distinctUntilChanged()

        // Fresh counts on open; LobbyUpdate only reaches a running stream.
        viewModelScope.launch {
            hostedGroups.first().keys.forEach { chatId -> launch { hostLobbies.load(chatId) } }
        }

        lobbyRequests.observe()
            .onEach { requests ->
                requests.keys.filter { it !in fetched.value }.forEach { chatId ->
                    viewModelScope.launch {
                        val metadata = chatController.getChat(chatId).getOrNull()
                        fetched.update { it + (chatId to metadata) }
                    }
                }
            }
            .launchIn(viewModelScope)

        combine(hostedGroups, hostLobbies.state, lobbyRequests.observe(), fetched) { hosted, lobbies, requests, fetched ->
            val hostedRows = hostedRequests(hosted.keys, lobbies).mapNotNull { (chatId, waiting) ->
                hosted[chatId]?.let { HostedRequest(reference(it), waiting.size) }
            }
            val sentRows = sentRequests(requests, fetched.filterValues { it != null }.mapValues { it.value!! })
                .map { (metadata, at) -> SentRequest(reference(metadata), at) }
            Event.Updated(hostedRows, sentRows, isLoaded = requests.keys.all { it in fetched })
        }
            .flowOn(dispatchers.Default)
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        internal val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.Updated -> { state ->
                    state.copy(hosted = event.hosted, sent = event.sent, isLoaded = event.isLoaded)
                }
            }
        }
    }
}

/** A private group the viewer hosts, with how many people wait to join it. */
internal data class HostedRequest(val chat: ConversationReference, val count: Int)

/** A private group the viewer asked to join, with when the request went in. */
internal data class SentRequest(val chat: ConversationReference, val requestedAt: Instant)

/**
 * The groups the viewer asked to join, newest request first. A group whose metadata isn't held is
 * left out until it is fetched.
 */
internal fun sentRequests(
    requests: Map<ChatId, Instant>,
    metadata: Map<ChatId, ChatMetadata>,
): List<Pair<ChatMetadata, Instant>> = requests
    .mapNotNull { (chatId, at) -> metadata[chatId]?.let { it to at } }
    .sortedByDescending { (_, at) -> at }
