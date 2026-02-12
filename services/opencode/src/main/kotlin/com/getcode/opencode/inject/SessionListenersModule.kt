package com.getcode.opencode.inject

import com.getcode.opencode.providers.SessionListener
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionListenerModule {
    @Multibinds
    abstract fun sessionListeners(): Set<SessionListener>
}