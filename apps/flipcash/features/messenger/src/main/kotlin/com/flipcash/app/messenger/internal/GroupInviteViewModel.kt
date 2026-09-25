package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.toConversationReference
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The invite sheet's "Recent Chats" (nodes 10330:19387, 10330:19549, 10329:12104): which 1:1 chats
 * are picked, the message to go with the link, and sending both to each.
 *
 * Scoped to the sheet rather than kept on the conversation's view model: a selection means nothing
 * once the sheet is gone, and the send has to outlive nothing but the sheet, which waits for it.
 */
@HiltViewModel
internal class GroupInviteViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
) : ViewModel() {

    data class State(
        /** Null until the feed first emits, so an empty list means there are no 1:1 chats. */
        val recentChats: List<ConversationReference>? = null,
        /** Picked chats in the order they were tapped. The first is where the send lands. */
        val selection: List<ChatId> = emptyList(),
        val message: String = "",
        val sending: Boolean = false,
    ) {
        /** The message bar only comes up once there is someone to send to (node 10330:19549). */
        val showsComposer: Boolean get() = selection.isNotEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // Buffered for the same reason as ReportViewModel.confirmed: it fires once, and dropping it
    // would leave the sheet up after the invites went out.
    private val _invited = Channel<ChatId>(Channel.BUFFERED)

    /** Emits the first-selected chat once every send has answered, which is where the sheet goes. */
    val invited: Flow<ChatId> = _invited.receiveAsFlow()

    init {
        // One read, not a subscription: a chat receiving a message while the sheet is up would
        // otherwise jump to the top and move the row out from under the viewer's finger.
        viewModelScope.launch {
            val summaries = chatCoordinator.feed(ChatType.CONTACT_DM, ChatType.TIP_DM).first()
            val selfId = userManager.accountId
            val chats = summaries.map { it.toConversationReference(selfId, emptyMap(), resources) }
            _state.update { it.copy(recentChats = chats) }
        }
    }

    fun toggle(chatId: ChatId) {
        _state.update { state ->
            if (state.sending) return@update state
            val selection = if (chatId in state.selection) {
                state.selection - chatId
            } else {
                state.selection + chatId
            }
            state.copy(selection = selection)
        }
    }

    fun onMessageChanged(message: String) {
        _state.update { it.copy(message = message) }
    }

    /**
     * Sends [inviteUrl] to every selected chat, then the typed message after it as its own bubble
     * when there is one (node 10330:23177).
     *
     * Each chat is sent to on its own, and one failing does not stop the rest. A failed send leaves
     * a failed row in that chat's transcript with its retry, the same as a failed send from the
     * composer. A chat whose link failed does not get the message: a retry would put the card after
     * the text it was meant to introduce.
     */
    fun invite(inviteUrl: String) {
        val current = _state.value
        val first = current.selection.firstOrNull() ?: return
        if (current.sending) return
        _state.update { it.copy(sending = true) }

        val message = current.message.trim()
        viewModelScope.launch {
            coroutineScope {
                current.selection.map { chatId ->
                    async { sendInvite(chatId, inviteUrl, message) }
                }.awaitAll()
            }
            _invited.send(first)
        }
    }

    private suspend fun sendInvite(chatId: ChatId, inviteUrl: String, message: String) {
        val linkSent = send(chatId, inviteUrl)
        if (linkSent && message.isNotEmpty()) send(chatId, message)
    }

    private suspend fun send(chatId: ChatId, content: String): Boolean {
        val result = try {
            chatCoordinator.sendMessage(chatId, content)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        return result
            .onFailure { trace("group invite failed to send - ${it.localizedMessage}") }
            .isSuccess
    }
}
