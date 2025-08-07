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

    @Singleton
    @Provides
    fun providesOkRpcDriver(
        okHttpClient: OkHttpClient
    ): HttpNetworkDriver = OkHttpNetworkDriver(okHttpClient)

    @Provides
    @Named("solana-rpc-url")
    fun providesSolanaRpcUrl(): String = "https://api.mainnet-beta.solana.com"

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