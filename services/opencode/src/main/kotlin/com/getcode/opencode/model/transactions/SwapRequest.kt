package com.getcode.opencode.model.transactions

import com.getcode.ed25519.Ed25519.KeyPair

data class SwapRequest(
    val type: SwapRequestType
)

sealed interface SwapRequestType {
    /**
     * @param owner The verified owner account public key
     * @param swapAuthority The user authority account that will sign to authorize the swap. Ideally,
     * this is an account derived off the owner account that is solely responsible
     * for swapping.
     * @param limit Maximum amount to swap from the source mint, in quarks. If value is set to zero,
     * the entire amount will be swapped.
     * @param waitForBlockchainStatus Whether the client wants the RPC to wait for blockchain status. If false,
     * then the RPC will return Success when the swap is submitted to the blockchain.
     * Otherwise, the RPC will observe and report back the status of the transaction.
     */
    data class Initiate(
        val owner: KeyPair,
        val swapAuthority: KeyPair,
        val limit: Long,
        val waitForBlockchainStatus: Boolean,
    ): SwapRequestType

    /**
     * The signature for the locally constructed swap transaction
     */
    data class SubmitSignature(
        val signature: KeyPair
    ): SwapRequestType
}