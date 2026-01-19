package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.extractServerParameters
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.CurrencyCreatorProgram_BuyAndDepositIntoVm
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
 * Builds the list of instructions required to buy a target currency using a core currency.
 *
 * This transaction flow performs the following:
 * 1.  Advances the nonce for transaction validity.
 * 2.  Sets compute unit limits and price.
 * 3.  Adds a memo for server-side correlation.
 * 4.  Creates a temporary token account for the core mint (idempotent).
 * 5.  Transfers the core currency from the user's VM swap account to the temporary account.
 * 6.  Executes the swap via the Currency Creator program, depositing the bought currency into the target VM.
 * 7.  Closes the temporary core mint account.
 * 8.  Closes the source VM swap account if it is empty.
 *
 * @param serverParameters Parameters provided by the server for the swap (payer, limits, etc.).
 * @param nonce The nonce account to use for the transaction.
 * @param authority The owner of the user's accounts (VM authority).
 * @param swapAuthority The authority executing the swap (transaction signer).
 * @param coreMintMetadata Metadata for the core currency (source).
 * @param targetMintMetadata Metadata for the target currency (destination).
 * @param amount The amount of core currency to spend.
 * @param minOutput The minimum amount of target currency to receive.
 * @return A list of [Instruction]s to execute the buy operation.
 */
internal fun buildBuyInstructions(
    serverParameters: SwapResponseServerParameters,
    nonce: PublicKey,
    authority: PublicKey,
    swapAuthority: PublicKey,
    coreMintMetadata: MintMetadata,
    targetMintMetadata: MintMetadata,
    amount: Long,
    minOutput: Long,
): List<Instruction> {
    val coreVm = coreMintMetadata.vmMetadata
    val targetVm = targetMintMetadata.vmMetadata

    val targetLaunchpad = targetMintMetadata.launchpadMetadata ?: throw IllegalStateException("Target mint has no launchpad metadata")

    val serverParams = extractServerParameters(serverParameters)

    val coreTimelockAccounts = coreMintMetadata.timelockSwapAccounts(authority)

    val createTemporaryCoreMintAta = AssociatedTokenProgram_CreateIdempotent(
        subsidizer = serverParams.payer,
        owner = swapAuthority,
        mint = coreMintMetadata.address,
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

        // 5. AssociatedTokenAccount::CreateIdempotent (open Core Mint temporary account)
        add(createTemporaryCoreMintAta.instruction())

        // 6. VM::TransferForSwap (Core Mint VM swap ATA -> Core Mint temporary account)
        add(
            VirtualMachineProgram_TransferForSwap(
                vmAuthority = coreVm.authority,
                vm = coreVm.vm,
                swapper = authority,
                swapPda = coreTimelockAccounts.pda.publicKey,
                swapAta = coreTimelockAccounts.ata.publicKey,
                destination = createTemporaryCoreMintAta.address,
                amount = amount,
                bump = coreTimelockAccounts.pda.bump,
            ).instruction()
        )

        // 7. CurrencyCreator::BuyAndDepositIntoVm
        add(
            CurrencyCreatorProgram_BuyAndDepositIntoVm(
                amount = amount,
                minOutput = minOutput,
                vmMemoryIndex = serverParams.memoryIndex,
                buyer = swapAuthority,
                pool = targetLaunchpad.liquidityPool,
                currency = targetLaunchpad.currencyConfig,
                targetMint = targetMintMetadata.address,
                baseMint = coreMintMetadata.address,
                vaultTarget = targetLaunchpad.mintVault,
                vaultBase = targetLaunchpad.coreMintVault,
                buyerBase = createTemporaryCoreMintAta.address,
                vmAuthority = targetVm.authority,
                vm = targetVm.vm,
                vmMemory = serverParams.memoryAccount,
                vmOmnibus = targetVm.omnibus,
                vtaOwner = authority,
            ).instruction()
        )

        // 8. Token::CloseAccount (closes Core Mint temporary account)
        add(
            TokenProgram_CloseAccount(
                account = createTemporaryCoreMintAta.address,
                destination = serverParams.payer,
                owner = swapAuthority,
            ).instruction()
        )
        // 9. VM::CloseSwapAccountIfEmpty (closes Core Mint VM swap ATA if empty)
        add(
            VirtualMachineProgram_CloseSwapAccountIfEmpty(
                vmAuthority = coreVm.authority,
                vm = coreVm.vm,
                swapper = authority,
                swapPda = coreTimelockAccounts.pda.publicKey,
                swapAta = coreTimelockAccounts.ata.publicKey,
                destination = serverParams.payer,
                bump = coreTimelockAccounts.pda.bump,
            ).instruction()
        )
    }
}
