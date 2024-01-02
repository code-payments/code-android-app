package com.getcode.util.locale

import com.getcode.model.Currency

interface LocaleHelper {
    fun getDefaultCurrencyName(): String
    fun getDefaultCurrency(): Currency?
}