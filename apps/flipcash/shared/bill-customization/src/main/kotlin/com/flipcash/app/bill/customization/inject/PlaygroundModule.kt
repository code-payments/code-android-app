package com.flipcash.app.bill.customization.inject

import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.internal.InternalBillPlaygroundController
import com.getcode.opencode.exchange.Exchange
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaygroundModule {
    @Provides
    @Singleton
    fun providesPlaygroundController(exchange: Exchange
    ): BillPlaygroundController = InternalBillPlaygroundController(exchange)
}