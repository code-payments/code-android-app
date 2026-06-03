package com.flipcash.app.currency.internal

import com.getcode.opencode.model.financial.Currency

sealed interface RegionListItem {
    data class TitleItem(val text: String) : RegionListItem
    data class RegionCurrencyItem(val currency: Currency, val isRecent: Boolean) : RegionListItem
}