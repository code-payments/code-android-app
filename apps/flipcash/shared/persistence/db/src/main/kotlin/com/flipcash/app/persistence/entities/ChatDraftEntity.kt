package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The half-typed message left behind in a chat, so leaving the screen stops being what discards it.
 *
 * Keyed by chat id rather than by a row id: there is one draft per conversation, and the id a
 * TIP_DM derives locally is the same one the chat gets when the first tip creates it, so a draft
 * typed at someone never messaged before needs no key migration to survive.
 *
 * [replyTargetJson] is the reply strip's own snapshot of the message it cites, serialized by
 * `:apps:flipcash:shared:chat` rather than by a type converter here. The strip has to render
 * without the cited message being loaded, so what it stores is a copy, not a pointer into
 * `chat_messages` — and keeping the shape in the module that owns it leaves this table free of
 * composer types.
 *
 * A row is only ever present when there is something to restore: a draft whose text trims to empty
 * with no reply target is deleted rather than stored as `""`, so a chat typed in once and cleared
 * restores nothing instead of an empty draft forever.
 */
@Entity(tableName = "chat_draft")
data class ChatDraftEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "reply_target_json")
    val replyTargetJson: String?,

    @ColumnInfo(name = "saved_at")
    val savedAt: Long,
)
