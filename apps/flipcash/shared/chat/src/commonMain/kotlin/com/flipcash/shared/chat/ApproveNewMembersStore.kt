package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.flow.Flow

/**
 * The host sheet's Approve new members switch, per group, on this device only (spec surface 1).
 * On, the host's link card shows the plain `/chat/{uuid}` link; off, the current invite link.
 * Turning it on revokes nothing.
 */
interface ApproveNewMembersStore {
    /** Whether the switch is on for [chatId] for the signed-in account; off until set. */
    fun observe(chatId: ChatId): Flow<Boolean>

    suspend fun set(chatId: ChatId, on: Boolean)
}
