package com.getcode.opencode.model.transactions

typealias SwapResult = Result<SwapSuccessCode>
typealias StartSwapResult = Result<SwapMetadata>

enum class SwapSuccessCode {
    Submitted,
    Finalized,
    ;
}