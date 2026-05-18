package com.getcode.opencode.model.transactions

import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.solana.keys.Mint

data class StatelessSwapRequest(
    val owner: AccountCluster,
    val fromMint: Mint,
    val toMint: Mint,
    val amount: Long,
    val waitForFinalization: Boolean = false,
)
