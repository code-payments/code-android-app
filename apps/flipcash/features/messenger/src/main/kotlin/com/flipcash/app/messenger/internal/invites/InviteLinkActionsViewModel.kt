package com.flipcash.app.messenger.internal.invites

import androidx.lifecycle.ViewModel
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Frame 2b's lookup: the invite a row sheet was opened on, from the invites Manage Invites has
 * already loaded. The step carries only the invite's id, so the sheet finds the rest here.
 */
@HiltViewModel
internal class InviteLinkActionsViewModel @Inject constructor(
    private val hostInvites: HostInvites,
) : ViewModel() {

    fun invite(chatId: ChatId, inviteIdHex: String): HostInvite? =
        hostInvites.state.value[chatId].orEmpty().firstOrNull { it.invite.id.toHex() == inviteIdHex }
}
