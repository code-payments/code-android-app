package com.flipcash.shared.appfunctions.functions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSchemaDefinition
import androidx.appfunctions.AppFunctionSerializable
import com.flipcash.shared.appfunctions.models.BalanceResult
import com.flipcash.shared.appfunctions.models.CashLinkResult
import com.flipcash.shared.appfunctions.models.ClaimResult
import com.flipcash.shared.appfunctions.models.DepositAddressResult
import com.flipcash.shared.appfunctions.models.TokenInfoResult
import com.flipcash.shared.appfunctions.models.TransactionResult

@AppFunctionSchemaDefinition(name = "getBalance", version = 1, category = "wallet")
interface GetBalanceSchema {
    suspend fun getBalance(context: AppFunctionContext): BalanceResult
}

@AppFunctionSchemaDefinition(name = "getTransactionHistory", version = 1, category = "wallet")
interface GetTransactionHistorySchema {
    suspend fun getTransactionHistory(context: AppFunctionContext, limit: Int): TransactionResult
}

@AppFunctionSchemaDefinition(name = "getDepositAddress", version = 1, category = "wallet")
interface GetDepositAddressSchema {
    suspend fun getDepositAddress(context: AppFunctionContext, tokenSymbol: String): DepositAddressResult
}

@AppFunctionSchemaDefinition(name = "getTokenInfo", version = 1, category = "wallet")
interface GetTokenInfoSchema {
    suspend fun getTokenInfo(context: AppFunctionContext, tokenSymbol: String): TokenInfoResult
}

@AppFunctionSchemaDefinition(name = "sendCashLink", version = 1, category = "wallet")
interface SendCashLinkSchema {
    suspend fun sendCashLink(context: AppFunctionContext, amountUsd: Double, tokenSymbol: String): CashLinkResult
}

@AppFunctionSchemaDefinition(name = "claimCashLink", version = 1, category = "wallet")
interface ClaimCashLinkSchema {
    suspend fun claimCashLink(context: AppFunctionContext, url: String): ClaimResult
}
