package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One searchable word of a chat member's name or handle, already normalized.
 *
 * Keyed per chat rather than per user, though a user's tokens are the same in every chat they are
 * in: the lookup it serves is "members of this chat whose word starts with q", and with the chat
 * as the leading column of [Index] that is a single range scan. Joining a per-user token table to
 * `chat_members` would scan every user matching the prefix across all chats first.
 *
 * Queried as `token >= q AND token < q || U+10FFFF`, which the index answers without a table scan.
 */
@Entity(
    tableName = "chat_member_search_tokens",
    primaryKeys = ["chat_id_hex", "user_id_hex", "token"],
    indices = [
        Index(
            value = ["chat_id_hex", "token"],
            name = "index_chat_member_search_tokens_chat_id_hex_token",
        ),
    ],
)
data class ChatMemberSearchTokenEntity(
    @ColumnInfo(name = "chat_id_hex") val chatIdHex: String,
    @ColumnInfo(name = "user_id_hex") val userIdHex: String,
    @ColumnInfo(name = "token") val token: String,
)
