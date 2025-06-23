package com.flipcash.app.payments.inject

import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.internal.InternalPaymentController
import com.getcode.opencode.controllers.BalanceController
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
        billController: BillController,
        balanceController: BalanceController,
    ): PaymentController {
        return InternalPaymentController(
            resources = resources,
            billController = billController,
            balanceController = balanceController,
        )
    }

}