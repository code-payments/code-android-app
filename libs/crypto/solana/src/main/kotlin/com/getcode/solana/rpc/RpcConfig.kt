package com.getcode.solana.rpc

import com.solana.networking.HttpNetworkDriver

data class RpcConfig(
    val networkDriver: HttpNetworkDriver,
    val rpcUrl: String,
)
