package com.getcode.oct24.domain.model.chat

import androidx.room.Entity
import androidx.room.Ignore
import com.getcode.model.ID
import com.getcode.vendor.Base58
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "members",
    primaryKeys = ["memberIdBase58", "conversationIdBase58"]
)
data class ConversationMember(
    val memberIdBase58: String, // Server-provided ID in base58 string format
    val conversationIdBase58: String, // Foreign key to `Conversation`
    val memberName: String?, // Other member-specific fields
    val imageUri: String?,
) {
    @Ignore
    val id: ID = Base58.decode(memberIdBase58).toList()

    @Ignore
    val conversationId: ID = Base58.decode(conversationIdBase58).toList()
}