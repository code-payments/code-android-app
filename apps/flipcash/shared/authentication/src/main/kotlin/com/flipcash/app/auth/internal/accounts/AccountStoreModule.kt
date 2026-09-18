package com.flipcash.app.auth.internal.accounts

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccountStoreModule {

    @Binds
    @Singleton
    abstract fun bindBlockStoreBytes(impl: PlayBlockStoreBytes): BlockStoreBytes

    companion object {
        @Provides
        @Singleton
        fun provideAccountStore(bytes: BlockStoreBytes): AccountStore =
            BlockStoreAccountStore(bytes)
    }
}
