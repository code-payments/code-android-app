package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.domain.mapping.HistoricalMintDataMapper
import com.getcode.opencode.internal.domain.mapping.LiveMintDataMapper
import com.getcode.opencode.internal.domain.mapping.MintMapper
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.internal.network.api.CurrencyApi
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.internal.network.extensions.toMint
import com.getcode.opencode.internal.network.streamers.LiveMintDataStreamer
import com.getcode.opencode.internal.network.streamers.ManagedMintStream
import com.getcode.opencode.model.core.errors.CheckTokenAvailabilityError
import com.getcode.opencode.model.core.errors.DiscoverTokensError
import com.getcode.opencode.model.core.errors.GetHistoricalMintDataError
import com.getcode.opencode.model.core.errors.GetMintsError
import com.getcode.opencode.model.core.errors.LaunchTokenError
import com.getcode.opencode.model.core.errors.UpdateIconError
import com.getcode.opencode.model.core.errors.UpdateMetadataError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.TokenUpdateRequest
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

internal class CurrencyService @Inject constructor(
    private val api: CurrencyApi,
    private val mintMapper: MintMapper,
    private val historicalMintDataMapper: HistoricalMintDataMapper,
    private val liveMintDataMapper: LiveMintDataMapper,
    private val verifiedStateManager: VerifiedProtoManager,
) {
    suspend fun getMints(
        mintAddresses: List<PublicKey>
    ): Result<List<MintMetadata>> {
        return runCatching {
            api.getMints(mintAddresses)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    CurrencyService.GetMintsResponse.Result.OK -> {
                        val mints = response.metadataByAddressMap.values.toList()
                            .map { mintMapper.map(it) }

                        Result.success(mints)
                    }
                    CurrencyService.GetMintsResponse.Result.NOT_FOUND -> Result.failure(GetMintsError.NotFound())
                    CurrencyService.GetMintsResponse.Result.UNRECOGNIZED -> Result.failure(GetMintsError.Unrecognized())
                    else -> Result.failure(GetMintsError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetMintsError.Other(cause = cause))
            }
        )
    }

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> {
        return runCatching {
            api.getHistoricalMintData(
                mint = mint,
                currencyCode = currencyCode,
                windowedRange = windowedRange,
            )
        }.foldWithSuppression(
            onSuccess = { response ->
                when (val result = response.result) {
                    CurrencyService.GetHistoricalMintDataResponse.Result.OK -> {
                        val data = response.dataList.map { historicalMintDataMapper.map(it) }
                        Result.success(data)
                    }
                    CurrencyService.GetHistoricalMintDataResponse.Result.NOT_FOUND -> Result.failure(GetHistoricalMintDataError.NotFound())
                    CurrencyService.GetHistoricalMintDataResponse.Result.MISSING_DATA -> Result.failure(GetHistoricalMintDataError.MissingData())
                    CurrencyService.GetHistoricalMintDataResponse.Result.UNRECOGNIZED -> Result.failure(GetHistoricalMintDataError.Unrecognized())
                    else -> Result.failure(GetHistoricalMintDataError.Other())
                }

            },
            onFailure = { cause ->
                Result.failure(GetHistoricalMintDataError.Other(cause))
            }
        )
    }

    fun streamLiveMintData(
        tag: String?,
        scope: CoroutineScope,
        mints: List<Mint>,
        onUpdate: (LiveMintDataResponse) -> Unit
    ): ManagedMintStream {
        val streamer = LiveMintDataStreamer(api)
        return streamer.stream(scope = scope, mints = mints, tag = tag) { update ->
            // save protos for later use
            when (update.typeCase) {
                CurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.CORE_MINT_FIAT_EXCHANGE_RATES -> verifiedStateManager.saveRates(update.coreMintFiatExchangeRates.exchangeRatesList)
                CurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.LAUNCHPAD_CURRENCY_RESERVE_STATES -> verifiedStateManager.saveReserveStates(update.launchpadCurrencyReserveStates.reserveStatesList)
                CurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.TYPE_NOT_SET -> Unit
            }

            // map to domain models for use throughout app (above server)
            val data = liveMintDataMapper.map(update)
            if (data != null) {
                onUpdate(data)
            }
        }
    }

    suspend fun checkTokenAvailability(name: String): Result<Boolean> {
        return runCatching {
            api.checkTokenAvailability(name)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    CurrencyService.CheckAvailabilityResponse.Result.OK -> Result.success(response.isAvailable)
                    CurrencyService.CheckAvailabilityResponse.Result.UNRECOGNIZED -> Result.failure(CheckTokenAvailabilityError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(CheckTokenAvailabilityError.Other(cause))
            }
        )
    }

    suspend fun launchNewToken(
        name: ModerationAttestation.Text,
        symbol: ModerationAttestation.Text?,
        description: ModerationAttestation.Text?,
        bill: TokenBillCustomizations?,
        icon: ModerationAttestation.Image?,
        owner: Ed25519.KeyPair
    ): Result<Mint> {
        return runCatching {
            api.launchToken(name, symbol, description, bill, icon, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    CurrencyService.LaunchResponse.Result.OK -> Result.success(response.mint.toMint())
                    CurrencyService.LaunchResponse.Result.DENIED -> Result.failure(LaunchTokenError.Denied())
                    CurrencyService.LaunchResponse.Result.NAME_EXISTS -> Result.failure(LaunchTokenError.Exists())
                    CurrencyService.LaunchResponse.Result.INVALID_ICON -> Result.failure(LaunchTokenError.InvalidIcon())
                    CurrencyService.LaunchResponse.Result.UNRECOGNIZED -> Result.failure(LaunchTokenError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(LaunchTokenError.Other(cause))
            }
        )
    }

    suspend fun updateIcon(
        updateRequest: TokenUpdateRequest.Icon,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.updateIcon(updateRequest, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    CurrencyService.UpdateIconResponse.Result.OK -> Result.success(Unit)
                    CurrencyService.UpdateIconResponse.Result.NOT_FOUND -> Result.failure(UpdateIconError.NotFound())
                    CurrencyService.UpdateIconResponse.Result.DENIED -> Result.failure(UpdateIconError.Denied())
                    CurrencyService.UpdateIconResponse.Result.INVALID_ICON -> Result.failure(UpdateIconError.InvalidIcon())
                    CurrencyService.UpdateIconResponse.Result.UNRECOGNIZED -> Result.failure(UpdateIconError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(UpdateIconError.Other(cause = cause))
            }
        )
    }

    suspend fun updateMetadata(
        updateRequest: TokenUpdateRequest.Metadata,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return runCatching {
            api.updateMetadata(updateRequest, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    CurrencyService.UpdateMetadataResponse.Result.OK -> Result.success(Unit)
                    CurrencyService.UpdateMetadataResponse.Result.NOT_FOUND -> Result.failure(UpdateMetadataError.NotFound())
                    CurrencyService.UpdateMetadataResponse.Result.DENIED -> Result.failure(UpdateMetadataError.Denied())
                    CurrencyService.UpdateMetadataResponse.Result.UNRECOGNIZED -> Result.failure(UpdateMetadataError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(UpdateMetadataError.Other(cause = cause))
            }
        )
    }

    suspend fun discover(
        category: DiscoverCategory
    ): Result<List<MintMetadata>> {
        return runCatching {
            api.discoverTokens(category)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    CurrencyService.DiscoverResponse.Result.OK -> {
                        val mints = response.mintsList.map(mintMapper::map)
                        Result.success(mints)
                    }
                    CurrencyService.DiscoverResponse.Result.NOT_FOUND -> Result.failure(DiscoverTokensError.NotFound())
                    CurrencyService.DiscoverResponse.Result.UNRECOGNIZED -> Result.failure(DiscoverTokensError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(DiscoverTokensError.Other(cause = cause))
            }
        )
    }
}