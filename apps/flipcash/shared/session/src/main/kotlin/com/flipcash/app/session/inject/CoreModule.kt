package com.flipcash.app.session.inject

import com.flipcash.app.core.toast.ToastController
import com.flipcash.app.session.SessionController
import com.flipcash.app.session.internal.RealSessionController
import com.flipcash.app.session.internal.toast.SessionToastController
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

    @Binds
    @Singleton
    abstract fun bindToastController(impl: SessionToastController): ToastController
}