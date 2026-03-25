package com.getcode.opencode.internal.transactors

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.Fiat
import javax.inject.Inject

data class PayloadResult(
    val rendezvous: KeyPair,
    val codeData: List<Byte>,
)

fun interface PayloadFactory {
    fun create(kind: PayloadKind, value: Fiat, nonce: List<Byte>): PayloadResult
}

class DefaultPayloadFactory @Inject constructor() : PayloadFactory {
    override fun create(kind: PayloadKind, value: Fiat, nonce: List<Byte>): PayloadResult {
        val payload = OpenCodePayload(kind, value, nonce)
        return PayloadResult(payload.rendezvous, payload.codeData.toList())
    }
}
