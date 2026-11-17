package com.getcode.opencode.model.transactions

/**
 * Fee and resource configuration for a v1 Solana transaction (SIMD-0385). In v1 these values
 * replace the ComputeBudget instructions. The server sends it only when the client requested
 * transaction version 1.
 */
data class SolanaTransactionConfig(
    /**
     * The compute unit limit for the transaction
     */
    val computeUnitLimit: Int,
    /**
     * The loaded accounts data size limit for the transaction, in bytes
     */
    val loadedAccountsDataSizeLimit: Int,
    /**
     * The total priority fee for the transaction, in lamports. Unlike
     * ComputeBudget::SetComputeUnitPrice, this is not a per compute unit price.
     */
    val priorityFeeLamports: Long,
    /**
     * The requested heap size in bytes. When 0, it's omitted from the transaction config and
     * the runtime default is used.
     */
    val heapSize: Int,
)
