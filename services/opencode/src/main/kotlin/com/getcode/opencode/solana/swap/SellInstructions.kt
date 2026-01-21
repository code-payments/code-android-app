package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.extractServerParameters
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram_SellAndDepositIntoVm
import com.getcode.opencode.internal.solana.programs.MemoProgram_Memo
import com.getcode.opencode.internal.solana.programs.SystemProgram_AdvanceNonce
import com.getcode.opencode.internal.solana.programs.TokenProgram_CloseAccount
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram_CloseSwapAccountIfEmpty
import com.getcode.opencode.internal.solana.programs.VirtualMachineProgram_TransferForSwap
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.transactions.SwapResponseServerParameters
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.PublicKey

/**
 * Builds the list of instructions required to sell a source currency for a core currency.
 *
 * This transaction flow performs the following:
 * 1.  Advances the nonce for transaction validity.
 * 2.  Sets compute unit limits and price.
 * 3.  Adds a memo for server-side correlation.
 * 4.  Creates a temporary token account for the source mint (idempotent).
 * 5.  Transfers the source currency from the user's VM swap account to the temporary account.
 * 6.  Executes the swap via the Currency Creator program, depositing the core currency into the core VM.
 * 7.  Closes the temporary source mint account.
 * 8.  Closes the source VM swap account if it is empty.
 *
 * @param serverParameters Parameters provided by the server for the swap (payer, limits, etc.).
 * @param nonce The nonce account to use for the transaction.
 * @param authority The owner of the user's accounts (VM authority).
 * @param swapAuthority The authority executing the swap (transaction signer).
 * @param sourceMintMetadata Metadata for the source currency (being sold).
 * @param coreMintMetadata Metadata for the core currency (being received).
 * @param amount The amount of source currency to sell.
 * @param minOutput The minimum amount of core currency to receive.
 * @return A list of [Instruction]s to execute the sell operation.
 */
internal fun buildSellInstructions(
    serverParameters: SwapResponseServerParameters,
    nonce: PublicKey,
    authority: PublicKey,
    swapAuthority: PublicKey,
    sourceMintMetadata: MintMetadata,
    coreMintMetadata: MintMetadata,
    amount: Long,
    minOutput: Long,
): List<Instruction> {
    val coreVm = coreMintMetadata.vmMetadata
    val sourceVm = sourceMintMetadata.vmMetadata

    val sourceLaunchpad = sourceMintMetadata.launchpadMetadata ?: throw IllegalStateException("source mint has no launchpad metadata")

    val serverParams = extractServerParameters(serverParameters)

    val sourceTimelockAccounts = sourceMintMetadata.timelockSwapAccounts(authority)

    val createTemporarySourceMintAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.payer,
        owner = swapAuthority,
        mint = sourceMintMetadata.address,
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

        // 5. AssociatedTokenAccount::CreateIdempotent (open source mint temporary account)
        add(createTemporarySourceMintAta.instruction())

        // 6. VM::TransferForSwap (source Mint VM swap ATA -> source Mint temporary account)
        add(
            VirtualMachineProgram_TransferForSwap(
                vmAuthority = sourceVm.authority,
                vm = sourceVm.vm,
                swapper = authority,
                swapPda = sourceTimelockAccounts.pda.publicKey,
                swapAta = sourceTimelockAccounts.ata.publicKey,
                destination = createTemporarySourceMintAta.address,
                amount = amount,
                bump = sourceTimelockAccounts.pda.bump,
            ).instruction()
        )

        // 7. CurrencyCreator::SellAndDepositIntoVm
        add(
            CurrencyCreatorProgram_SellAndDepositIntoVm(
                inAmount = amount,
                minOutAmount = minOutput,
                vmMemoryIndex = serverParams.memoryIndex,
                seller = swapAuthority,
                pool = sourceLaunchpad.liquidityPool,
                targetMint = sourceMintMetadata.address,
                baseMint = coreMintMetadata.address,
                vaultTarget = sourceLaunchpad.mintVault,
                vaultBase = sourceLaunchpad.coreMintVault,
                sellerTarget = createTemporarySourceMintAta.address,
                vmAuthority = coreVm.authority,
                vm = coreVm.vm,
                vmMemory = serverParams.memoryAccount,
                vmOmnibus = coreVm.omnibus,
                vtaOwner = authority,
            ).instruction()
        )

        // 8. Token::CloseAccount (closes source Mint temporary account)
        add(
            TokenProgram_CloseAccount(
                account = createTemporarySourceMintAta.address,
                destination = serverParams.payer,
                owner = swapAuthority,
            ).instruction()
        )

        // 9. VM::CloseSwapAccountIfEmpty (closes source Mint VM swap ATA if empty)
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
