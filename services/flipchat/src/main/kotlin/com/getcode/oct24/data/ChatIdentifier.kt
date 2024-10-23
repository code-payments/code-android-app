package com.getcode.oct24.data

import com.getcode.model.ID

sealed interface ChatIdentifier {
    data class Id(val id: ID): ChatIdentifier
    data class RoomNumber(val number: Long): ChatIdentifier
}