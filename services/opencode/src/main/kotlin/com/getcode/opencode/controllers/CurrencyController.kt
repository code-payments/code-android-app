package com.getcode.opencode.controllers

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyController @Inject constructor(
    private val repository: CurrencyRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun getRates(
        from: Instant?
    ): Result<Map<CurrencyCode, Rate>> {
        return repository.getRates(from)
    }

    suspend fun getMints(
        addresses: List<PublicKey>
    ): Result<List<MintMetadata>> {
        return repository.getMints(addresses)
    }
}