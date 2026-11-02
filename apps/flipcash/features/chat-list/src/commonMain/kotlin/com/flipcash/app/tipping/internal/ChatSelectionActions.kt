package com.flipcash.app.tipping.internal

import com.flipcash.services.models.chat.isMutedAt
import com.flipcash.shared.chat.ChatMuteToggle
import com.flipcash.shared.chat.ChatReadToggle
import com.flipcash.shared.chat.bulkMuteToggle
import com.flipcash.shared.chat.bulkReadToggle
import com.flipcash.shared.chat.ui.ConversationReference

/**
 * What select mode's bar offers for the selected rows: Mark as Read when any is unread (else Mark
 * as Unread), and Unmute when every one is muted (else Mute). The rules are the shared ones.
 */
data class ChatSelectionActions(
    val count: Int,
    val read: ChatReadToggle,
    val mute: ChatMuteToggle,
) {
    companion object {
        fun of(selected: List<ConversationReference>) = ChatSelectionActions(
            count = selected.size,
            read = bulkReadToggle(selected.map { it.isUnread }),
            mute = bulkMuteToggle(selected.map { it.viewerState.isMutedAt() }),
        )
    }
}
