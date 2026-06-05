package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.flipcash.app.persistence.converters.MessagePointerSerialized
import com.flipcash.app.persistence.converters.UserProfileSerialized

@Entity(
    tableName = "chat_members",
    primaryKeys = ["chat_id_hex", "user_id_hex"],
)
data class ChatMemberEntity(
    @ColumnInfo(name = "chat_id_hex") val chatIdHex: String,
    @ColumnInfo(name = "user_id_hex") val userIdHex: String,
    @ColumnInfo(name = "user_profile_json") val userProfileJson: UserProfileSerialized?,
    @ColumnInfo(name = "pointers_json") val pointersJson: List<MessagePointerSerialized>?,
)
