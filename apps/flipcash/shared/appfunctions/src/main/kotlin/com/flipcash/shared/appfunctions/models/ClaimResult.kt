package com.flipcash.shared.appfunctions.models

import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class ClaimResult(
    val amountUsd: Double,
    val tokenSymbol: String,
)
