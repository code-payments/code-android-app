package com.getcode.model.protomapping

import com.codeinc.gen.chat.v1.CodeChatService
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

fun Verb.Companion.invoke(proto: CodeChatService.ExchangeDataContent.Verb): Verb {
    return when (proto) {
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.UNKNOWN -> Unknown
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.GAVE -> Gave
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.RECEIVED -> Received
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.WITHDREW -> Withdrew
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.DEPOSITED -> Deposited
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.SENT -> Sent
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.RETURNED -> Returned
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.SPENT -> Spent
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.PAID -> Paid
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.PURCHASED -> Purchased
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.UNRECOGNIZED -> Unknown
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.RECEIVED_TIP -> ReceivedTip
        com.codeinc.gen.chat.v1.CodeChatService.ExchangeDataContent.Verb.SENT_TIP -> SentTip
    }
}