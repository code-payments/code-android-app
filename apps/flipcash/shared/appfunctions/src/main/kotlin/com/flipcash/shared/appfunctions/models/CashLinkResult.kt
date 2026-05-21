package com.flipcash.shared.appfunctions.models

import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class CashLinkResult(
    val url: String,
    val amountUsd: Double,
    val tokenSymbol: String,
)
