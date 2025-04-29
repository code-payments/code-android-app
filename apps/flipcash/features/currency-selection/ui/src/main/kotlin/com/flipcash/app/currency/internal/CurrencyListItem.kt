package com.flipcash.app.currency.internal

import com.getcode.opencode.model.financial.Currency

sealed interface CurrencyListItem {
    data class TitleItem(val text: String) : CurrencyListItem
    data class RegionCurrencyItem(val currency: Currency, val isRecent: Boolean) : CurrencyListItem
}