package com.getcode.opencode.internal.transactors

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.Fiat
import javax.inject.Inject

/**
 * The output of [PayloadFactory.create] — a rendezvous key pair derived from
 * the payload and the encoded scannable code data.
 */
data class PayloadResult(
    val rendezvous: KeyPair,
    val codeData: List<Byte>,
)

/**
 * Creates an [OpenCodePayload][com.getcode.opencode.model.core.OpenCodePayload]
 * from the given parameters and returns the derived rendezvous key pair and
 * encoded code data. Extracted as a functional interface for testability.
 */
fun interface PayloadFactory {
    fun create(kind: PayloadKind, value: Fiat, nonce: List<Byte>): PayloadResult
}

class DefaultPayloadFactory @Inject constructor() : PayloadFactory {
    override fun create(kind: PayloadKind, value: Fiat, nonce: List<Byte>): PayloadResult {
        val payload = OpenCodePayload(kind, value, nonce)
        return PayloadResult(payload.rendezvous, payload.codeData.toList())
    }
}
