package com.getcode.opencode.controllers

import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private data class HistoricalMintKey(
    val mint: Mint,
    val currency: CurrencyCode,
    val window: WindowedRange
)

@Singleton
class CurrencyController @Inject constructor(
    private val repository: CurrencyRepository,
) {

    private val mutexMap = ConcurrentHashMap<HistoricalMintKey, Mutex>()
    private val historicalMintMatrixCache =
        mutableMapOf<HistoricalMintKey, List<HistoricalMintData>>()

    suspend fun getRates(
        from: Instant?
    ): Result<Map<CurrencyCode, Rate>> {
        return repository.getRates(from)
    }

    suspend fun getMintMetadata(
        addresses: List<Mint>
    ): Result<List<MintMetadata>> {
        return repository.getMintMetadata(addresses)
    }

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
        skipCache: Boolean = false,
        evictAllCacheForMint: Boolean = false,
        onCacheMiss: () -> Unit = { },
    ): Result<List<HistoricalMintData>> = runCatching {
        if (evictAllCacheForMint) {
            historicalMintMatrixCache.apply {
                keys.removeIf { it.mint == mint }
            }
        }

        val key = HistoricalMintKey(mint, currencyCode, windowedRange)
        val mutex = mutexMap.getOrPut(key) { Mutex() }

        mutex.withLock {
            if (skipCache) {
                // re-check cache inside lock
                historicalMintMatrixCache[key]?.let { cached ->
                    val now = Clock.System.now()
                    val last = cached.firstOrNull()?.snapshotAt
                    if (last != null) {
                        if ((now - last) < 15.minutes) {
                            return Result.success(cached)
                        }
                    }
                }
            }

            onCacheMiss()

            // still need → fetch
            return repository.getHistoricalMintData(mint, currencyCode, windowedRange)
                .onSuccess { fresh ->
                    if (fresh.isNotEmpty()) historicalMintMatrixCache[key] = fresh
                }
        }
    }
}