package com.coinbase.onramp.internal

import com.coinbase.onramp.annotations.OnRampClient
import com.coinbase.onramp.api.CoinbaseOnRampApi
import com.coinbase.onramp.data.OnRampApiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OnRampModule {

    @Singleton
    @Provides
    fun providesOnRampApiConfig(): OnRampApiConfig = OnRampApiConfig(
        baseUrl = "https://api.developer.coinbase.com/onramp/",
        path = "v2/onramp/order",
        method = "POST"
    )

    @Singleton
    @Provides
    fun providesHttpLoggingInterceptor() = HttpLoggingInterceptor()
        .apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Singleton
    @Provides
    fun providesOkHttpClient(httpLoggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(httpLoggingInterceptor)
            .build()


    @Singleton
    @Provides
    @OnRampClient
    fun providesRetrofit(
        okHttpClient: OkHttpClient,
        apiConfig: OnRampApiConfig
    ): Retrofit = Retrofit.Builder()
        .baseUrl(apiConfig.baseUrl)
        .client(okHttpClient)
        .addConverterFactory(
            Json.asConverterFactory(
                "application/json; charset=UTF8".toMediaType()
            )
        )
        .build()

    @Provides
    @Singleton
    fun provideOnRampApi(
        @OnRampClient retrofit: Retrofit
    ): CoinbaseOnRampApi = retrofit.create(CoinbaseOnRampApi::class.java)
}