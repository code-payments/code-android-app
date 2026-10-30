package com.getcode.opencode.model.transactions

import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey

/**
 * Server parameters for executing stateless swap flows against the
 * Coinbase Stable Swapper program.
 *
 * Supported Solana transaction version: v0
 *
 * Instruction format:
 *  1. [Optional] ComputeBudget::SetComputeUnitLimit
 *  2. [Optional] ComputeBudget::SetComputeUnitPrice
 *  3. [Optional] Memo::Memo
 *  4. AssociatedTokenAccount::CreateIdempotent (open owner's to_mint VM Deposit ATA)
 *  5. CoinbaseStableSwapper::Swap (owner's from_mint ATA -> owner's to_mint VM Deposit ATA)
 */
data class StatelessSwapServerParameters(
    val payer: PublicKey,
    val blockhash: Hash,
    val alts: List<AddressLookupTable>,
    val computeUnitLimit: Int,
    val computeUnitPrice: Long,
    val memoValue: String,
    val poolFeeRecipient: PublicKey,
)
