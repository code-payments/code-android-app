package com.getcode.opencode.repositories

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.PublicKey
import kotlinx.datetime.Instant

interface CurrencyRepository {
    suspend fun getRates(from: Instant?): Result<Map<CurrencyCode, Rate>>
    suspend fun getMints(addresses: List<PublicKey>): Result<List<MintMetadata>>
}