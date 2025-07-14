package com.flipcash.app.persistence.converters

import androidx.room.TypeConverter
import com.flipcash.app.core.pools.PoolUserSummary
import com.flipcash.services.models.NetworkPoolUserSummary
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat

object PoolUserSummaryConverter {
    @TypeConverter
    fun fromPoolUserSummary(summary: PoolUserSummary): String {
        return when (summary) {
            is PoolUserSummary.Won -> "won_${summary.amountWon.quarks}_${summary.amountWon.currencyCode}"
            is PoolUserSummary.Lost -> "lost_${summary.amount.quarks}_${summary.amount.currencyCode}"
            is PoolUserSummary.Refunded -> "refunded_${summary.amount.quarks}_${summary.amount.currencyCode}"
            PoolUserSummary.NotSet -> "not-set"
        }
    }

    @TypeConverter
    fun fromPoolUserSummary(summary: NetworkPoolUserSummary): String {
        return when (summary) {
            is NetworkPoolUserSummary.Win -> "won_${summary.amountWon.quarks}_${summary.amountWon.currencyCode}"
            is NetworkPoolUserSummary.Lose -> "lost_${summary.amount.quarks}_${summary.amount.currencyCode}"
            is NetworkPoolUserSummary.Refund -> "refunded_${summary.amount.quarks}_${summary.amount.currencyCode}"
            NetworkPoolUserSummary.NotSet -> "not-set"
        }
    }


    @TypeConverter
    fun toPoolUserSummary(summary: NetworkPoolUserSummary): PoolUserSummary {
        return when (summary) {
            is NetworkPoolUserSummary.Win -> PoolUserSummary.Won(summary.amountWon, summary.amountReceived)
            is NetworkPoolUserSummary.Lose -> PoolUserSummary.Lost(summary.amount)
            is NetworkPoolUserSummary.Refund -> PoolUserSummary.Refunded(summary.amount)
            NetworkPoolUserSummary.NotSet -> PoolUserSummary.NotSet
        }
    }

    @TypeConverter
    fun toPoolUserSummary(value: String?): PoolUserSummary {
        val parts = value?.split("_").orEmpty()
        return when (parts[0]) {
            "not-set" -> PoolUserSummary.NotSet
            "won" -> {
                val wonQuarks = parts[1].toLong()
                val wonCurrency = parts[2].let { CurrencyCode.tryValueOf(it) } ?: CurrencyCode.USD
                val receivedQuarks = parts.getOrNull(3)?.toLong()
                val receivedCurrency = parts.getOrNull(4)?.let { CurrencyCode.tryValueOf(it) } ?: CurrencyCode.USD

                val amountWon = Fiat(wonQuarks, wonCurrency)
                val amountReceived = if (receivedQuarks != null) {
                    Fiat(receivedQuarks, receivedCurrency)
                } else {
                    Fiat.Zero
                }
                PoolUserSummary.Won(amountWon, amountReceived)
            }
            "lost" -> {
                val quarks = parts[1].toLong()
                val currency = parts[2].let { CurrencyCode.tryValueOf(it) } ?: CurrencyCode.USD
                PoolUserSummary.Lost(Fiat(quarks, currency))
            }
            "refunded" -> {
                val quarks = parts[1].toLong()
                val currency = parts[2].let { CurrencyCode.tryValueOf(it) } ?: CurrencyCode.USD
                PoolUserSummary.Refunded(Fiat(quarks, currency))
            }
            else -> throw IllegalArgumentException("Unknown PoolBetSummary type: $value")
        }
    }
}