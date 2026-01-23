package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.internal.solana.model.LiquidityPool
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.internal.solana.programs.AssociatedTokenProgram_CreateIdempotent
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.opencode.internal.solana.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.opencode.internal.solana.programs.MemoProgram_Memo
import com.getcode.opencode.internal.solana.programs.TokenProgram_Transfer
import com.getcode.opencode.internal.solana.programs.UsdfProgram_Swap
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58

internal fun buildUsdcToUsdfSwapInstructions(
    sender: PublicKey,
    owner: PublicKey,
    amount: Long,
    pool: LiquidityPool,
    swapId: SwapId,
): List<Instruction> {
    return buildList {
        val usdfSwapAccounts = Token.usdf.timelockSwapAccounts(owner)

        val createUsdfAta = AssociatedTokenProgram_CreateIdempotent(
            subsidizer = sender,
            owner = sender,
            mint = Mint.usdf,
        )

        val usdcAta = PublicKey.deriveAssociatedAccount(
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

        // 5. Memo:Memo
        add(
            MemoProgram_Memo(
                message = swapId.publicKey.base58()
            ).instruction()
        )

        // 6. Usdf::Swap (USDC ATA -> USDF ATA)
        add(
            UsdfProgram_Swap(
                amount = amount,
                usdfToOther = false,
                user = sender,
                pool = pool.address,
                usdfVault = pool.usdfVault,
                otherVault = pool.otherVault,
                userUsdfToken = createUsdfAta.address,
                userOtherToken = usdcAta.publicKey,
            ).instruction()
        )

        // 7. Token::Transfer (USDF ATA -> USDF Swap PDA)
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