package com.flipcash.shared.chat.share

import com.flipcash.services.models.chat.ChatId

/** The chats a share is addressed to, in the order they were picked, capped at [MAX_SHARE_RECIPIENTS]. */
data class ShareRecipientSelection(val ids: List<ChatId> = emptyList()) {

    enum class Toggle { Selected, Deselected, LimitReached }

    val isFull: Boolean get() = ids.size >= MAX_SHARE_RECIPIENTS

    /** Picks [id], or drops it when already picked. A full selection refuses a new pick. */
    fun toggle(id: ChatId): Pair<ShareRecipientSelection, Toggle> = when {
        id in ids -> copy(ids = ids - id) to Toggle.Deselected
        isFull -> this to Toggle.LimitReached
        else -> copy(ids = ids + id) to Toggle.Selected
    }
}
