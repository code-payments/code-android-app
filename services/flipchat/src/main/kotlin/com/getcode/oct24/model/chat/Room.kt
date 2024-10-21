package com.getcode.oct24.model.chat

import com.getcode.model.ID
import com.getcode.model.chat.ChatType
import kotlinx.serialization.Serializable

@Serializable
data class Room(
    val id: ID,
    val type: ChatType,
    val title: String?,
    val roomNumber: Long = -1,
    val members: List<Member>,
    val muted: Boolean,
    val muteable: Boolean,
    private val _unread: Int
)
