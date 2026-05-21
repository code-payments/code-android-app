package com.flipcash.shared.appfunctions.models

import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class TransactionResult(
    val transactions: List<TransactionItem>,
)

@AppFunctionSerializable
data class TransactionItem(
    val id: String,
    val description: String,
    val amountUsd: Double,
    val timestamp: Long,
    val state: String,
    val type: String,
)
