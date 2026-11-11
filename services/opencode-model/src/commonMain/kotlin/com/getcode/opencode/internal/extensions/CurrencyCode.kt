package com.getcode.opencode.internal.extensions

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.internal.format.isoFractionDigits

val CurrencyCode.fractionDigits: Int
    get() = when (this) {
        CurrencyCode.AFN -> 0 // No pul coins/notes in circulation since 2002; all transactions in whole afghanis due to low subunit value.
        CurrencyCode.COP -> 0 // Centavos obsolete since the 1980s; all pricing and payments in whole pesos.
        CurrencyCode.IDR -> 0 // Sen coins/banknotes obsolete since 1987 due to hyperinflation; all transactions rounded to whole rupiah.
        CurrencyCode.IRR -> 0 // Dinar never circulated; prices often quoted in "tomans" (1,000 rials) with inflation making subunits irrelevant.
        CurrencyCode.MGA -> 0 // Non-decimal 5:1 ratio; iraimbilanja coins obsolete since 1960s, no practical use despite ISO decimal assumption.
        CurrencyCode.MRU -> 0 // Non-decimal 5:1 ratio; khoum coins rare and unused in transactions since 1970s, despite a 2017 commemorative issue.
        CurrencyCode.TZS -> 0 // Senti coins withdrawn in the 1980s; no circulation since, with whole shillings used exclusively.
        CurrencyCode.UYU -> 0 // Centésimo coins withdrawn in 1998; prices rounded to nearest peso in retail and payments.
        else -> isoFractionDigits(name)
    }
