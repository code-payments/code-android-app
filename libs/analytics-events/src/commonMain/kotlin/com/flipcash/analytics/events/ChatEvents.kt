package com.flipcash.analytics.events

import com.flipcash.analytics.Amount
import com.flipcash.analytics.ChatType
import com.flipcash.analytics.amount
import com.flipcash.analytics.event

object ChatEvents {
    fun sentMessage(chatType: ChatType, error: String?) = event("Sent Message") {
        text("Chat Type", chatType.value)
        text("Error", error)
    }

    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount).
    fun tipReceived(chatType: ChatType, amount: Amount) = event("Tip Received") {
        text("Chat Type", chatType.value)
        amount(amount)
    }

    fun messageReceived(chatType: ChatType) = event("Message Received") {
        text("Chat Type", chatType.value)
    }
}
