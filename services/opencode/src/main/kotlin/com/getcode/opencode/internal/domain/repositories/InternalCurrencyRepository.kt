package com.getcode.opencode.internal.domain.repositories

import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.PublicKey
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class InternalCurrencyRepository @Inject constructor(
    private val service: CurrencyService,
): CurrencyRepository{
    override suspend fun getRates(from: Instant?): Result<Map<CurrencyCode, Rate>> {
        return service.getRates(from)
    }

    override suspend fun getMints(addresses: List<PublicKey>): Result<List<MintMetadata>> {
        return service.getMints(addresses)
    }
}