package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId

/**
 * Holds the half-typed message a chat was left with, so backing out of the screen stops being what
 * discards it.
 *
 * Keyed by chat id, including a DM id derived locally by [DmChatResolver.generateChatId] for a chat
 * that does not exist on the server yet — a draft typed at someone never messaged survives the
 * first tip creating the chat, with no key migration. Groups are a chat id like any other.
 *
 * Nothing here reaches the network; a draft is local to the device that typed it.
 */
interface ChatDraftStore {

    /** The draft left in [chatId], or null if there is none. Returned verbatim, untrimmed. */
    suspend fun load(chatId: ChatId): ChatDraft?

    /**
     * Writes [snapshot] as the draft for [chatId], or deletes the row when the snapshot is
     * [ChatDraftSnapshot.isEmpty].
     */
    suspend fun save(chatId: ChatId, snapshot: ChatDraftSnapshot)

    /**
     * [save] for the two callers that cannot suspend and cannot be waited on: `onCleared`, which
     * runs after the ViewModel's scope is cancelled, and the process-lifecycle stop that is the
     * last callback before the app may be killed. Both need the write to outlive the caller, so it
     * goes on this store's own scope.
     */
    fun saveInBackground(chatId: ChatId, snapshot: ChatDraftSnapshot)

    /**
     * Drops [chatId]'s draft. For the chat-level exits — leaving a group, blocking the other side —
     * where the composer is gone along with the reason to keep what was in it.
     */
    suspend fun clear(chatId: ChatId)

    /** Drops every draft. Logout and account switch; there is no TTL beyond this. */
    suspend fun clearAll()
}
