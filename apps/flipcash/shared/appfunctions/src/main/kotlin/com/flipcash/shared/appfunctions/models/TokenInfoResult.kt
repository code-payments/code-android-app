package com.flipcash.shared.appfunctions.models

import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class TokenInfoResult(
    val symbol: String,
    val name: String,
    val balanceUsd: Double,
    val marketCapUsd: Double,
    val mintAddress: String,
    val description: String,
)
