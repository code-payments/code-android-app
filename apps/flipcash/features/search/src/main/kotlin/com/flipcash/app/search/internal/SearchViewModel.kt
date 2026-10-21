package com.flipcash.app.search.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.chatListFeeds
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.toConversationReference
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs Search. The query lives in [query] and nowhere else, so it is gone once the screen is.
 * Messages are read from the database once per visit; chats, recents and profiles stay live.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
internal class SearchViewModel @Inject constructor(
    chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    tokenCoordinator: TokenCoordinator,
    resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    messageDataSource: ChatMessageDataSource,
    private val currencySearch: CurrencySearch,
    private val recentStore: RecentSearchStore,
) : ViewModel() {

    val query = TextFieldState()

    private val _chip = MutableStateFlow(SearchChip.All)
    val chip: StateFlow<SearchChip> = _chip.asStateFlow()

    private data class Directory(
        val chats: List<ConversationReference>,
        val profiles: Map<String, UserProfile>,
    )

    private data class Transcripts(
        val messages: List<StoredMessage>,
        val links: List<SharedLink>,
        val currencyShares: Map<String, StoredMessage>,
    )

    private val directory = combine(
        chatCoordinator.chatListFeeds(),
        tokenCoordinator.tokens,
        chatCoordinator.observeSenderProfiles(),
    ) { feeds, tokens, profiles ->
        val tokensByMint = tokens.associateBy { it.address }
        val selfId = userManager.accountId
        Directory(
            chats = (feeds.main + feeds.archived)
                .map { it.toConversationReference(selfId, tokensByMint, resources, profiles) },
            profiles = profiles,
        )
    }
        // Messages, links and currencies don't need the chat list, so they don't wait for it.
        .onStart { emit(Directory(chats = emptyList(), profiles = emptyMap())) }
        .flowOn(dispatchers.Default)

    private val transcripts = MutableStateFlow(Transcripts(emptyList(), emptyList(), emptyMap()))

    /** The chats opened from Search, newest first. Chats no longer in the list drop out. */
    val recents: StateFlow<List<ConversationReference>> =
        combine(recentStore.recents, directory) { ids, directory ->
            val byId = directory.chats.associateBy { it.chatId }
            ids.mapNotNull(byId::get)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * The query (null while blank), with the currencies it finds. Currencies come first as an
     * empty list, so a slow Discover holds back only the Currencies section.
     */
    private val currencyHits = snapshotFlow { query.text.toString() }
        .map(SearchMatching::query)
        .distinctUntilChanged()
        .debounce { if (it == null) 0 else QUERY_DEBOUNCE_MS }
        .transformLatest { query ->
            emit(query to emptyList<Token>())
            if (query != null) emit(query to currencySearch.search(query))
        }

    /** What the query finds, or null while it is blank and Search shows Recent. */
    val results: StateFlow<SearchResults?> = combine(
        currencyHits,
        directory,
        transcripts,
    ) { (query, currencies), directory, transcripts ->
        query ?: return@combine null
        buildResults(
            query = query,
            chats = directory.chats,
            messages = transcripts.messages,
            links = transcripts.links,
            currencies = currencies,
            currencyShares = transcripts.currencyShares,
            senderName = { id -> directory.profiles.nameOf(id) },
        )
    }
        .flowOn(dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // A cleared query starts the next search from All, whichever chip the last one ended on.
        viewModelScope.launch {
            snapshotFlow { SearchMatching.query(query.text.toString()) == null }
                .distinctUntilChanged()
                .filter { it }
                .collect { _chip.value = SearchChip.All }
        }
        viewModelScope.launch {
            val messages = withContext(dispatchers.IO) {
                messageDataSource.getSearchable(MESSAGE_LIMIT)
                    .map { (chatIdHex, message) -> StoredMessage(ChatId(chatIdHex), message) }
            }
            transcripts.value = withContext(dispatchers.Default) {
                Transcripts(
                    messages = messages,
                    links = collectLinks(messages),
                    currencyShares = latestCurrencyShares(messages),
                )
            }
        }
    }

    fun selectChip(chip: SearchChip) = _chip.update { chip }

    /** Records [chatId] as a recent before the screen opens it. */
    fun opened(chatId: ChatId) {
        viewModelScope.launch { recentStore.record(chatId) }
    }

    private fun Map<String, UserProfile>.nameOf(id: ID): String? =
        get(id.hexEncodedString())?.displayName?.takeIf { it.isNotBlank() }

    private companion object {
        // Enough history to search without holding every message ever received in memory.
        const val MESSAGE_LIMIT = 5_000
        const val QUERY_DEBOUNCE_MS = 150L
    }
}
