package com.getcode.inject

import android.content.Context
import com.getcode.BuildConfig
import com.getcode.api.KadoApi
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.network.repository.PrefRepository
import com.getcode.services.R
import com.getcode.services.db.CurrencyProvider
import com.getcode.services.model.PrefsString
import com.getcode.util.AccountAuthenticator
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.CurrencyUtils
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
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

    @Singleton
    @Provides
    fun provideAccountAuthenticator(
        @ApplicationContext context: Context,
    ): AccountAuthenticator {
        return AccountAuthenticator(context)
    }

    @Singleton
    @Provides
    fun provideMixpanelApi(@ApplicationContext context: Context): MixpanelAPI {
        return MixpanelAPI.getInstance(context, BuildConfig.MIXPANEL_API_KEY)
    }

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
    @Named("kado-retrofit")
    fun provideKadoRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("https://api.kado.money/")
        .client(okHttpClient)
        .build()

    @Singleton
    @Provides
    fun providesKadoApi(
        @Named("kado-retrofit")
        retrofit: Retrofit
    ): KadoApi = retrofit.create(KadoApi::class.java)

    @Singleton
    @Provides
    fun providesCurrencyProvider(
        prefRepository: PrefRepository,
        currencyUtils: CurrencyUtils,
        locale: LocaleHelper,
        resources: ResourceHelper,
    ): CurrencyProvider = object : CurrencyProvider {
        override suspend fun preferredCurrency(): Currency? {
            val preferredCurrencyCode = prefRepository.get(
                PrefsString.KEY_LOCAL_CURRENCY,
                ""
            ).takeIf { it.isNotEmpty() }
            val preferredCurrency = preferredCurrencyCode?.let { currencyUtils.getCurrency(it) }
            return preferredCurrency ?: locale.getDefaultCurrencyName().let { currencyUtils.getCurrency(it) }
        }

        override suspend fun defaultCurrency(): Currency? = currencyUtils.getCurrency(locale.getDefaultCurrencyName())

        override fun suffix(currency: Currency?): String {
            return if (currency?.code == CurrencyCode.KIN.name) {
                ""
            } else {
                resources.getString(R.string.core_ofKin)
            }
        }
    }
}