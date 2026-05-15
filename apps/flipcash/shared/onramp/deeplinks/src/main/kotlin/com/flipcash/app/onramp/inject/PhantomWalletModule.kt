package com.flipcash.app.onramp.inject

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import dev.bmcreations.phantom.connect.wallet.createDeeplinkLauncher

@Module
@InstallIn(ActivityRetainedComponent::class)
object PhantomWalletModule {
    @Provides
    @ActivityRetainedScoped
    fun providePhantomWalletConnector(
        @ApplicationContext context: Context,
    ): PhantomWalletConnector {
        return PhantomWalletConnector(
            deeplinkLauncher = createDeeplinkLauncher(context),
            appUrl = "https://app.flipcash.com",
            callbackScheme = "codewallet",
        )
    }
}
