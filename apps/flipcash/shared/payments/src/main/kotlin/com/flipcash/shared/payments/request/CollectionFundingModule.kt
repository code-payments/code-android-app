package com.flipcash.shared.payments.request

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CollectionFundingModule {

    @Binds
    @Singleton
    abstract fun bindCollectionFundingStore(impl: DataStoreCollectionFundingStore): CollectionFundingStore
}
