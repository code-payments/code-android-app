package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat

sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Cash(val intentId: ID, val amount: Fiat) : MessageContent
}
