package com.getcode.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.codeinc.gen.transaction.v2.CodeTransactionService as TransactionService
import kotlinx.serialization.Serializable


@Serializable
@Entity(tableName = "exchangeData")
data class ExchangeRate(
    @ColumnInfo(name = "fiat")
    val fx: Double,
    @PrimaryKey
    val currency: CurrencyCode,
    @ColumnInfo(name = "synced_at")
    val synced: Long,
)

fun KinAmount.Companion.fromProtoExchangeData(exchangeData: TransactionService.ExchangeData): KinAmount {
    return fromFiatAmount(
        kin = Kin(exchangeData.quarks),
        fiat = exchangeData.nativeAmount,
        fx = exchangeData.exchangeRate,
        currencyCode = CurrencyCode.tryValueOf(exchangeData.currency)!!
    )
}