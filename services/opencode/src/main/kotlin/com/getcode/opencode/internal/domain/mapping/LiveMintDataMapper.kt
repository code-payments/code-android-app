package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.financial.VerifiedResponseData
import com.getcode.opencode.internal.network.extensions.toMint
import com.getcode.opencode.internal.network.extensions.toSignature
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.LaunchpadReserveStateSnapshot
import com.getcode.opencode.model.financial.Rate
import com.getcode.util.toInstantFromMillis
import javax.inject.Inject

internal class LiveMintDataMapper @Inject constructor(): Mapper<OcpCurrencyService.StreamLiveMintDataResponse.LiveData, LiveMintDataResponse?> {
    override fun map(from: OcpCurrencyService.StreamLiveMintDataResponse.LiveData): LiveMintDataResponse? {
        return when (from.typeCase) {
            OcpCurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.CORE_MINT_FIAT_EXCHANGE_RATES -> {
                val verifiedRates = from.coreMintFiatExchangeRates.exchangeRatesList.mapNotNull { verifiedData ->
                    val fx = verifiedData.exchangeRate.exchangeRate
                    val code = verifiedData.exchangeRate.currencyCode
                    val currencyCode = CurrencyCode.tryValueOf(code) ?: return@mapNotNull null
                    val timestamp = (verifiedData.exchangeRate.timestamp.seconds * 1000L).toInstantFromMillis()


                    VerifiedResponseData.ExchangeRate(
                        rate = Rate(fx = fx, currency = currencyCode),
                        timestamp = timestamp,
                        signature = verifiedData.signature.toSignature()
                    )
                }

                LiveMintDataResponse.ExchangeRates(rates = verifiedRates)
            }
            OcpCurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.LAUNCHPAD_CURRENCY_RESERVE_STATES -> {
                val verifiedStates = from.launchpadCurrencyReserveStates.reserveStatesList.map { verifiedData ->
                    val mint = verifiedData.reserveState.mint.toMint()
                    val supply = verifiedData.reserveState.supplyFromBonding
                    val timestamp = (verifiedData.reserveState.timestamp.seconds * 1000L).toInstantFromMillis()

                    VerifiedResponseData.LaunchpadReserveState(
                        reserveState = LaunchpadReserveStateSnapshot(
                            mint = mint,
                            currentSupply = supply,
                            timestamp = timestamp
                        ),
                        signature = verifiedData.signature.toSignature()
                    )
                }

                LiveMintDataResponse.LaunchpadReserveState(reserveStates = verifiedStates)
            }
            OcpCurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.TYPE_NOT_SET -> null
        }
    }
}