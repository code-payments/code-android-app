package com.getcode.data.transactions

import com.getcode.model.AirdropType
import com.getcode.model.Currency
import com.getcode.model.HistoricalTransaction
import com.getcode.model.Kin
import com.getcode.model.PaymentType
import com.getcode.util.DateUtils
import com.getcode.util.Kin
import com.getcode.util.flagResId
import com.getcode.util.format
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils

fun HistoricalTransaction.toUi(
    currencyLookup: (String) -> Currency?,
    resources: ResourceHelper,
): HistoricalTransactionUiModel {
    val currency = currencyLookup(  transactionRateCurrency?.uppercase().orEmpty()) ?: Currency.Kin

    val isKin = currency.code == "KIN"
    val currencyResId = currency.flagResId(resources)

    val kinAmount = Kin.fromQuarks(transactionAmountQuarks)
    val amount: Double =
        if (isKin) kinAmount.toKinTruncatingLong().toDouble()
        else nativeAmount

    val amountText = currency.format(amount)

    return HistoricalTransactionUiModel(
        id = id,
        amountText = amountText,
        dateText = DateUtils.getDateWithToday(date * 1000L),
        isKin = isKin,
        kinAmountText = FormatUtils.formatWholeRoundDown(
            kinAmount.toKinTruncatingLong().toDouble()
        ),
        paymentType = paymentType,
        currencyResourceId = currencyResId,
        isWithdrawal = isWithdrawal,
        isRemoteSend = isRemoteSend,
        isDeposit = isDeposit,
        isReturned = isReturned,
        airdropType = airdropType
    )
}

data class HistoricalTransactionUiModel(
    val id: List<Byte>,
    val amountText: String = "",
    val dateText: String = "",
    val isKin: Boolean = false,
    val kinAmountText: String = "",
    val paymentType: PaymentType,
    val currencyResourceId: Int? = 0,
    val isWithdrawal: Boolean = false,
    val isRemoteSend: Boolean = false,
    val isDeposit: Boolean = false,
    val isReturned: Boolean = false,
    val airdropType: AirdropType?
)