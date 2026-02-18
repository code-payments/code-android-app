package com.flipcash.app.tokens.inject

import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.providers.SessionListener
import com.getcode.opencode.providers.TokenMetadataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenModule {

    @Multibinds
    abstract fun sessionListeners(): Set<SessionListener>

    @Binds
    abstract fun bindTokenMetadataProvider(
        coordinator: TokenCoordinator
    ): TokenMetadataProvider

    @Binds
    @IntoSet
    abstract fun bindSessionListener(
        coordinator: TokenCoordinator
    ): SessionListener
}