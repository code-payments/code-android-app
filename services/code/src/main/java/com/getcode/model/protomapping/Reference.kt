package com.getcode.model.protomapping

import com.codeinc.gen.chat.v2.ChatService.ExchangeDataContent
import com.codeinc.gen.chat.v2.ChatService.ExchangeDataContent.ReferenceCase
import com.getcode.model.chat.Reference
import com.getcode.model.chat.Reference.IntentId
import com.getcode.model.chat.Reference.NoneSet
import com.getcode.model.chat.Reference.Signature

operator fun Reference.Companion.invoke(proto: ExchangeDataContent): Reference {
    return when (proto.referenceCase) {
        ReferenceCase.INTENT -> IntentId(proto.intent.toByteArray().toList())
        ReferenceCase.SIGNATURE -> Signature(com.getcode.solana.keys.Signature(proto.signature.toByteArray().toList()))
        ReferenceCase.REFERENCE_NOT_SET -> NoneSet
        null -> NoneSet
    }
}