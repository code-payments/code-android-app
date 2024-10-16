package com.getcode.model.chat

import com.codeinc.gen.chat.v1.ChatService as ChatServiceV1
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

    companion object
}