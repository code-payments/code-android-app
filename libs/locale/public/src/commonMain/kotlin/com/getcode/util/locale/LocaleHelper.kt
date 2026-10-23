package com.getcode.util.locale

interface LocaleHelper {
    fun getLanguageTag(): String
    fun getDefaultCurrencyName(): String
    suspend fun getDefaultCountry(): String?
}