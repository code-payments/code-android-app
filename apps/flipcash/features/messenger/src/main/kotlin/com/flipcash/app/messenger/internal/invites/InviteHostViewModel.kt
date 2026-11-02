package com.flipcash.app.messenger.internal.invites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.HostInvites
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Whether the viewer can invite (`can_invite`), for the screens that hand the group out: a host
 * gets the link card sheet ([com.flipcash.app.core.chat.ChatStep.InviteHost]), a member the plain link.
 *
 * Kept out of the conversation's view model: nothing here is about the transcript.
 */
@HiltViewModel
internal class InviteHostViewModel @Inject constructor(
    private val hostInvites: HostInvites,
) : ViewModel() {

    /** Each checked chat's `can_invite`; a chat not checked yet reads as false. */
    val canInvite: StateFlow<Map<ChatId, Boolean>> = hostInvites.canInvite

    /** Asks the server whether the viewer can invite to [chatId]. A failure keeps the last answer. */
    fun refresh(chatId: ChatId) {
        viewModelScope.launch { hostInvites.refreshAccess(chatId) }
    }
}
