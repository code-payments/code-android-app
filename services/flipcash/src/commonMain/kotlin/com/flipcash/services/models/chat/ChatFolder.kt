package com.flipcash.services.models.chat

/**
 * Which folder a chat feed read covers. Mirrors `chat.v1.ChatFolder`.
 *
 * [INBOX] is what the feeds return when the folder is unset. Archived chats come only from an
 * [ARCHIVED] read. A paging token is valid only for the folder that issued it.
 */
enum class ChatFolder {
    INBOX,
    ARCHIVED,
}
