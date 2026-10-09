package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.chatListFeed
import com.flipcash.shared.chat.share.PendingChatShares
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.toConversationReference
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private var chosen = false

    init {
        viewModelScope.launch {
            val selfId = userManager.accountId
            _chats.value = chatCoordinator.chatListFeed().first()
                .map { it.toConversationReference(selfId, emptyMap(), resources) }
        }
    }

    /** Routes the share to [chatId]. False when there is no share left to route. */
    fun choose(chatId: ChatId): Boolean {
        val routed = shares.route(chatId)
        chosen = routed
        return routed
    }

    override fun onCleared() {
        // Left without choosing (back, close): nothing was asked for, so the share is dropped and
        // its copied images with it. Cleared rather than on dispose, so a rotation keeps it.
        if (!chosen) shares.discard()
    }
}
