package xyz.flipchat.services.data

import com.getcode.model.ID

sealed interface ChatIdentifier {
    data class Id(val roomId: ID): ChatIdentifier
    data class RoomNumber(val number: Long): ChatIdentifier
}