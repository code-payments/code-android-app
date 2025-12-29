package com.getcode.opencode.model.transactions

import com.getcode.solana.keys.PublicKey

sealed interface SwapResponseServerParameters {

    /**
     * Server parameters when executing stateless buy/sell flows against the
     * Currency Creator program
     *
     * Note: The transaction and instruction formats are nearly identical to
     * the stateful flow, except the system::AdvanceNonce instruction is
     * ommitted in favour of using a latest blockhash.
     *
     * Note: This will be deprecated in favour of the stateful version. For
     * PoC of swap flow only.
     */
    data class Stateless(
        /**
         * Subisdizer account that will be paying for the swap
         */
        val payer: PublicKey,
        /**
         * Recent blockhash
         */
        val recentBlockhash: PublicKey,
        /**
         * ALTs that should be used when constructing the versioned transaction
         */
        val alts: List<AddressLookupTable>,
        /**
         * Compute unit limit provided to the ComputeBudget::SetComputeUnitLimit
         */
        val computeUnitLimit: Int,
        /**
         * Compute unit price provided in the ComputeBudget::SetCompute
         */
        val computeUnitPrice: Long,
        /**
         * Value provided into the Memo::Memo instruction. If the value length is 0,
         * then the instruction can be omitted.
         */
        val memoValue: String,
        /**
         * The memory account where the destination virtual Timelock account lives
         */
        val memoryAccount: PublicKey,
        /**
         * The memory account where the destination virtual Timelock account lives
         */
        val memoryIndex: Int,
    ) : SwapResponseServerParameters

    /**
     * Server parameters when executing stateful buy/sell flows against the
     * Currency Creator program
     *
     * Supported Solana transaction version: v0
     *
     * Instruction formats:
     *
     * Buy Tokens (Core Mint -> Launchpad Currency Mint):
     *  1. System::AdvanceNonce
     *  2. [Optional] ComputeBudget::SetComputeUnitLimit
     *  3. [Optional] ComputeBudget::SetCompute
     *  4. [Optional] Memo::Memo
     *  5. AssociatedTokenAccount::CreateIdempotent (open Core Mint temporary account)
     *  6. VM::TransferForSwap (Core Mint VM swap ATA -> Core Mint temporary account)
     *  7. CurrencyCreator::BuyAndDepositIntoVm (bounded buy depositing to_mint tokens into the to_mint VM)
     *  8. Token::CloseAccount (closes Core Mint temporary account)
     *  9. VM::CloseSwapAccountIfEmpty (closes Core Mint VM swap ATA if empty)
     *
     * Sell Tokens (Launchpad Currency Mint -> Core Mint):
     *  1. System::AdvanceNonce
     *  2. [Optional] ComputeBudget::SetComputeUnitLimit
     *  3. [Optional] ComputeBudget::SetCompute
     *  4. [Optional] Memo::Memo
     *  5. AssociatedTokenAccount::CreateIdempotent (open from_mint temporary account)
     *  6. VM::TransferForSwap (from_mint VM swap ATA -> from_mint temporary account)
     *  7. CurrencyCreator
     *  8. Token::CloseAccount (closes from_mint temporary account)
     *  9. VM::CloseSwapAccountIfEmpty (closes from_mint swap PDA/ATA if empty)
     * 10. CurrencyCreator::SellAndDepositIntoVm (bounded sell depositing Core Mint into the Core Mint VM)
     * 11. Token::CloseAccount (closes Core Mint temporary account)
     * 12. VM::CloseSwapAccountIfEmpty (closes Core Mint VM swap ATA if empty)
     *
     * Swap Tokens (Launchpad Currency Mint -> Launchpad Currency Mint):
     *  1.  System::AdvanceNonce
     *  2.  [Optional] ComputeBudget::SetComputeUnitLimit
     *  3.  [Optional] ComputeBudget::SetCompute
     *  4.  [Optional] Memo::Memo
     *  5.  AssociatedTokenAccount::CreateIdempotent (open Core Mint temporary account)
     *  6.  AssociatedTokenAccount::CreateIdempotent (open from_mint temporary account)
     *  7.  VM::TransferForSwap
     *  8.  CurrencyCreator::SellTokens (bounded sell transferring Core Mint into temporary account)
     *  9.  CurrencyCreator::BuyAndDepositIntoVm (unlimited buy depositing to_mint tokens into the to_mint VM)
     *  10. Token::CloseAccount (closes Core Mint temporary account)
     *  11. Token::CloseAccount (closes from_mint temporary account)
     *  12. VM::CloseSwapAccountIfEmpty (closes from_mint swap PDA/ATA if empty)
     *
     */
    data class Stateful(
        /**
         * Subisdizer account that will be paying for the swap
         */
        val payer: PublicKey,
        /**
         * ALTs that should be used when constructing the versioned transaction
         */
        val alts: List<AddressLookupTable>,
        /**
         * Compute unit limit provided to the ComputeBudget::SetComputeUnitLimit
         */
        val computeUnitLimit: Int,
        /**
         * Compute unit price provided in the ComputeBudget::SetCompute
         */
        val computeUnitPrice: Long,
        /**
         * Value provided into the Memo::Memo instruction. If the value length is 0,
         * then the instruction can be omitted.
         */
        val memoValue: String,
        /**
         * The memory account where the destination virtual Timelock account lives
         */
        val memoryAccount: PublicKey,
        /**
         * The memory index where the destination virtual Timelock account lives
         */
        val memoryIndex: Int,
    ) : SwapResponseServerParameters
}