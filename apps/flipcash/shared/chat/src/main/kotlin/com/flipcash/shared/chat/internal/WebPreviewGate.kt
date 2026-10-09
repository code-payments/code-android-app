package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType

/**
 * Whether a chat's web links may be fetched now: it is the chat on screen, and the viewer may read
 * it. `activeChat` is null while the app is backgrounded, so a push that arrives then is refused.
 *
 * A DM is always allowed and its membership is not read. Only a group has non-members who can be
 * looking at a preview, so only a group is checked. A chat whose type is not stored yet is refused:
 * it may be a group.
 */
internal class WebPreviewGate(
    private val stateHolder: ChatStateHolder,
    private val metadata: ChatMetadataDataSource,
) {
    suspend fun allows(chatId: ChatId): Boolean {
        if (stateHolder.current.activeChat != chatId) return false
        return when (metadata.getChatType(chatId)) {
            ChatType.CONTACT_DM, ChatType.TIP_DM -> true
            ChatType.GROUP -> metadata.isMember(chatId)
            ChatType.UNKNOWN -> false
        }
    }
}
