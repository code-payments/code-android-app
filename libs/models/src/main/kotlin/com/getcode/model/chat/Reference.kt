package com.getcode.model.chat

import com.getcode.model.ID

/**
 * An ID that can be referenced to the source of the exchange of Kin
 */
sealed interface Reference {
    data object NoneSet: Reference
    data class IntentId(val id: ID): Reference
    data class Signature(val signature: com.getcode.solana.keys.Signature): Reference

    companion object
}
