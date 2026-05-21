package com.flipcash.shared.appfunctions.models

import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class BalanceResult(
    val totalUsd: Double,
    val tokens: List<TokenBalanceItem>,
)

@AppFunctionSerializable
data class TokenBalanceItem(
    val symbol: String,
    val name: String,
    val balanceUsd: Double,
)
