package com.getcode.inject

import android.content.Context
import com.getcode.util.AndroidLocale
import com.getcode.util.AndroidResources
import com.getcode.util.CurrencyUtils
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.AndroidNetworkUtils
import com.getcode.utils.NetworkUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun providesResourceHelper(
        @ApplicationContext context: Context,
    ): ResourceHelper = AndroidResources(context)

    @Provides
    fun providesLocaleHelper(
        @ApplicationContext context: Context,
        currencyUtils: CurrencyUtils,
    ): LocaleHelper = AndroidLocale(context, currencyUtils)

    @Provides
    fun providesNetworkUtilities(
        @ApplicationContext context: Context,
    ): NetworkUtils = AndroidNetworkUtils(context)
}