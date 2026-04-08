package com.flipcash.app.notifications.inject

import com.flipcash.app.notifications.FirebasePushTokenProvider
import com.flipcash.app.push.PushTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    abstract fun bindPushTokenProvider(impl: FirebasePushTokenProvider): PushTokenProvider
}
