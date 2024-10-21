package com.getcode.oct24.model.chat

import com.getcode.model.ID
import com.getcode.model.chat.Pointer
import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val id: ID,
    val isSelf: Boolean,
    val identity: MemberIdentity?,
    val pointers: List<Pointer>,
)

@Serializable
data class MemberIdentity(
    val displayName: String,
    val imageUrl: String?,
)