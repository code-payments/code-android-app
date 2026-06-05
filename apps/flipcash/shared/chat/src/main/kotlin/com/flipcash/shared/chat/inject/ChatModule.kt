package com.flipcash.shared.chat.inject

import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.providers.SessionListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    @Binds
    @IntoSet
    abstract fun bindSessionListener(
        coordinator: ChatCoordinator
    ): SessionListener
}
