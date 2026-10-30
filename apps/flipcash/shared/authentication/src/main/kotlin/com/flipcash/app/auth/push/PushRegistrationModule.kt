package com.flipcash.app.auth.push

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PushRegistrationModule {

    @Binds
    abstract fun bindPushRegistrationStore(impl: DataStorePushRegistrationStore): PushRegistrationStore
}
