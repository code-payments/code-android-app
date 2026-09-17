package com.flipcash.app.session.inject

import com.flipcash.app.core.toast.ToastController
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.session.SessionController
import com.flipcash.app.session.internal.RealSessionController
import com.flipcash.app.session.internal.delegates.CashLinkDelegate
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

    /**
     * Bound off the delegate rather than off [SessionController], so a surface that only needs to
     * hear about claims does not get the whole session — and, for chat, does not get
     * `openCashLink`. See [CashLinkClaims].
     */
    @Binds
    @Singleton
    abstract fun bindCashLinkClaims(impl: CashLinkDelegate): CashLinkClaims
}