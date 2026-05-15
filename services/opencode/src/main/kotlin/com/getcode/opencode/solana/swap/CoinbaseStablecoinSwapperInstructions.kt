package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.model.CoinbaseSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram_Swap
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.MemoProgram_Memo
import com.getcode.opencode.internal.solana.programs.SystemProgram_AdvanceNonce
import com.getcode.opencode.internal.solana.programs.TokenProgram_CloseAccount
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram_TransferForSwapWithFee
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.PublicKey

/**
 * Builds the list of instructions required to swap tokens via the Coinbase Stable Swapper program.
 *
 * This transaction flow performs the following:
 * 1.  Advances the nonce for transaction validity.
 * 2.  Sets compute unit limits and price.
 * 3.  Adds a memo for server-side correlation.
 * 4.  Creates a temporary token account for the from_mint owned by the swap authority (idempotent).
 * 5.  Creates a token account for the to_mint owned by the destination owner (idempotent).
 * 6.  Transfers from_mint tokens from the user's VM swap account to the swap authority's ATA,
 *     splitting between the swap amount and a fee paid to the fee destination.
 * 7.  Executes the swap via the Coinbase Stable Swapper program, converting from_mint tokens
 *     in the swap authority's ATA into to_mint tokens deposited to the destination owner's ATA.
 * 8.  Closes the swap authority's temporary from_mint ATA.
 *
 * @param serverParameters Parameters provided by the server for the stablecoin swap (payer, limits,
 *                         fee destination, and the on-chain pool fee recipient).
 * @param nonce The nonce account to use for the transaction.
 * @param authority The owner of the user's accounts (VM swapper).
 * @param swapAuthority A random one-time use account that signs the swap.
 * @param destinationOwner The owner account where to_mint tokens will land.
 * @param fromMintMetadata Metadata for the source mint being swapped from.
 * @param toMintMetadata Metadata for the destination mint being swapped to.
 * @param amount The amount of from_mint tokens to swap (in quarks).
 * @param feeAmount The fee amount deducted from the VM transfer and sent to the fee destination.
 * @param minOutput The minimum acceptable amount of to_mint tokens to receive (slippage protection).
 * @return A list of [Instruction]s to execute the stablecoin swap.
 */
internal fun buildStablecoinSwapperInstructions(
    serverParameters: SwapResponseServerParameters.Stablecoin,
    nonce: PublicKey,
    authority: PublicKey,
    swapAuthority: PublicKey,
    destinationOwner: PublicKey,
    fromMintMetadata: MintMetadata,
    toMintMetadata: MintMetadata,
    amount: Long,
    feeAmount: Long,
    minOutput: Long,
): List<Instruction> {
    val fromVm = fromMintMetadata.vmMetadata
    val fromTimelockAccounts = fromMintMetadata.timelockSwapAccounts(authority)

    // Derive CoinbaseStableSwapper PDAs
    val swapAccounts = CoinbaseSwapAccounts.derive(fromMintMetadata.address, toMintMetadata.address)

    val feeRecipientFromMintAta = swapAccounts.feeRecipientTokenAccount(
        feeRecipient = serverParameters.poolFeeRecipient,
        fromMint = fromMintMetadata.address,
    )

    // 5. AssociatedTokenAccount::CreateIdempotent (open swap authority's from_mint ATA)
    val createSwapAuthorityFromMintAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParameters.payer,
        owner = swapAuthority,
        mint = fromMintMetadata.address,
    )

    // 6. AssociatedTokenAccount::CreateIdempotent (open destination owner's to_mint ATA)
    val createDestinationOwnerToMintAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParameters.payer,
        owner = destinationOwner,
        mint = toMintMetadata.address,
    )

    return buildList {
        // 1. System::AdvanceNonce
        add(SystemProgram_AdvanceNonce(nonce, serverParameters.payer).instruction())

        // 2. ComputeBudget::SetComputeUnitLimit
        add(ComputeBudgetProgram_SetComputeUnitLimit(units = serverParameters.computeUnitLimit).instruction())

        // 3. ComputeBudget::SetComputeUnitPrice
        add(ComputeBudgetProgram_SetComputeUnitPrice(microLamports = serverParameters.computeUnitPrice).instruction())

        // 4. Memo::Memo
        add(MemoProgram_Memo(message = serverParameters.memoValue).instruction())

        // 5. AssociatedTokenAccount::CreateIdempotent (open swap authority's from_mint ATA)
        add(createSwapAuthorityFromMintAta.instruction())

        // 6. AssociatedTokenAccount::CreateIdempotent (open destination owner's to_mint ATA)
        add(createDestinationOwnerToMintAta.instruction())

        // 7. VM::TransferForSwapWithFee (from_mint VM swap ATA -> swap authority's from_mint ATA (swap amount) and fee destination (fee amount))
        add(
            VirtualMachineProgram_TransferForSwapWithFee(
                vmAuthority = fromVm.authority,
                vm = fromVm.vm,
                swapper = authority,
                swapPda = fromTimelockAccounts.pda.publicKey,
                swapAta = fromTimelockAccounts.ata.publicKey,
                destination = createSwapAuthorityFromMintAta.address,
                feeDestination = serverParameters.feeDestination,
                swapAmount = amount,
                feeAmount = feeAmount,
                bump = fromTimelockAccounts.pda.bump,
            ).instruction()
        )

        // 8. CoinbaseStableSwapper::Swap (from_mint swap authority ATA -> to_mint destination owner ATA)
        add(
            CoinbaseStableSwapperProgram_Swap(
                pool = swapAccounts.pool,
                inVault = swapAccounts.inVault,
                outVault = swapAccounts.outVault,
                inVaultTokenAccount = swapAccounts.inVaultTokenAccount,
                outVaultTokenAccount = swapAccounts.outVaultTokenAccount,
                userFromTokenAccount = createSwapAuthorityFromMintAta.address,
                toTokenAccount = createDestinationOwnerToMintAta.address,
                feeRecipientTokenAccount = feeRecipientFromMintAta,
                feeRecipient = serverParameters.poolFeeRecipient,
                fromMint = fromMintMetadata.address,
                toMint = toMintMetadata.address,
                user = swapAuthority,
                whitelist = swapAccounts.whitelist,
                amountIn = amount,
                minAmountOut = minOutput,
            ).instruction()
        )

        // 9. Token::CloseAccount (closes swap authority's from_mint ATA)
        add(
            TokenProgram_CloseAccount(
                account = createSwapAuthorityFromMintAta.address,
                destination = serverParameters.payer,
                owner = swapAuthority,
            ).instruction()
        )
    }
}
