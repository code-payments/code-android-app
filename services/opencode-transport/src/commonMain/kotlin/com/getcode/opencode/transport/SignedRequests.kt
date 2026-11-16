package com.getcode.opencode.transport

import com.codeinc.opencode.wire.account.v1.GetTokenAccountInfosRequest
import com.codeinc.opencode.wire.account.v1.IsOcpAccountRequest
import com.codeinc.opencode.wire.common.v1.Signature
import com.codeinc.opencode.wire.common.v1.SolanaAccountId
import com.getcode.ed25519kmp.Ed25519
import com.squareup.wire.Message
import okio.ByteString.Companion.toByteString

/**
 * Signed OCP requests built from Wire messages.
 *
 * Same scheme as Android's `sign()` in `:services:opencode`: serialize the request with every
 * signature field unset, sign those bytes with Ed25519, then set the signature fields.
 * `SignedRequestParityTest` pins the bytes to the ones Android's protobuf-javalite path produces.
 */
internal object SignedRequests {

    fun isOcpAccount(owner: Ed25519.KeyPair): IsOcpAccountRequest {
        val unsigned = IsOcpAccountRequest(owner = owner.accountId())
        return unsigned.copy(signature = signatureOf(unsigned, owner))
    }

    /**
     * [requestingOwner] signs the same bytes as [owner]: Android computes both signatures before
     * setting either.
     */
    fun getTokenAccountInfos(
        owner: Ed25519.KeyPair,
        requestingOwner: Ed25519.KeyPair,
        filterByMint: ByteArray? = null,
    ): GetTokenAccountInfosRequest {
        val unsigned = GetTokenAccountInfosRequest(
            owner = owner.accountId(),
            requesting_owner = requestingOwner.accountId(),
            filter_by_mint_address = filterByMint?.let { SolanaAccountId(value_ = it.toByteString()) },
        )
        return unsigned.copy(
            signature = signatureOf(unsigned, owner),
            requesting_owner_signature = signatureOf(unsigned, requestingOwner),
        )
    }

    fun signatureOf(unsigned: Message<*, *>, signer: Ed25519.KeyPair): Signature =
        Signature(value_ = Ed25519.sign(unsigned.encodeCanonical(), signer).toByteString())

    private fun Ed25519.KeyPair.accountId() = SolanaAccountId(value_ = publicKeyBytes.toByteString())
}
