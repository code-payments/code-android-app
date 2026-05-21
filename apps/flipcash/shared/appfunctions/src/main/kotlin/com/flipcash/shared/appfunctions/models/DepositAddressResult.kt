package com.flipcash.shared.appfunctions.models

import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class DepositAddressResult(
    val address: String,
    val tokenSymbol: String,
)
