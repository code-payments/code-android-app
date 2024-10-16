package com.getcode.model.protomapping

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.Verb
import com.getcode.model.chat.Verb.Deposited
import com.getcode.model.chat.Verb.Gave
import com.getcode.model.chat.Verb.Paid
import com.getcode.model.chat.Verb.Purchased
import com.getcode.model.chat.Verb.Received
import com.getcode.model.chat.Verb.ReceivedTip
import com.getcode.model.chat.Verb.Returned
import com.getcode.model.chat.Verb.Sent
import com.getcode.model.chat.Verb.SentTip
import com.getcode.model.chat.Verb.Spent
import com.getcode.model.chat.Verb.Unknown
import com.getcode.model.chat.Verb.Withdrew

operator fun Verb.Companion.invoke(proto: com.getcode.model.chat.VerbV2): Verb {
    return when (proto) {
        ChatService.ExchangeDataContent.Verb.UNKNOWN -> Unknown
        ChatService.ExchangeDataContent.Verb.GAVE -> Gave
        ChatService.ExchangeDataContent.Verb.RECEIVED -> Received
        ChatService.ExchangeDataContent.Verb.WITHDREW -> Withdrew
        ChatService.ExchangeDataContent.Verb.DEPOSITED -> Deposited
        ChatService.ExchangeDataContent.Verb.SENT -> Sent
        ChatService.ExchangeDataContent.Verb.RETURNED -> Returned
        ChatService.ExchangeDataContent.Verb.SPENT -> Spent
        ChatService.ExchangeDataContent.Verb.PAID -> Paid
        ChatService.ExchangeDataContent.Verb.PURCHASED -> Purchased
        ChatService.ExchangeDataContent.Verb.UNRECOGNIZED -> Unknown
        ChatService.ExchangeDataContent.Verb.RECEIVED_TIP -> ReceivedTip
        ChatService.ExchangeDataContent.Verb.SENT_TIP -> SentTip
    }
}

fun Verb.Companion.fromV1(proto: com.getcode.model.chat.VerbV1): Verb {
    return when (proto) {
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.UNKNOWN -> Unknown
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.GAVE -> Gave
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.RECEIVED -> Received
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.WITHDREW -> Withdrew
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.DEPOSITED -> Deposited
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.SENT -> Sent
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.RETURNED -> Returned
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.SPENT -> Spent
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.PAID -> Paid
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.PURCHASED -> Purchased
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.UNRECOGNIZED -> Unknown
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.RECEIVED_TIP -> ReceivedTip
        com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb.SENT_TIP -> SentTip
    }
}