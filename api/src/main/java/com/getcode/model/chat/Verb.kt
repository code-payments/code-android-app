package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService

sealed interface Verb {
    val increasesBalance: Boolean

    data object Unknown : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Gave : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Received : Verb {
        override val increasesBalance: Boolean = true
    }

    data object Withdrew : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Deposited : Verb {
        override val increasesBalance: Boolean = true
    }

    data object Sent : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Returned : Verb {
        override val increasesBalance: Boolean = true
    }

    data object Spent : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Paid : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Purchased : Verb {
        override val increasesBalance: Boolean = true
    }

    data object ReceivedTip : Verb {
        override val increasesBalance: Boolean = true
    }

    data object SentTip : Verb {
        override val increasesBalance: Boolean = false
    }

    companion object {
        operator fun invoke(proto: ChatService.ExchangeDataContent.Verb): Verb {
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
    }
}