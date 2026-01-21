package com.getcode.opencode.model.transactions

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey

typealias Swap = SwapMetadata

data class SwapMetadata(
    val verifiedMetadata: VerifiedSwapMetadata,
    val state: SwapState = SwapState.UNKNOWN,
    val signature: List<Byte>,
) {
    val swapId: SwapId
        get() = verifiedMetadata.id

    val fromMint: PublicKey
        get() = verifiedMetadata.fromMint

    val toMint: PublicKey
        get() = verifiedMetadata.toMint

    val amount: Fiat
        get() = verifiedMetadata.amount
}

/**
 * The state of a swap.
 */
enum class SwapState {
    UNKNOWN,

    /**
     * Swap state has been created and is pending funding
     */
    CREATED,

    /**
     * The VM swap PDA is in the process of being funded
     */
    FUNDING,

    /**
     * The VM swap PDA has been funded
     */
    FUNDED,

    /**
     * The swap transaction is being submitted to the blockchain
     */
    SUBMITTING,

    /**
     * The swap transaction has been finalized on the blockchain
     */
    FINALIZED,

    /**
     * The swap transaction failed
     */
    FAILED,

    /**
     * The swap is in the process of being cancelled.
     */
    CANCELLING,

    /**
     * The swap transaction is cancelled. Funds have been deposited back into the VM
     */
    CANCELLED,
    ;

    companion object {
        fun tryValueOf(value: TransactionService.SwapMetadata): SwapState {
            return entries.getOrNull(value.stateValue) ?: UNKNOWN
        }
    }
}


data class VerifiedSwapMetadata(
    val id: SwapId,
    val fromMint: PublicKey,
    val toMint: PublicKey,
    val amount: Fiat,
    val fundingSource: SwapFundingSource,
)