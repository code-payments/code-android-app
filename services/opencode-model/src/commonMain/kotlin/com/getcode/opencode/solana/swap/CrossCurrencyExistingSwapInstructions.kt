package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.extractServerParameters
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram_BuyAndDepositIntoVm
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram_SellTokens
import com.getcode.opencode.internal.solana.programs.MemoProgram_Memo
import com.getcode.opencode.internal.solana.programs.SystemProgram_AdvanceNonce
import com.getcode.opencode.internal.solana.programs.TokenProgram_CloseAccount
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram_CloseSwapAccountIfEmpty
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram_TransferForSwap
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.transactions.StatefulSwapResponseServerParameters
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.PublicKey

/**
 * Builds the list of instructions for a cross-currency swap between two existing launchpad
 * currencies (neither side is the core mint). This mirrors the server's
 * `ReserveBuySellSwapHandler`.
 *
 * The source currency is transferred out of the user's VM swap account into a temporary account
 * held by the [swapAuthority], sold for the core mint, and the resulting core mint is immediately
 * spent buying the destination currency, which is deposited into the user's destination VM. The
 * swap authority is the seller/buyer and owns the temporary accounts (it signs client-side); the
 * VM authorities are signed server-side.
 *
 * Instruction format (matches the server's buy+sell existing currency layout):
 * 1.  System::AdvanceNonce
 * 2.  ComputeBudget::SetComputeUnitLimit
 * 3.  ComputeBudget::SetComputeUnitPrice
 * 4.  Memo::Memo
 * 5.  AssociatedTokenAccount::CreateIdempotent (temporary core mint ATA)
 * 6.  AssociatedTokenAccount::CreateIdempotent (temporary source mint ATA)
 * 7.  VM::TransferForSwap (source VM swap ATA -> temporary source mint ATA)
 * 8.  Reserve::SellTokens (sell source mint -> core mint into the temporary core mint ATA)
 * 9.  Reserve::BuyAndDepositIntoVm (buy destination mint funded by the temporary core mint ATA,
 *     depositing into the destination VM)
 * 10. Token::CloseAccount (close temporary core mint ATA)
 * 11. Token::CloseAccount (close temporary source mint ATA)
 * 12. VM::CloseSwapAccountIfEmpty (close source VM swap ATA if empty)
 *
 * @param serverParameters Existing currency server parameters (payer, limits, memory account, etc.).
 * @param nonce The nonce account to use for the transaction.
 * @param authority The owner of the user's accounts (the VTA owner and source swapper).
 * @param swapAuthority The temporary holder that executes the sell/buy and owns the temporary ATAs.
 * @param fromMintMetadata Metadata for the source currency being sold.
 * @param toMintMetadata Metadata for the destination currency being bought.
 * @param coreMintMetadata Metadata for the core mint (the reserve base currency).
 * @param amount The amount of the source currency to swap.
 * @return A list of [Instruction]s to execute the cross-currency swap.
 */
