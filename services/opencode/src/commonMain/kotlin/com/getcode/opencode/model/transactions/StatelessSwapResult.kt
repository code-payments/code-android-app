package com.getcode.opencode.model.transactions

import com.getcode.solana.keys.Signature

typealias StatelessSwapResult = Result<StatelessSwapSuccess>

data class StatelessSwapSuccess(
    val code: StatelessSwapSuccessCode,
    val transactionSignature: Signature,
)

enum class StatelessSwapSuccessCode {
    Submitted,
    Finalized,
}
