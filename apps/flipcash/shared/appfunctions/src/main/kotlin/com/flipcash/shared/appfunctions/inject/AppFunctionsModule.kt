package com.flipcash.shared.appfunctions.inject

import androidx.appfunctions.service.AppFunctionConfiguration
import com.flipcash.shared.appfunctions.functions.BalanceFunctions
import com.flipcash.shared.appfunctions.functions.CashLinkFunctions
import com.flipcash.shared.appfunctions.functions.DepositFunctions
import com.flipcash.shared.appfunctions.functions.TokenInfoFunctions
import com.flipcash.shared.appfunctions.functions.TransactionFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppFunctionsModule {

    @Provides
    @Singleton
    fun provideAppFunctionConfiguration(
        balanceFunctions: BalanceFunctions,
        transactionFunctions: TransactionFunctions,
        depositFunctions: DepositFunctions,
        tokenInfoFunctions: TokenInfoFunctions,
        cashLinkFunctions: CashLinkFunctions,
    ): AppFunctionConfiguration = AppFunctionConfiguration.Builder()
        .addEnclosingClassFactory(BalanceFunctions::class.java) { balanceFunctions }
        .addEnclosingClassFactory(TransactionFunctions::class.java) { transactionFunctions }
        .addEnclosingClassFactory(DepositFunctions::class.java) { depositFunctions }
        .addEnclosingClassFactory(TokenInfoFunctions::class.java) { tokenInfoFunctions }
        .addEnclosingClassFactory(CashLinkFunctions::class.java) { cashLinkFunctions }
        .build()
}
