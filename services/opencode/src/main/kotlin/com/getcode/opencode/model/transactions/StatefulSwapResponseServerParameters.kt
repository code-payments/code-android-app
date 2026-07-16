package com.getcode.opencode.model.transactions

import com.getcode.solana.keys.PublicKey

sealed interface StatefulSwapResponseServerParameters {
    /**
     * Subisdizer account that will be paying for the swap
     */
    val payer: PublicKey
    /**
     *  The nonce that is reserved for use in the swap transaction
     */
    val nonce: PublicKey
    /**
     * The blockhash that is reserved for use in the swap transaction
     */
    val blockhash: PublicKey
    /**
     * ALTs that should be used when constructing the versioned transaction
     */
    val alts: List<AddressLookupTable>
    /**
     * Compute unit limit provided to the ComputeBudget::SetComputeUnitLimit
     */
    val computeUnitLimit: Int
    /**
     * Compute unit price provided in the ComputeBudget::SetCompute
     */
    val computeUnitPrice: Long

    /**
     * Value provided into the Memo::Memo instruction. If the value length is 0,
     * then the instruction can be omitted.
     */
    val memoValue: String

    /**
     * Server parameters when executing stateful buy/sell flows against the
     * Reserve contract against an existing currency
     *
     * Supported Solana transaction version: v0
     *
     * Instruction formats:
     *
     * Buy Tokens (Core Mint -> Launchpad Currency Mint):
     *  1. System::AdvanceNonce
     *  2. [Optional] ComputeBudget::SetComputeUnitLimit
     *  3. [Optional] ComputeBudget::SetComputeUnitPrice
     *  4. [Optional] Memo::Memo
     *  5. AssociatedTokenAccount::CreateIdempotent (open Core Mint temporary account)
     *  6. VM::TransferForSwap (Core Mint VM swap ATA -> Core Mint temporary account)
     *  7. Reserve::BuyAndDepositIntoVm (bounded buy depositing to_mint tokens into the to_mint VM)
     *  8. Token::CloseAccount (closes Core Mint temporary account)
     *  9. VM::CloseSwapAccountIfEmpty (closes Core Mint VM swap ATA if empty)
     *
     * Sell Tokens (Launchpad Currency Mint -> Core Mint):
     *  1. System::AdvanceNonce
     *  2. [Optional] ComputeBudget::SetComputeUnitLimit
     *  3. [Optional] ComputeBudget::SetComputeUnitPrice
     *  4. [Optional] Memo::Memo
     *  5. AssociatedTokenAccount::CreateIdempotent (open from_mint temporary account)
     *  6. VM::TransferForSwap (from_mint VM swap ATA -> from_mint temporary account)
     *  7. Reserve::SellAndDepositIntoVm (bounded sell depositing Core Mint into the Core Mint VM)
     *  8. Token::CloseAccount (closes from_mint temporary account)
     *  9. VM::CloseSwapAccountIfEmpty (closes from_mint swap PDA/ATA if empty)
     * 10. CurrencyCreator::SellAndDepositIntoVm (bounded sell depositing Core Mint into the Core Mint VM)
     * 11. Token::CloseAccount (closes Core Mint temporary account)
     * 12. VM::CloseSwapAccountIfEmpty (closes Core Mint VM swap ATA if empty)
     *
     * Swap Tokens (Launchpad Currency Mint -> Launchpad Currency Mint):
     *  1.  System::AdvanceNonce
     *  2.  [Optional] ComputeBudget::SetComputeUnitLimit
     *  3.  [Optional] ComputeBudget::SetComputeUnitPrice
     *  4.  [Optional] Memo::Memo
     *  5.  AssociatedTokenAccount::CreateIdempotent (open Core Mint temporary account)
     *  6.  AssociatedTokenAccount::CreateIdempotent (open from_mint temporary account)
     *  7.  VM::TransferForSwap
     *  8.  Reserve::SellTokens (bounded sell transferring Core Mint into temporary account)
     *  9.  Reserve::BuyAndDepositIntoVm (unlimited buy depositing to_mint tokens into the to_mint VM)
     *  10. Token::CloseAccount (closes Core Mint temporary account)
     *  11. Token::CloseAccount (closes from_mint temporary account)
     *  12. VM::CloseSwapAccountIfEmpty (closes from_mint swap PDA/ATA if empty)
     *
     */
    data class ExistingCurrency(
        /**
         * Subisdizer account that will be paying for the swap
         */
        override val payer: PublicKey,
        /**
         *  The nonce that is reserved for use in the swap transaction
         */
        override val nonce: PublicKey,
        /**
         * The blockhash that is reserved for use in the swap transaction
         */
        override val blockhash: PublicKey,
        /**
         * ALTs that should be used when constructing the versioned transaction
         */
        override val alts: List<AddressLookupTable>,
        /**
         * Compute unit limit provided to the ComputeBudget::SetComputeUnitLimit
         */
        override val computeUnitLimit: Int,
        /**
         * Compute unit price provided in the ComputeBudget::SetCompute
         */
        override val computeUnitPrice: Long,
        /**
         * Value provided into the Memo::Memo instruction. If the value length is 0,
         * then the instruction can be omitted.
         */
        override val memoValue: String,
        /**
         * The memory account where the destination virtual Timelock account lives
         */
        val memoryAccount: PublicKey,
        /**
         * The memory index where the destination virtual Timelock account lives
         */
        val memoryIndex: Int,
    ): StatefulSwapResponseServerParameters

