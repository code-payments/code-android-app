package com.flipcash.app.core.inject

import com.flipcash.app.core.SessionController
import com.flipcash.app.core.internal.session.RealSessionController
import com.getcode.util.permissions.PermissionChecker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {
    @Binds
    @Singleton
    abstract fun bindSessionController(impl: RealSessionController): SessionController
}