package com.getcode.opencode.controllers

import com.getcode.opencode.internal.model.LiveMintDataResponse
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class CurrencyController @Inject constructor(
    private val repository: CurrencyRepository,
) {
    fun streamLiveMintData(
        scope: CoroutineScope,
        mints: Flow<List<Mint>>,
        tag: String? = null,
    ): Flow<LiveMintDataResponse> {
        return mints.flatMapLatest { mintList ->
            if (mintList.isEmpty()) {
                emptyFlow()
            } else {
                callbackFlow {
                    val reference = repository.streamMintData(scope = scope, mints = mintList, tag = tag) {
                        scope.launch {
                            send(it)
                        }
                    }
                    awaitClose {
                        reference.cancel()
                    }
                }.shareIn(
                    scope = scope,
                    started = SharingStarted.Lazily,
                    replay = 1
                )
            }
        }
    }

    suspend fun getLiveMintData(
        scope: CoroutineScope,
        mint: Mint,
        tag: String? = null
    ): Result<LiveMintDataResponse> = runCatching {
        callbackFlow {
            val reference = repository.streamMintData(scope = scope, mints = listOf(mint), tag = tag) {
                trySend(it)
            }
            awaitClose { reference.cancel() }
        }.first()
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
    ): Result<List<HistoricalMintData>> = runCatching {
        return repository.getHistoricalMintData(mint, currencyCode, windowedRange)
    }
}