    /**
     * Server parameters when executing stateful buy flows against the
     * Reserve contract against a new currency. Only the creator of the
     * currency will be able to execute this flow.
     *
     * Supported Solana transaction version: v0
     *
     * Instruction format:
     * 1. System::AdvanceNonce
     * 2. [Optional] ComputeBudget::SetComputeUnitLimit
     * 3. [Optional] ComputeBudget::SetComputeUnitPrice
     * 4. [Optional] Memo::Memo
     * 5. Reserve::InitializeCurrency
     * 6. Reserve::InitializePool
     * 7. VM::InitializeVm
     * 8. AssociatedTokenAccount::CreateIdempotent (open owner's Core Mint ATA)
     * 9. AssociatedTokenAccount::CreateIdempotent (open owner's to_mint VM Deposit ATA)
     * 10. VM::TransferForSwapWithFee (Core Mint VM swap ATA -> owner's Core Mint ATA (swap amount) and fee destination (fee amount))
     * 11. Reserve::BuyTokens (limited buy transferring to_mint tokens into the to_mint VM Deposit ATA)
     * 12. Token::CloseAccount (closes owner's Core Mint ATA)
     *
     * Note: Client should verify that the new currency's mint address matches that derive
     * from using these server parameters.
     */
    data class NewCurrency(
        /**
         * Subisdizer account that will be paying for the swap
         */
        override val payer: PublicKey,
        /**
         *  The nonce that is reserved for use in the swap transaction
         */
        override val nonce: PublicKey,
        /**
         * The blockhash that is reserved for use in the swap transaction
         */
        override val blockhash: PublicKey,
        /**
         * ALTs that should be used when constructing the versioned transaction
         */
        override val alts: List<AddressLookupTable>,
        /**
         * Compute unit limit provided to the ComputeBudget::SetComputeUnitLimit
         */
        override val computeUnitLimit: Int,
        /**
         * Compute unit price provided in the ComputeBudget::SetCompute
         */
        override val computeUnitPrice: Long,
        /**
         * Value provided into the Memo::Memo instruction. If the value length is 0,
         * then the instruction can be omitted.
         */
        override val memoValue: String,
        /**
         * The VM and currency authority
         */
        val authority: PublicKey,
        /**
         * The currency name
         */
        val name: String,
        /**
         * The currency symbol
         */
        val symbol: String,
        /**
         * The random seed value used to generate a unique currency of the given name
         */
        val seed: PublicKey,
        /**
         * Liquidity pool's percent sell fee in basis points
         */
        val sellFeeBps: Int,
        /**
         * The VM lock duration
         */
        val vmLockDurationInDays: Int,
        /**
         * Destination account where fee should be paid
         */
        val feeDestination: PublicKey,
        /**
         * Server-controlled treasury account for flows that require it (e.g.
         * initializing a new reserve currency outside of the core mint). Null
         * when the swap is funded directly with the core mint.
         */
        val treasury: PublicKey?,
        /**
         * The amount of core mint tokens used for the purchase when funding
         * through the [treasury]. Client should validate this matches a
         * pre-coordinated amount accepted by the user. Zero when unused.
         */
        val treasuryPurchaseAmount: Long,
    ): StatefulSwapResponseServerParameters

    /**
     * Server parameters when executing stateful swap flows against the
     * Coinbase Stable Swapper program.
     *
     * Supported Solana transaction version: v0
     *
     * Instruction format:
     * 1. System::AdvanceNonce
     * 2. [Optional] ComputeBudget::SetComputeUnitLimit
     * 3. [Optional] ComputeBudget::SetComputeUnitPrice
     * 4. [Optional] Memo::Memo
     * 5. AssociatedTokenAccount::CreateIdempotent (open swap authority's from_mint ATA)
     * 6. AssociatedTokenAccount::CreateIdempotent (open destination owner's to_mint ATA)
     * 7. VM::TransferForSwapWithFee (from_mint VM swap ATA -> swap authority's from_mint ATA (swap amount) and fee destination (fee amount))
     * 8. CoinbaseStableSwapper::Swap (from_mint swap authority ATA -> to_mint destination owner ATA)
     * 9. Token::CloseAccount (closes swap authority's from_mint ATA)
     */
    data class Stablecoin(
        /**
         * Subisdizer account that will be paying for the swap
         */
        override val payer: PublicKey,
        /**
         *  The nonce that is reserved for use in the swap transaction
         */
        override val nonce: PublicKey,
        /**
         * The blockhash that is reserved for use in the swap transaction
         */
        override val blockhash: PublicKey,
        /**
         * ALTs that should be used when constructing the versioned transaction
         */
        override val alts: List<AddressLookupTable>,
        /**
         * Compute unit limit provided to the ComputeBudget::SetComputeUnitLimit
         */
        override val computeUnitLimit: Int,
        /**
         * Compute unit price provided in the ComputeBudget::SetCompute
         */
        override val computeUnitPrice: Long,
        /**
         * Value provided into the Memo::Memo instruction. If the value length is 0,
         * then the instruction can be omitted.
         */
        override val memoValue: String,
        /**
         * Destination account where fee should be paid
         */
        val feeDestination: PublicKey,
        /**
         * The CoinbaseStableSwapper liquidity pool's configured fee recipient,
         * sourced from the on-chain LiquidityPool account. Required by the
         * CoinbaseStableSwapper::Swap instruction.
         */
        val poolFeeRecipient: PublicKey,
    ): StatefulSwapResponseServerParameters
}