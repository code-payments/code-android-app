package com.flipcash.app.messenger.internal.invites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.HostInvites
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The host's way into invite links, for the screens that hand the group out: whether the viewer
 * can invite (`can_invite`), and the three-row sheet that opens from the profile's share circle
 * and Join Requests' empty state (frame 1a).
 *
 * Kept out of the conversation's view model: nothing here is about the transcript.
 */
@HiltViewModel
internal class InviteHostViewModel @Inject constructor(
    private val hostInvites: HostInvites,
    private val resources: ResourceHelper,
) : ViewModel() {

    /** Each checked chat's `can_invite`; a chat not checked yet reads as false. */
    val canInvite: StateFlow<Map<ChatId, Boolean>> = hostInvites.canInvite

    /** Asks the server whether the viewer can invite to [chatId]. A failure keeps the last answer. */
    fun refresh(chatId: ChatId) {
        viewModelScope.launch { hostInvites.refreshAccess(chatId) }
    }

    /** Frame 1a: see [showInviteHostSheet]. */
    fun presentHostSheet(
        groupName: String,
        onShareInviteLink: () -> Unit,
        onShareJoinLink: () -> Unit,
        onManageInvites: () -> Unit,
    ) = showInviteHostSheet(resources, groupName, onShareInviteLink, onShareJoinLink, onManageInvites)
}
