package com.getcode.opencode.inject

import com.getcode.opencode.internal.transactors.AccountClusterFactory
import com.getcode.opencode.internal.transactors.DefaultAccountClusterFactory
import com.getcode.opencode.internal.transactors.DefaultPayloadFactory
import com.getcode.opencode.internal.transactors.PayloadFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactorModule {
    @Binds
    abstract fun bindPayloadFactory(impl: DefaultPayloadFactory): PayloadFactory

    @Binds
    abstract fun bindAccountClusterFactory(impl: DefaultAccountClusterFactory): AccountClusterFactory
}
