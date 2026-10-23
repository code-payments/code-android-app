package com.getcode.model.chat

import kotlinx.serialization.Serializable

@Serializable
sealed interface Verb {
    val increasesBalance: Boolean

    @Serializable
    data object Unknown : Verb {
        override val increasesBalance: Boolean = false
    }

    @Serializable
    data object Gave : Verb {
        override val increasesBalance: Boolean = false
    }

    @Serializable
    data object Received : Verb {
        override val increasesBalance: Boolean = true
    }

    @Serializable
    data object Withdrew : Verb {
        override val increasesBalance: Boolean = false
    }

    @Serializable
    data object Deposited : Verb {
        override val increasesBalance: Boolean = true
    }

    @Serializable
    data object Sent : Verb {
        override val increasesBalance: Boolean = false
    }

    @Serializable
    data object Returned : Verb {
        override val increasesBalance: Boolean = true
    }

    @Serializable
    data object Spent : Verb {
        override val increasesBalance: Boolean = false
    }

    @Serializable
    data object Paid : Verb {
        override val increasesBalance: Boolean = false
    }

    @Serializable
    data object Purchased : Verb {
        override val increasesBalance: Boolean = true
    }

    @Serializable
    data object ReceivedTip : Verb {
        override val increasesBalance: Boolean = true
    }

    @Serializable
    data object SentTip : Verb {
        override val increasesBalance: Boolean = false
    }

    companion object
}