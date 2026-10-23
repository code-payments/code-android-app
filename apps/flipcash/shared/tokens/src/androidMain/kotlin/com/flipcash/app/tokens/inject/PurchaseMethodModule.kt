package com.flipcash.app.tokens.inject

import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.funding.internal.InternalPurchaseMethodController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PurchaseMethodModule {

    @Binds
    abstract fun bindPurchaseMethodController(
        impl: InternalPurchaseMethodController
    ): PurchaseMethodController
}
