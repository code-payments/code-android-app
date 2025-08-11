package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.codeinc.flipcash.gen.pool.v1.pagingTokenOrNull
import com.codeinc.flipcash.gen.pool.v1.userSummaryOrNull
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBetSummary
import com.flipcash.services.models.NetworkPoolUserSummary
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import javax.inject.Inject

class PoolMapper @Inject constructor(
    private val metadataMapper: PoolMetadataMapper,
    private val betMapper: PoolBetMapper
): Mapper<Model.PoolMetadata, NetworkPool> {
    override fun map(from: Model.PoolMetadata): NetworkPool {
        val metadata = metadataMapper.map(from.verifiedMetadata)
        return NetworkPool(
            metadata = metadata,
            rendezvousSignature = from.rendezvousSignature.value.toList(),
            bets = from.betsList.map { betMapper.map(it) },
            pagingToken = from.pagingTokenOrNull?.value?.toList(),
            isFundingDestinationInitialized = from.isFundingDestinationInitialized,
            derivationIndex = from.derivationIndex,
            betSummary = when (from.betSummary.kindCase) {
                Model.BetSummary.KindCase.KIND_NOT_SET -> NetworkPoolBetSummary.NotSet
                Model.BetSummary.KindCase.BOOLEAN_SUMMARY -> NetworkPoolBetSummary.Boolean(
                    yes = from.betSummary.booleanSummary.numYes,
                    no = from.betSummary.booleanSummary.numNo
                )
            },
            userSummary = from.userSummaryOrNull?.let {
                when (it.outcomeCase) {
                    Model.UserPoolSummary.OutcomeCase.WIN -> {
                        val amountWon = Fiat(
                            fiat = from.userSummary.win.amountWon.nativeAmount,
                            currencyCode = CurrencyCode.Companion.tryValueOf(from.userSummary.win.amountWon.currency)
                                ?: CurrencyCode.USD,
                        )
                        val amountReceived = Fiat(
                            fiat = from.userSummary.win.totalAmountReceived.nativeAmount,
                            currencyCode = CurrencyCode.Companion.tryValueOf(from.userSummary.win.totalAmountReceived.currency)
                                ?: CurrencyCode.USD,
                        )
                        NetworkPoolUserSummary.Win(amountWon, amountReceived)
                    }
                    Model.UserPoolSummary.OutcomeCase.LOSE -> {
                        val amountRefunded = Fiat(
                            fiat = from.userSummary.lose.amountLost.nativeAmount,
                            currencyCode = CurrencyCode.Companion.tryValueOf(from.userSummary.lose.amountLost.currency)
                                ?: CurrencyCode.USD,
                        )
                        NetworkPoolUserSummary.Lose(amountRefunded)
                    }
                    Model.UserPoolSummary.OutcomeCase.REFUND -> {
                        val amountRefunded = Fiat(
                            fiat = from.userSummary.refund.amountRefunded.nativeAmount,
                            currencyCode = CurrencyCode.Companion.tryValueOf(from.userSummary.refund.amountRefunded.currency)
                                ?: CurrencyCode.USD,
                        )
                        NetworkPoolUserSummary.Refund(amountRefunded)
                    }
                    Model.UserPoolSummary.OutcomeCase.NONE,
                    Model.UserPoolSummary.OutcomeCase.OUTCOME_NOT_SET -> NetworkPoolUserSummary.NotSet
                }
            }
        )
    }
}