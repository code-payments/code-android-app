package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.currency.v1.CurrencyGrpcKt
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.annotations.OpenCodeManagedStreamingChannel
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asProto
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.TokenUpdateRequest
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.toByteString
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CurrencyApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,

    @OpenCodeManagedStreamingChannel
    streamingChannel: ManagedChannel,
) : GrpcApi(managedChannel, streamingChannel) {

    private val api = CurrencyGrpcKt.CurrencyCoroutineStub(managedChannel)
        .withWaitForReady()

    private val streamingApi = CurrencyGrpcKt.CurrencyCoroutineStub(streamingChannel)
        .withWaitForReady()

    /**
     * Gets mint account metadata by address
     *
     * @param mintAddresses The list of mint addresses to query
     *
     * @return The [CurrencyService.GetMintsResponse] with mint account metadata by address
     */
    suspend fun getMints(
        mintAddresses: List<PublicKey>
    ): CurrencyService.GetMintsResponse {
        val request = CurrencyService.GetMintsRequest.newBuilder()
            .apply {
                mintAddresses.forEachIndexed { index, address ->
                    addAddresses(index, address.asSolanaAccountId())
                }
            }.build()

        return withContext(Dispatchers.IO) {
            api.getMints(request)
        }
    }

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): CurrencyService.GetHistoricalMintDataResponse {
        val request = CurrencyService.GetHistoricalMintDataRequest.newBuilder()
            .setAddress(mint.asSolanaAccountId())
            .setCurrencyCode(currencyCode.name.lowercase())
            .setPredefinedRange(
                when (windowedRange) {
                    WindowedRange.AllTime -> CurrencyService.PredefinedRange.ALL_TIME
                    WindowedRange.LastDay -> CurrencyService.PredefinedRange.LAST_DAY
                    WindowedRange.LastWeek -> CurrencyService.PredefinedRange.LAST_WEEK
                    WindowedRange.LastMonth -> CurrencyService.PredefinedRange.LAST_MONTH
                    WindowedRange.LastYear -> CurrencyService.PredefinedRange.LAST_YEAR
                }
            ).build()

        return withContext(Dispatchers.IO) {
            api.getHistoricalMintData(request)
        }
    }

    fun streamLiveMintData(
        requests: Flow<CurrencyService.StreamLiveMintDataRequest>
    ): Flow<CurrencyService.StreamLiveMintDataResponse> {
        return streamingApi.streamLiveMintData(requests)
    }

    suspend fun checkTokenAvailability(name: String): CurrencyService.CheckAvailabilityResponse {
        val request = CurrencyService.CheckAvailabilityRequest.newBuilder()
            .setName(name)
            .build()

        return withContext(Dispatchers.IO) {
            api.checkAvailability(request)
        }
    }

    suspend fun launchToken(
        name: String,
        symbol: String,
        bill: TokenBillCustomizations?,
        icon: ByteArray?,
        owner: Ed25519.KeyPair
    ): CurrencyService.LaunchResponse {
        val request = CurrencyService.LaunchRequest.newBuilder()
            .setName(name)
            .setSymbol(symbol)
            .apply apply@{
                if (bill != null) {
                    setBillCustomization(bill.asProto())
                }

                if (icon != null) {
                    setIcon(icon.toByteString())
                }
            }
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.launch(request)
        }
    }

    suspend fun updateIcon(
        update: TokenUpdateRequest.Icon,
        owner: Ed25519.KeyPair
    ): CurrencyService.UpdateIconResponse {
        val request = CurrencyService.UpdateIconRequest.newBuilder()
            .setMint(update.token.address.asSolanaAccountId())
            .setIcon(update.icon.toByteString())
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.updateIcon(request)
        }
    }

    suspend fun updateMetadata(
        update: TokenUpdateRequest.Metadata,
        owner: Ed25519.KeyPair
    ): CurrencyService.UpdateMetadataResponse {
        val request = CurrencyService.UpdateMetadataRequest.newBuilder()
            .setMint(update.token.address.asSolanaAccountId())
            .setOwner(owner.asSolanaAccountId())
            .apply {
                if (update.billCustomization != null) {
                    setNewBillCustomization(
                        CurrencyService.UpdateMetadataRequest.BillCustomizationUpdate.newBuilder()
                            .setValue(update.billCustomization.asProto())
                            .build()
                    )
                }

                if (update.description != null) {
                    setNewDescription(
                        CurrencyService.UpdateMetadataRequest.DescriptionUpdate.newBuilder()
                            .setValue(update.description)
                    )
                }

                if (update.socialLinks != null) {
                    setNewSocialLinks(
                        CurrencyService.UpdateMetadataRequest.SocialLinksUpdate.newBuilder()
                            .apply {
                                val proto =
                                    CurrencyService.UpdateMetadataRequest.SocialLinksUpdate.newBuilder()
                                update.socialLinks.mapIndexed { index, socialLink ->
                                    proto.setValue(index, socialLink.asProto())
                                }
                                setNewSocialLinks(proto)
                            }
                    )
                }
            }
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.updateMetadata(request)
        }
    }

    suspend fun discoverTokens(
        category: DiscoverCategory
    ): CurrencyService.DiscoverResponse {
        val request = CurrencyService.DiscoverRequest.newBuilder()
            .setCategory(
                when (category) {
                    DiscoverCategory.Popular -> CurrencyService.DiscoverRequest.Category.POPULAR
                    DiscoverCategory.New -> CurrencyService.DiscoverRequest.Category.NEW
                }
            )
            .build()

        return withContext(Dispatchers.IO) {
            // Collect streamed responses into a single aggregated response
            val allMints = mutableListOf<CurrencyService.Mint>()
            var lastResult = CurrencyService.DiscoverResponse.Result.OK

            streamingApi.discover(request).collect { response ->
                lastResult = response.result
                allMints += response.mintsList
            }

            CurrencyService.DiscoverResponse.newBuilder()
                .setResult(lastResult)
                .addAllMints(allMints)
                .build()
        }
    }
}