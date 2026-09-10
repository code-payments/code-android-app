package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.extensions.deriveDepositAccount
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.model.CoinbaseSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram_Swap
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.MemoProgram_Memo
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.transactions.StatelessSwapServerParameters
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.PublicKey

/**
 * Builds the instruction list for a stateless swap against the Coinbase Stable
 * Swapper program.
 *
 * Instruction layout:
 *  1. [Optional] ComputeBudget::SetComputeUnitLimit
 *  2. [Optional] ComputeBudget::SetComputeUnitPrice
 *  3. [Optional] Memo::Memo
 *  4. AssociatedTokenAccount::CreateIdempotent (open owner's to_mint VM Deposit ATA)
 *  5. CoinbaseStableSwapper::Swap (owner's from_mint ATA -> owner's to_mint VM Deposit ATA)
 *
 * Used by the on-app-open USDC -> USDF sweep: source is the owner's plain
 * USDC ATA, destination is the owner's USDF VM Deposit ATA (Geyser-monitored
 * — once funds land there, the server-side watcher transfers them into the USDF VM).
 */
internal fun buildStatelessSwapInstructions(
    serverParameters: StatelessSwapServerParameters,
    owner: PublicKey,
    fromMint: MintMetadata,
    toMint: MintMetadata,
    amount: Long,
): List<Instruction> {
    val toVm = requireNotNull(toMint.vmMetadata) {
        "Destination mint missing VM metadata: ${toMint.symbol}"
    }

    // Owner's source-mint ATA — where external USDC deposits land.
    val ownerFromAta = PublicKey.deriveAssociatedAccount(
        owner = owner,
        mint = fromMint.address,
    )

    // Owner's destination-mint VM Deposit PDA.
    val vmAccount = PublicKey.deriveVirtualMachineAccount(
        mint = toMint.address,
        authority = toVm.authority,
        lockout = toVm.lockDurationInDays.toUByte(),
    )
    val ownerToVmDepositPda = PublicKey.deriveDepositAccount(
        vm = vmAccount.publicKey,
        depositor = owner,
    )

    // Owner's destination-mint VM Deposit ATA — the address Geyser watches.
    val ownerToVmDepositAta = PublicKey.deriveAssociatedAccount(
        owner = ownerToVmDepositPda.publicKey,
        mint = toMint.address,
    )

    // Coinbase Stable Swapper PDAs.
    val swapAccounts = CoinbaseSwapAccounts.derive(fromMint.address, toMint.address)

    val feeRecipientFromMintAta = swapAccounts.feeRecipientTokenAccount(
        feeRecipient = serverParameters.poolFeeRecipient,
        fromMint = fromMint.address,
    )

    return buildList {
        // 1. [Optional] ComputeBudget::SetComputeUnitLimit
        if (serverParameters.computeUnitLimit != 0) {
            add(ComputeBudgetProgram_SetComputeUnitLimit(units = serverParameters.computeUnitLimit).instruction())
        }

        // 2. [Optional] ComputeBudget::SetComputeUnitPrice
        if (serverParameters.computeUnitPrice != 0L) {
            add(ComputeBudgetProgram_SetComputeUnitPrice(microLamports = serverParameters.computeUnitPrice).instruction())
        }

        // 3. [Optional] Memo::Memo
        if (serverParameters.memoValue.isNotEmpty()) {
            add(MemoProgram_Memo(message = serverParameters.memoValue).instruction())
        }

        // 4. AssociatedTokenAccount::CreateIdempotent (open owner's to_mint VM Deposit ATA)
        add(
            AssociatedTokenProgram_CreateIdempotent(
                subsidizer = serverParameters.payer,
                owner = ownerToVmDepositPda.publicKey,
                mint = toMint.address,
            ).instruction()
        )

        // 5. CoinbaseStableSwapper::Swap (owner's from_mint ATA -> owner's to_mint VM Deposit ATA)
        add(
            CoinbaseStableSwapperProgram_Swap(
                pool = swapAccounts.pool,
                inVault = swapAccounts.inVault,
                outVault = swapAccounts.outVault,
                inVaultTokenAccount = swapAccounts.inVaultTokenAccount,
                outVaultTokenAccount = swapAccounts.outVaultTokenAccount,
                userFromTokenAccount = ownerFromAta.publicKey,
                toTokenAccount = ownerToVmDepositAta.publicKey,
                feeRecipientTokenAccount = feeRecipientFromMintAta,
                feeRecipient = serverParameters.poolFeeRecipient,
                fromMint = fromMint.address,
                toMint = toMint.address,
                user = owner,
                whitelist = swapAccounts.whitelist,
                amountIn = amount,
                minAmountOut = amount, // 1:1 stable pair
            ).instruction()
        )
    }
}
