package com.flipcash.app.session.inject

import com.flipcash.app.session.SessionController
import com.flipcash.app.session.internal.RealSessionController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    @Singleton
    abstract fun bindSessionController(impl: RealSessionController): SessionController
}