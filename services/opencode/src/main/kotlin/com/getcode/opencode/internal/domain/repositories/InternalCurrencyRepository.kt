package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.model.core.errors.DiscoverTokensError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenCreateRequest
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

internal class InternalCurrencyRepository @Inject constructor(
    private val service: CurrencyService,
) : CurrencyRepository {

    override fun streamMintData(
        scope: CoroutineScope,
        mints: List<Mint>,
        tag: String?,
        onUpdate: (LiveMintDataResponse) -> Unit
    ) = service.streamLiveMintData(tag = tag, scope = scope, mints = mints, onUpdate = onUpdate)

    override suspend fun getMintMetadata(addresses: List<Mint>): Result<List<MintMetadata>> =
        service.getMints(addresses)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange
    ): Result<List<HistoricalMintData>> =
        service.getHistoricalMintData(mint, currencyCode, windowedRange)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun discoverTokens(category: DiscoverCategory): Result<List<Token>> =
        service.discover(category)
            .onFailure { error ->
                if (error !is DiscoverTokensError.NotFound) {
                    ErrorUtils.handleError(error)
                }
            }

    override suspend fun checkTokenAvailability(name: String): Result<Boolean> =
        service.checkTokenAvailability(name.trim())
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun launchToken(
        request: TokenCreateRequest,
        owner: Ed25519.KeyPair
    ): Result<Mint> {
        return service.launchNewToken(request, owner)
            .onFailure { ErrorUtils.handleError(it) }
    }
}