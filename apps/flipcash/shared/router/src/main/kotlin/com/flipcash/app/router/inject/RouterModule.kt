package com.flipcash.app.router.inject

import com.flipcash.app.router.internal.AppRouter
import com.flipcash.app.router.Router
import com.flipcash.services.user.UserManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RouterModule {

    @Singleton
    @Provides
    fun providesRouter(
        userManager: UserManager,
    ): Router = AppRouter(userManager)
}