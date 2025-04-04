package com.flipcash.app.inject

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Singleton
    @Provides
    fun provideCompositeDisposable(): CompositeDisposable {
        return CompositeDisposable()
    }

    @Provides
    fun provideScheduler(): Scheduler = Schedulers.io()

//    @Singleton
//    @Provides
//    fun provideAccountAuthenticator(
//        @ApplicationContext context: Context,
//    ): AccountAuthenticator {
//        return AccountAuthenticator(context)
//    }
//
//    @Singleton
//    @Provides
//    fun provideMixpanelApi(@ApplicationContext context: Context): MixpanelAPI {
//        return MixpanelAPI.getInstance(context, BuildConfig.MIXPANEL_API_KEY)
//    }

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

//    @Singleton
//    @Provides
//    fun providesCurrencyProvider(
//        resources: ResourceHelper
//    ): CurrencyProvider = object : CurrencyProvider {
//        override suspend fun preferredCurrency(): Currency = Currency.Kin
//        override suspend fun defaultCurrency(): Currency = Currency.Kin
//        override fun suffix(currency: Currency?): String = resources.getKinSuffix()
//    }
}