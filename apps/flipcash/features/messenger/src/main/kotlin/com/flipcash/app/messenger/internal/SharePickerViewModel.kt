package com.flipcash.app.messenger.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.chatListFeed
import com.flipcash.shared.chat.share.PendingChatShare
import com.flipcash.shared.chat.share.PendingChatShares
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.toConversationReference
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The chats a share from another app can go to. Lists the Chats tab's own set and, on a pick,
 * routes the waiting [PendingChatShares] share to that chat; the chat claims it when it opens.
 */
@HiltViewModel
internal class SharePickerViewModel @Inject constructor(
    chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    resources: ResourceHelper,
    private val shares: PendingChatShares,
) : ViewModel() {

    private val _chats = MutableStateFlow<List<ConversationReference>?>(null)

    /** Null until the feed first emits, so empty means there are no chats to offer. */
    val chats: StateFlow<List<ConversationReference>?> = _chats.asStateFlow()

    /** What the search field holds; the list below it is [chats] narrowed by this. */
    val queryState = TextFieldState()

    private val query = snapshotFlow { queryState.text.toString() }

    /** [chats] narrowed by the search field. Null until the list is known. */
    val visibleChats: StateFlow<List<ConversationReference>?> =
        combine(_chats, query) { all, q -> all?.filterByQuery(q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The share being sent, for the compose bar's preview and message. Null once it is gone. */
    val share: StateFlow<PendingChatShare?> = shares.pending

    private val _selected = MutableStateFlow<ChatId?>(null)

    /** The chat the share will go to, or null while none is picked. */
    val selected: StateFlow<ChatId?> = _selected.asStateFlow()

    private var chosen = false

    init {
        viewModelScope.launch {
            val selfId = userManager.accountId
            chatCoordinator.chatListFeed().collect { summaries ->
                val fresh = summaries.map { it.toConversationReference(selfId, emptyMap(), resources) }
                // The first list is shown as it arrives. Later ones (the feed keeps syncing) keep
                // the rows already on screen where they are, so nothing moves under a finger.
                _chats.value = fresh.mergeStable(_chats.value) { it.chatId }
            }
        }
    }

    /** Picks [chatId], or clears the pick when it is already the one picked. */
    fun toggle(chatId: ChatId) {
        _selected.update { if (it == chatId) null else chatId }
    }

    /**
     * Routes the share to the picked chat with [message] as its text, replacing what was shared.
     * False when nothing is picked or there is no share left to route.
     */
    fun confirm(message: String): Boolean {
        val chatId = _selected.value ?: return false
        val routed = shares.route(chatId, message)
        chosen = routed
        return routed
    }

    override fun onCleared() {
        // Left without choosing (back, close): nothing was asked for, so the share is dropped and
        // its copied images with it. Cleared rather than on dispose, so a rotation keeps it.
        if (!chosen) shares.discard()
    }
}

/**
 * This list with the order of [previous] kept: rows still present stay in their old positions with
 * their new contents, rows that went are dropped, and new ones are appended in their own order.
 * With no [previous] it is this list unchanged.
 */
internal fun <T, K> List<T>.mergeStable(previous: List<T>?, key: (T) -> K): List<T> {
    if (previous == null) return this
    val byKey = associateBy(key)
    val kept = previous.mapNotNull { byKey[key(it)] }
    val keptKeys = kept.mapTo(HashSet(), key)
    return kept + filterNot { key(it) in keptKeys }
}

/**
 * The chats whose name contains [query], ignoring case and surrounding space. A blank query keeps
 * them all.
 */
internal fun List<ConversationReference>.filterByQuery(query: String): List<ConversationReference> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { it.name.orEmpty().contains(needle, ignoreCase = true) }
}
