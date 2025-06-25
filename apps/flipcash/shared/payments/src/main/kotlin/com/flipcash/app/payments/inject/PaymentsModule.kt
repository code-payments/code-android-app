package com.flipcash.app.payments.inject

import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.internal.InternalPaymentController
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.resources.ResourceHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PaymentsModule {
    @Provides
    @Singleton
    fun providePaymentController(
        resources: ResourceHelper,
        balanceController: BalanceController,
        transactionController: TransactionController,
        exchange: Exchange,
        userManager: UserManager,
    ): PaymentController {
        return InternalPaymentController(
            resources = resources,
            balanceController = balanceController,
            transactionController = transactionController,
            exchange = exchange,
            userManager = userManager
        )
    }

}