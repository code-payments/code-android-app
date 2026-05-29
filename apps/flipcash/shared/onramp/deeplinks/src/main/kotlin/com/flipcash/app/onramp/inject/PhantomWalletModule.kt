package com.flipcash.app.onramp.inject

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dev.bmcreations.phantom.connect.Chain
import dev.bmcreations.phantom.connect.Network
import dev.bmcreations.phantom.connect.OAuthLauncher
import dev.bmcreations.phantom.connect.OAuthResult
import dev.bmcreations.phantom.connect.PhantomSdk
import dev.bmcreations.phantom.connect.PhantomSdkConfig
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
            redirectUrl = "https://app.flipcash.com/phantom-wallet-callback",
        )
    }

    @Provides
    @ActivityRetainedScoped
    fun providesPhantomSdkConfig(): PhantomSdkConfig = PhantomSdkConfig(
        appId = "", // TODO: if/when we support connect
        redirectUri = "https://app.flipcash.com/phantom-auth-callback",
        providers = emptyList(), // disable social logins for now
        chains = listOf(Chain.Solana),
        network = Network.Mainnet,
        persistSession = false, // explicity don't persist
    )

    @Provides
    @ActivityRetainedScoped
    fun providePhantomSdk(
        config: PhantomSdkConfig,
        connector: PhantomWalletConnector,
    ): PhantomSdk {
        return PhantomSdk.create(
            config = config,
            oauthLauncher = NoOpOAuthLauncher,
            connectors = listOf(connector),
        )
    }
}

private object NoOpOAuthLauncher : OAuthLauncher {
    override suspend fun launch(url: String, callbackScheme: String): OAuthResult {
        return OAuthResult.Error(UnsupportedOperationException("OAuth not supported"))
    }
}
