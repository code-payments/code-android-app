package com.getcode.opencode.exchange

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.services.opencode.BuildConfig

interface VerifiedFiatCalculator {
    fun compute(
        amount: Fiat,
        token: Token,
        balance: Fiat? = null,
        rate: Rate,
        trace: Boolean = true,
    ): LocalFiat
}
