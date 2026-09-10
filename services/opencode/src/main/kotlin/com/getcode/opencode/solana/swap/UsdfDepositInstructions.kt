package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.extensions.deriveDepositAccount
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.internal.solana.model.CoinbaseSwapAccounts
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram_Swap
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.vmAuthority
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.solana.Instruction
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

/**
 * Builds instructions for a Phantom-signed USDC→USDF swap that deposits
 * directly into the owner's USDF VM deposit PDA ATA.
 *
 * Instruction layout:
 *  1. ComputeBudget::SetComputeUnitLimit(200_000)
 *  2. ComputeBudget::SetComputeUnitPrice(1_000)
 *  3. AssociatedTokenAccount::CreateIdempotent (owner's USDF VM deposit PDA ATA — Phantom pays rent)
 *  4. CoinbaseStableSwapper::Swap (sender's USDC ATA → owner's USDF VM deposit PDA ATA)
 *
 * The Geyser watcher monitors the deposit PDA ATA and transfers USDF
 * into the VM once funds land — same path as the stateless sweep.
 */
internal fun buildUsdfDepositInstructions(
    sender: PublicKey,
    owner: PublicKey,
    amount: Long,
    feeRecipient: PublicKey,
): List<Instruction> {
    val usdfMeta = MintMetadata.usdf
    val usdfVm = requireNotNull(usdfMeta.vmMetadata)

    // Sender's USDC ATA — source of funds in Phantom's wallet.
    val senderUsdcAta = PublicKey.deriveAssociatedAccount(
        owner = sender,
        mint = Mint.usdc,
    ).publicKey

    // Owner's USDF VM Deposit PDA.
    val vmAccount = PublicKey.deriveVirtualMachineAccount(
        mint = Mint.usdf,
        authority = usdfVm.authority,
        lockout = usdfVm.lockDurationInDays.toUByte(),
    )
    val depositPda = PublicKey.deriveDepositAccount(
        vm = vmAccount.publicKey,
        depositor = owner,
    )

    // Owner's USDF VM Deposit ATA — the address Geyser watches.
    val depositPdaUsdfAta = PublicKey.deriveAssociatedAccount(
        owner = depositPda.publicKey,
        mint = Mint.usdf,
    )

    // Coinbase Stable Swapper PDAs.
    val swapAccounts = CoinbaseSwapAccounts.derive(Mint.usdc, Mint.usdf)

    val feeRecipientUsdcAta = swapAccounts.feeRecipientTokenAccount(
        feeRecipient = feeRecipient,
        fromMint = Mint.usdc,
    )

    return buildList {
        // 1. ComputeBudget::SetComputeUnitLimit
        add(ComputeBudgetProgram_SetComputeUnitLimit(units = 200_000).instruction())
        // 2. ComputeBudget::SetComputeUnitPrice
        add(ComputeBudgetProgram_SetComputeUnitPrice(microLamports = 1_000).instruction())
        // 3. AssociatedTokenAccount::CreateIdempotent (owner's USDF VM deposit PDA ATA)
        add(
            AssociatedTokenProgram_CreateIdempotent(
                subsidizer = sender,
                owner = depositPda.publicKey,
                mint = Mint.usdf,
            ).instruction()
        )
        // 4. CoinbaseStableSwapper::Swap (sender's USDC ATA → owner's USDF VM deposit PDA ATA)
        add(
            CoinbaseStableSwapperProgram_Swap(
                pool = swapAccounts.pool,
                inVault = swapAccounts.inVault,
                outVault = swapAccounts.outVault,
                inVaultTokenAccount = swapAccounts.inVaultTokenAccount,
                outVaultTokenAccount = swapAccounts.outVaultTokenAccount,
                userFromTokenAccount = senderUsdcAta,
                toTokenAccount = depositPdaUsdfAta.publicKey,
                feeRecipientTokenAccount = feeRecipientUsdcAta,
                feeRecipient = feeRecipient,
                fromMint = Mint.usdc,
                toMint = Mint.usdf,
                user = sender,
                whitelist = swapAccounts.whitelist,
                amountIn = amount,
                minAmountOut = amount, // 1:1 stable pair
            ).instruction()
        )
    }
}
