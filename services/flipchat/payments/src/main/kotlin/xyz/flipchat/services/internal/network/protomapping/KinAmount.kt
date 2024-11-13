package xyz.flipchat.services.internal.network.protomapping

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.CurrencyCode
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.fromFiatAmount

fun KinAmount.Companion.fromProtoExchangeData(exchangeData: TransactionService.ExchangeData): KinAmount {
    return fromFiatAmount(
        kin = Kin(exchangeData.quarks),
        fiat = exchangeData.nativeAmount,
        fx = exchangeData.exchangeRate,
        currencyCode = CurrencyCode.tryValueOf(exchangeData.currency)!!
    )
}