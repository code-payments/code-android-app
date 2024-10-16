package com.getcode.model

import com.getcode.model.chat.MessageStatus
import com.getcode.utils.timestamp
import java.util.UUID

data class PointerStatus(
    val messageId: UUID,
    val memberId: ID,
    val messageStatus: MessageStatus,
) {

    val timestamp: Long?
        get() = messageId.timestamp
}