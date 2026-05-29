package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.model.CoinbaseSwapAccounts
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram_Swap
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.MemoProgram_Memo
import com.getcode.opencode.internal.solana.programs.TokenProgram_Transfer
import com.getcode.opencode.internal.solana.programs.UsdfProgram_Swap
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.FundSwapPool
import com.getcode.opencode.model.transactions.LiquidityPool
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58

internal fun buildUsdcToUsdfSwapInstructions(
    sender: PublicKey,
    owner: PublicKey,
    amount: Long,
    pool: FundSwapPool,
    swapId: SwapId,
): List<Instruction> {
    return buildList {
        val usdfSwapAccounts = Token.usdf.timelockSwapAccounts(owner)

        val createUsdfAta = AssociatedTokenProgram_CreateIdempotent(
            subsidizer = sender,
            owner = sender,
            mint = Mint.usdf,
        )

        val createUsdcAta = AssociatedTokenProgram_CreateIdempotent(
            subsidizer = sender,
            owner = sender,
            mint = Mint.usdc,
        )

        // 1. ComputeBudget::SetComputeUnitLimit
        add(ComputeBudgetProgram_SetComputeUnitLimit(units = 200_000).instruction())

        // 2. ComputeBudget::SetComputeUnitPrice
        add(ComputeBudgetProgram_SetComputeUnitPrice(microLamports = 1_000).instruction())

        // 3. AssociatedTokenAccount::CreateIdempotent (USDF ATA)
        add(createUsdfAta.instruction())
        // 4. System::CreateAccount (USDF Swap PDA)
        add(
            AssociatedTokenProgram_CreateIdempotent(
                subsidizer = sender,
                owner = usdfSwapAccounts.pda.publicKey,
                mint = Mint.usdf,
            ).instruction()
        )

        // 5. AssociatedTokenAccount::CreateIdempotent (USDC ATA)
        add(createUsdcAta.instruction())

        // 6. Memo:Memo
        add(
            MemoProgram_Memo(
                message = swapId.publicKey.base58()
            ).instruction()
        )

        // 7. Swap (USDC ATA -> USDF ATA)
        when (pool) {
            is FundSwapPool.Usdf -> {
                add(
                    UsdfProgram_Swap(
                        amount = amount,
                        usdfToOther = false,
                        user = sender,
                        pool = pool.pool.address,
                        usdfVault = pool.pool.usdfVault,
                        otherVault = pool.pool.otherVault,
                        userUsdfToken = createUsdfAta.address,
                        userOtherToken = createUsdcAta.address,
                    ).instruction()
                )
            }
            is FundSwapPool.CoinbaseStableSwapper -> {
                val swapAccounts = CoinbaseSwapAccounts.derive(Mint.usdc, Mint.usdf)
                add(
                    CoinbaseStableSwapperProgram_Swap(
                        pool = swapAccounts.pool,
                        inVault = swapAccounts.inVault,
                        outVault = swapAccounts.outVault,
                        inVaultTokenAccount = swapAccounts.inVaultTokenAccount,
                        outVaultTokenAccount = swapAccounts.outVaultTokenAccount,
                        userFromTokenAccount = createUsdcAta.address,
                        toTokenAccount = createUsdfAta.address,
                        feeRecipientTokenAccount = swapAccounts.feeRecipientTokenAccount(
                            feeRecipient = pool.feeRecipient,
                            fromMint = Mint.usdc,
                        ),
                        feeRecipient = pool.feeRecipient,
                        fromMint = Mint.usdc,
                        toMint = Mint.usdf,
                        user = sender,
                        whitelist = swapAccounts.whitelist,
                        amountIn = amount,
                        minAmountOut = 0,
                    ).instruction()
                )
            }
        }

        // 8. Token::Transfer (USDF ATA -> USDF Swap PDA)
        add(
            TokenProgram_Transfer(
                amount = amount,
                owner = sender,
                source = createUsdfAta.address,
                destination = usdfSwapAccounts.ata.publicKey,
            ).instruction()
        )
    }
}