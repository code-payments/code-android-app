package com.flipcash.app.core.ui

import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode

data class CurrencyHolder(
    val selected: Currency? = null
) {
    val code: CurrencyCode?
        get() = selected?.code?.let { CurrencyCode.tryValueOf(it) }
}