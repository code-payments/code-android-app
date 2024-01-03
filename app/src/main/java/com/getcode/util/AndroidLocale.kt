package com.getcode.util

import android.content.Context
import com.getcode.App
import com.getcode.model.Currency
import com.getcode.util.locale.LocaleHelper
import com.getcode.utils.LocaleUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidLocale @Inject constructor(
    @ApplicationContext private val context: Context,
    private val currencyUtils: CurrencyUtils,
): LocaleHelper {
    override fun getDefaultCurrencyName(): String {
        return LocaleUtils.getDefaultCurrency(context)
    }

    override fun getDefaultCurrency(): Currency? {
        return currencyUtils.getCurrency(getDefaultCurrencyName())
    }

    override fun getDefaultCountry(): String {
        return LocaleUtils.getDefaultCountry(context)
    }
}