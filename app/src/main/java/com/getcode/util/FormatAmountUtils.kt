package com.getcode.util

import com.getcode.App
import com.getcode.R
import com.getcode.model.Currency
import com.getcode.model.KinAmount
import com.getcode.utils.FormatUtils

object FormatAmountUtils {
    fun formatAmountString(amount: KinAmount): String {
        return formatAmountString(
            CurrencyUtils.getCurrency(amount.rate.currency.name)!!,
            amount.fiat
        )
    }

    fun formatAmountString(currency: Currency, amount: Double): String {
        val isKin = currency.code == "KIN"

        return if (isKin) {
            "${FormatUtils.formatWholeRoundDown(amount)} ${
                App.getInstance().getString(R.string.core_kin)
            }"
        } else {
            "${currency.symbol}${FormatUtils.format(amount)} ${
                App.getInstance().getString(R.string.core_ofKin)
            }"
        }
    }
}