package com.getcode.solana.inject

import com.getcode.solana.rpc.RpcConfig
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.OkHttpNetworkDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SolanaModule {

    private const val MAINNET_RPC_URL = "https://api.mainnet-beta.solana.com"

    @Singleton
    @Provides
    fun providesOkRpcDriver(
        okHttpClient: OkHttpClient
    ): HttpNetworkDriver = OkHttpNetworkDriver(okHttpClient)

    @Provides
    @Named("solana-rpc-url")
    fun providesSolanaRpcUrl(): String = MAINNET_RPC_URL

    @Provides
    @Singleton
    fun providesSolanaRpcConfig(
        networkDriver: HttpNetworkDriver,
        @Named("solana-rpc-url") rpcUrl: String
    ): RpcConfig = RpcConfig(
        networkDriver = networkDriver,
        rpcUrl = rpcUrl
    )
}