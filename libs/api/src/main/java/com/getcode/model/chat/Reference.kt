package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService.ExchangeDataContent
import com.codeinc.gen.chat.v2.ChatService.ExchangeDataContent.ReferenceCase
import com.getcode.model.ID
import com.getcode.solana.keys.Signature

/**
 * An ID that can be referenced to the source of the exchange of Kin
 */
sealed interface Reference {
    data object NoneSet: Reference
    data class IntentId(val id: ID): Reference
    data class Signature(val signature: com.getcode.solana.keys.Signature): Reference

    companion object {
        operator fun invoke(proto: ExchangeDataContent): Reference {
            return when (proto.referenceCase) {
                ReferenceCase.INTENT -> IntentId(proto.intent.toByteArray().toList())
                ReferenceCase.SIGNATURE -> Signature(Signature(proto.signature.toByteArray().toList()))
                ReferenceCase.REFERENCE_NOT_SET -> NoneSet
                null -> NoneSet
            }
        }
    }
}
