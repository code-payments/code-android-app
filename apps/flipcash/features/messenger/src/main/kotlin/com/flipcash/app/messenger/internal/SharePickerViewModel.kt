package com.flipcash.app.messenger.internal

import com.getcode.opencode.exchange.Exchange
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.chatListFeed
import android.net.Uri
import com.flipcash.shared.chat.share.ChatShareGateway
import com.flipcash.shared.chat.share.MAX_SHARE_RECIPIENTS
import com.flipcash.shared.chat.share.ShareRecipientSelection
import com.flipcash.shared.chat.share.PendingChatShare
import com.flipcash.shared.chat.share.ShareSendProgress
import com.flipcash.shared.chat.share.ShareSender
import com.flipcash.shared.chat.share.ShareSending
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The chats a share from another app can go to, and the send itself. Lists the Chats tab's own set,
 * keeps the picked chats (up to [MAX_SHARE_RECIPIENTS], in pick order) and the message, and sends
 * through [ShareSender], which uses the same path as typing the content in each chat.
 */
@HiltViewModel
internal class SharePickerViewModel @Inject constructor(
    private val exchange: Exchange,
    chatCoordinator: ChatCoordinator,
    userManager: UserManager,
    resources: ResourceHelper,
    private val shares: PendingChatShares,
    gateway: ChatShareGateway,
) : ViewModel() {

    private val sender = ShareSender(gateway)

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

    /** The share being sent, held for the whole life of the picker. */
    private val held: PendingChatShare? = shares.pending.value

    /** False when there is no share to send, as after the process was restored; the screen closes. */
    val hasShare: Boolean = held != null

    /** The images to show and send. */
    val images: List<Uri> = held?.images.orEmpty()

    /** Images past the limit that were left out. */
    val droppedImageCount: Int = held?.droppedImageCount ?: 0

    /** The chat the sender suggested, offered first, or null. */
    val suggested: ChatId? = held?.chatId

    /** The message: starts as the text that was shared and is what goes with the images. */
    val messageState = TextFieldState(held?.text.orEmpty())

    private val _selected = MutableStateFlow<List<ChatId>>(emptyList())

    /** The chats the share will go to, in pick order. */
    val selected: StateFlow<List<ChatId>> = _selected.asStateFlow()

    val progress: StateFlow<ShareSendProgress?> = sender.progress

    val sending: StateFlow<ShareSending?> = sender.sending

    private val _done = MutableStateFlow(false)

    /** True once every chat has the share, so the screen can close. */
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private val _limitHits = MutableStateFlow(0)

    /** Counts taps on a row once the selection was full, for haptic feedback. */
    val limitHits: StateFlow<Int> = _limitHits.asStateFlow()

    private var started = false
    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            val selfId = userManager.accountId
            chatCoordinator.chatListFeed().collect { summaries ->
                val fresh = summaries.map { it.toConversationReference(selfId, emptyMap(), resources, exchange = exchange) }
                // The first list is shown as it arrives. Later ones (the feed keeps syncing) keep
                // the rows already on screen where they are, so nothing moves under a finger.
                val first = _chats.value == null
                _chats.value = fresh.mergeStable(_chats.value) { it.chatId }
                if (first && suggested != null && fresh.any { it.chatId == suggested }) select(suggested)
            }
        }
    }

    /** Whether there is something to send and somewhere to send it, and no send under way. */
    fun canSend(selected: List<ChatId>, sending: Boolean, progress: ShareSendProgress?, hasMessage: Boolean): Boolean {
        if (sending || selected.isEmpty()) return false
        if (progress?.isStuck == true) return false
        return images.isNotEmpty() || hasMessage
    }

    private fun select(chatId: ChatId) {
        if (chatId !in _selected.value) _selected.update { ShareRecipientSelection(it).toggle(chatId).first.ids }
    }

    /** Picks [chatId], or drops it when already picked. A full selection refuses new picks. */
    fun toggle(chatId: ChatId) {
        if (sender.sending.value != null) return
        val (next, result) = ShareRecipientSelection(_selected.value).toggle(chatId)
        if (result == ShareRecipientSelection.Toggle.LimitReached) {
            _limitHits.update { it + 1 }
            return
        }
        _selected.value = next.ids
        // The set changed under a failed send; the next send starts from the new set.
        if (sender.progress.value?.failed?.isNotEmpty() == true) sender.reset()
    }

    /** Sends to every picked chat that hasn't got the share, and closes once all have. */
    fun send() {
        if (sendJob?.isActive == true) return
        val recipients = _selected.value
        if (recipients.isEmpty()) return
        started = true
        val message = messageState.text.toString()
        sendJob = viewModelScope.launch {
            if (sender.send(recipients, images, message)) {
                // Past this point every image has been encoded and posted, so the copies can go.
                shares.discard()
                _done.value = true
            }
        }
    }

    override fun onCleared() {
        // Left without sending (back, close): nothing was asked for, so the share is dropped and
        // its copied images with it. Once a send started the images belong to it, so they stay.
        if (_done.value) return
        if (started) shares.release() else shares.discard()
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