internal fun buildCrossCurrencyExistingSwapInstructions(
    serverParameters: StatefulSwapResponseServerParameters.ExistingCurrency,
    nonce: PublicKey,
    authority: PublicKey,
    swapAuthority: PublicKey,
    fromMintMetadata: MintMetadata,
    toMintMetadata: MintMetadata,
    coreMintMetadata: MintMetadata,
    amount: Long,
): List<Instruction> {
    val sourceVm = fromMintMetadata.vmMetadata
    val destinationVm = toMintMetadata.vmMetadata

    val sourceLaunchpad = fromMintMetadata.launchpadMetadata
        ?: throw IllegalStateException("source mint has no launchpad metadata")
    val destinationLaunchpad = toMintMetadata.launchpadMetadata
        ?: throw IllegalStateException("destination mint has no launchpad metadata")

    val serverParams = extractServerParameters(serverParameters)

    val sourceTimelockAccounts = fromMintMetadata.timelockSwapAccounts(authority)

    val createTemporaryCoreMintAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.payer,
        owner = swapAuthority,
        mint = coreMintMetadata.address,
    )

    val createTemporarySourceCurrencyAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.payer,
        owner = swapAuthority,
        mint = fromMintMetadata.address,
    )

    return buildList {
        // 1. System::AdvanceNonce
        add(SystemProgram_AdvanceNonce(nonce, serverParams.payer).instruction())

        // 2. ComputeBudget::SetComputeUnitLimit
        add(ComputeBudgetProgram_SetComputeUnitLimit(units = serverParams.computeUnitLimit).instruction())

        // 3. ComputeBudget::SetComputeUnitPrice
        add(ComputeBudgetProgram_SetComputeUnitPrice(microLamports = serverParams.computeUnitPrice).instruction())

        // 4. Memo::Memo
        add(MemoProgram_Memo(message = serverParams.memo).instruction())

        // 5. AssociatedTokenAccount::CreateIdempotent (temporary core mint ATA)
        add(createTemporaryCoreMintAta.instruction())

        // 6. AssociatedTokenAccount::CreateIdempotent (temporary source mint ATA)
        add(createTemporarySourceCurrencyAta.instruction())

        // 7. VM::TransferForSwap (source VM swap ATA -> temporary source mint ATA)
        add(
            VirtualMachineProgram_TransferForSwap(
                vmAuthority = sourceVm.authority,
                vm = sourceVm.vm,
                swapper = authority,
                swapPda = sourceTimelockAccounts.pda.publicKey,
                swapAta = sourceTimelockAccounts.ata.publicKey,
                destination = createTemporarySourceCurrencyAta.address,
                amount = amount,
                bump = sourceTimelockAccounts.pda.bump,
            ).instruction()
        )

        // 8. Reserve::SellTokens (sell source mint -> core mint into the temporary core mint ATA)
        add(
            CurrencyCreatorProgram_SellTokens(
                inAmount = amount,
                minOutAmount = 0,
                seller = swapAuthority,
                pool = sourceLaunchpad.liquidityPool,
                targetMint = fromMintMetadata.address,
                baseMint = coreMintMetadata.address,
                vaultTarget = sourceLaunchpad.mintVault,
                vaultBase = sourceLaunchpad.coreMintVault,
                sellerTarget = createTemporarySourceCurrencyAta.address,
                sellerBase = createTemporaryCoreMintAta.address,
            ).instruction()
        )

        // 9. Reserve::BuyAndDepositIntoVm (buy destination mint funded by the temporary core mint ATA)
        add(
            CurrencyCreatorProgram_BuyAndDepositIntoVm(
                inAmount = 0,
                minOutAmount = 0,
                vmMemoryIndex = serverParams.memoryIndex,
                buyer = swapAuthority,
                pool = destinationLaunchpad.liquidityPool,
                targetMint = toMintMetadata.address,
                baseMint = coreMintMetadata.address,
                vaultTarget = destinationLaunchpad.mintVault,
                vaultBase = destinationLaunchpad.coreMintVault,
                buyerBase = createTemporaryCoreMintAta.address,
                vmAuthority = destinationVm.authority,
                vm = destinationVm.vm,
                vmMemory = serverParams.memoryAccount,
                vmOmnibus = destinationVm.omnibus,
                vtaOwner = authority,
            ).instruction()
        )

        // 10. Token::CloseAccount (close temporary core mint ATA)
        add(
            TokenProgram_CloseAccount(
                account = createTemporaryCoreMintAta.address,
                destination = serverParams.payer,
                owner = swapAuthority,
            ).instruction()
        )

        // 11. Token::CloseAccount (close temporary source mint ATA)
        add(
            TokenProgram_CloseAccount(
                account = createTemporarySourceCurrencyAta.address,
                destination = serverParams.payer,
                owner = swapAuthority,
            ).instruction()
        )

        // 12. VM::CloseSwapAccountIfEmpty (close source VM swap ATA if empty)
        add(
            VirtualMachineProgram_CloseSwapAccountIfEmpty(
                vmAuthority = sourceVm.authority,
                vm = sourceVm.vm,
                swapper = authority,
                swapPda = sourceTimelockAccounts.pda.publicKey,
                swapAta = sourceTimelockAccounts.ata.publicKey,
                destination = serverParams.payer,
                bump = sourceTimelockAccounts.pda.bump,
            ).instruction()
        )
    }
}
