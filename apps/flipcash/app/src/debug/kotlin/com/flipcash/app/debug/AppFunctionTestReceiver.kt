package com.flipcash.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appfunctions.AppFunctionContext
import com.flipcash.shared.appfunctions.functions.BalanceFunctions
import com.flipcash.shared.appfunctions.functions.CashLinkFunctions
import com.flipcash.shared.appfunctions.functions.DepositFunctions
import com.flipcash.shared.appfunctions.functions.TokenInfoFunctions
import com.flipcash.shared.appfunctions.functions.TransactionFunctions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppFunctionTestReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestEntryPoint {
        fun balanceFunctions(): BalanceFunctions
        fun transactionFunctions(): TransactionFunctions
        fun depositFunctions(): DepositFunctions
        fun tokenInfoFunctions(): TokenInfoFunctions
        fun cashLinkFunctions(): CashLinkFunctions
    }

    override fun onReceive(context: Context, intent: Intent) {
        val function = intent.getStringExtra("function") ?: "getBalance"
        Log.d(TAG, "Testing AppFunction: $function")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TestEntryPoint::class.java,
        )

        val appContext = object : AppFunctionContext {
            override val context: Context = context.applicationContext
        }

        // Fire-and-forget — no goAsync() to avoid ANR on long-running operations
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val result = when (function) {
                    "getBalance" -> entryPoint.balanceFunctions().getBalance(appContext).toString()
                    "getTransactionHistory" -> {
                        val limit = intent.getIntExtra("limit", 5)
                        entryPoint.transactionFunctions().getTransactionHistory(appContext, limit).toString()
                    }
                    "getDepositAddress" -> {
                        val token = intent.getStringExtra("tokenSymbol") ?: "USDF"
                        entryPoint.depositFunctions().getDepositAddress(appContext, token).toString()
                    }
                    "getTokenInfo" -> {
                        val token = intent.getStringExtra("tokenSymbol") ?: "USDF"
                        entryPoint.tokenInfoFunctions().getTokenInfo(appContext, token).toString()
                    }
                    "sendCashLink" -> {
                        val amount = intent.getStringExtra("amountUsd")?.toDouble()
                            ?: intent.getFloatExtra("amountUsd", 1.0f).toDouble()
                        val token = intent.getStringExtra("tokenSymbol") ?: "USDF"
                        entryPoint.cashLinkFunctions().sendCashLink(appContext, amount, token).toString()
                    }
                    "claimCashLink" -> {
                        val url = intent.getStringExtra("url")
                            ?: throw IllegalArgumentException("url extra is required")
                        entryPoint.cashLinkFunctions().claimCashLink(appContext, url).toString()
                    }
                    else -> "Unknown function: $function"
                }
                Log.d(TAG, "SUCCESS: $result")
            } catch (e: Exception) {
                Log.e(TAG, "FAILED: $function", e)
            }
        }
    }

    companion object {
        private const val TAG = "AppFunctionTest"
    }
}
