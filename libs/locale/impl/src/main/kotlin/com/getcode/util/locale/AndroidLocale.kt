package com.getcode.util.locale

import android.content.Context
import com.getcode.model.Currency
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidLocale @Inject constructor(
    @ApplicationContext private val context: Context,
): LocaleHelper {
    override fun getDefaultCurrencyName(): String {
        return LocaleUtils.getDefaultCurrency(context)
    }

    override fun getDefaultCountry(): String {
        return LocaleUtils.getDefaultCountry(context)
    }
}