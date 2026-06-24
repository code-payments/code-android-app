package com.flipcash.shared.chat.inject

import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.internal.RealChatCoordinator
import com.getcode.opencode.providers.SessionListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    @Binds
    @Singleton
    abstract fun bindChatCoordinator(
        impl: RealChatCoordinator
    ): ChatCoordinator

    @Binds
    @IntoSet
    abstract fun bindSessionListener(
        impl: RealChatCoordinator
    ): SessionListener
}
