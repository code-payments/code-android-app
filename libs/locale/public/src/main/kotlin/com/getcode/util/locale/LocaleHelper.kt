package com.getcode.util.locale

interface LocaleHelper {
    fun getDefaultCurrencyName(): String
    suspend fun getDefaultCountry(): String?
}