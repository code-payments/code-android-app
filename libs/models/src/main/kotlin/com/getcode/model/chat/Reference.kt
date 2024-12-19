package com.getcode.model.chat

import com.getcode.model.ID
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * An ID that can be referenced to the source of the exchange of Kin
 */
@Serializable
sealed interface Reference {
    @Serializable
    data object NoneSet: Reference
    @Serializable
    data class IntentId(val id: ID): Reference
    @Serializable
    data class Signature(val bytes: List<Byte>): Reference {
        @Transient
        val signature = com.getcode.solana.keys.Signature(bytes)
    }

    companion object
}
