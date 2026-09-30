package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Relation
import com.flipcash.app.persistence.converters.MessagePointerSerialized

@Entity(
    tableName = "chat_members",
    primaryKeys = ["chat_id_hex", "user_id_hex"],
)
data class ChatMemberEntity(
    @ColumnInfo(name = "chat_id_hex") val chatIdHex: String,
    @ColumnInfo(name = "user_id_hex") val userIdHex: String,
    @ColumnInfo(name = "pointers_json") val pointersJson: List<MessagePointerSerialized>?,
    // The roster version the member joined at: `Member.version`. The merge key across roster pages
    // and stream updates (greater wins), and what a full roster read checks before dropping a
    // member it did not see. Zero for chat-creation joins, DM participants, and rows from before
    // it was stored.
    @ColumnInfo(name = "version", defaultValue = "0") val version: Long = 0,
)

/**
 * A chat member row joined to the shared, normalized [UserProfileEntity]. [profile] is
 * null until the member's profile has been synced (or, transiently, before the v26
 * migration backfill runs — the read mapper falls back to the staged blob in that case).
 */
data class ChatMemberWithProfile(
    @Embedded val member: ChatMemberEntity,
    @Relation(parentColumn = "user_id_hex", entityColumn = "user_id_hex")
    val profile: UserProfileEntity?,
)
