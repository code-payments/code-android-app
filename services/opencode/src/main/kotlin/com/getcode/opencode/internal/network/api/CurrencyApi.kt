package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.currency.v1.CurrencyGrpcKt
import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.codeinc.opencode.gen.currency.v1.validate
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
import com.getcode.opencode.model.financial.TokenCreateRequest
import com.getcode.opencode.model.financial.TokenUpdateRequest
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.toByteString
import dev.bmcreations.protovalidate.orThrow
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
     * @return The [OcpCurrencyService.GetMintsResponse] with mint account metadata by address
     */
    suspend fun getMints(
        mintAddresses: List<PublicKey>
    ): OcpCurrencyService.GetMintsResponse {
        val request = OcpCurrencyService.GetMintsRequest.newBuilder()
            .apply {
                mintAddresses.forEachIndexed { index, address ->
                    addAddresses(index, address.asSolanaAccountId())
                }
            }.build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getMints(request)
        }
    }

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): OcpCurrencyService.GetHistoricalMintDataResponse {
        val request = OcpCurrencyService.GetHistoricalMintDataRequest.newBuilder()
            .setAddress(mint.asSolanaAccountId())
            .setCurrencyCode(currencyCode.name.lowercase())
            .setPredefinedRange(
                when (windowedRange) {
                    WindowedRange.AllTime -> OcpCurrencyService.PredefinedRange.ALL_TIME
                    WindowedRange.LastDay -> OcpCurrencyService.PredefinedRange.LAST_DAY
                    WindowedRange.LastWeek -> OcpCurrencyService.PredefinedRange.LAST_WEEK
                    WindowedRange.LastMonth -> OcpCurrencyService.PredefinedRange.LAST_MONTH
                    WindowedRange.LastYear -> OcpCurrencyService.PredefinedRange.LAST_YEAR
                }
            ).build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getHistoricalMintData(request)
        }
    }

    fun streamLiveMintData(
        requests: Flow<OcpCurrencyService.StreamLiveMintDataRequest>
    ): Flow<OcpCurrencyService.StreamLiveMintDataResponse> {
        return streamingApi.streamLiveMintData(requests)
    }

    suspend fun checkTokenAvailability(name: String): OcpCurrencyService.CheckAvailabilityResponse {
        val request = OcpCurrencyService.CheckAvailabilityRequest.newBuilder()
            .setName(name.trim())
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.checkAvailability(request)
        }
    }

    suspend fun launchToken(
        request: TokenCreateRequest,
        owner: Ed25519.KeyPair
    ): OcpCurrencyService.LaunchResponse {
        println(request)
        val request = OcpCurrencyService.LaunchRequest.newBuilder()
            .setName(request.name.text)
            .setNameModerationAttestation(request.name.asProto())
            .apply apply@{
                if (request.symbol != null) {
                    setSymbol(request.symbol.text)
                    setSymbolModerationAttestation(request.symbol.asProto())
                }

                if (request.description != null) {
                    setDescription(request.description.text)
                    setDescriptionModerationAttestation(request.description.asProto())
                }

                if (request.icon != null) {
                    setIcon(request.icon.imageBytes.toByteString())
                    setIconModerationAttestation(request.icon.asProto())
                }

                if (request.bill != null) {
                    setBillCustomization(request.bill.asProto())
                }
            }
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.launch(request)
        }
    }

    suspend fun updateIcon(
        update: TokenUpdateRequest.Icon,
        owner: Ed25519.KeyPair
    ): OcpCurrencyService.UpdateIconResponse {
        val request = OcpCurrencyService.UpdateIconRequest.newBuilder()
            .setMint(update.token.address.asSolanaAccountId())
            .setIcon(update.icon.imageBytes.toByteString())
            .setModerationAttestation(update.icon.asProto())
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.updateIcon(request)
        }
    }

    suspend fun updateMetadata(
        update: TokenUpdateRequest.Metadata,
        owner: Ed25519.KeyPair
    ): OcpCurrencyService.UpdateMetadataResponse {
        val request = OcpCurrencyService.UpdateMetadataRequest.newBuilder()
            .setMint(update.token.address.asSolanaAccountId())
            .setOwner(owner.asSolanaAccountId())
            .apply {
                if (update.billCustomization != null) {
                    setNewBillCustomization(
                        OcpCurrencyService.UpdateMetadataRequest.BillCustomizationUpdate.newBuilder()
                            .setValue(update.billCustomization.asProto())
                            .build()
                    )
                }

                if (update.description != null) {
                    setNewDescription(
                        OcpCurrencyService.UpdateMetadataRequest.DescriptionUpdate.newBuilder()
                            .setValue(update.description.text)
                            .setModerationAttestation(update.description.asProto())
                    )
                }

                if (update.socialLinks != null) {
                    setNewSocialLinks(
                        OcpCurrencyService.UpdateMetadataRequest.SocialLinksUpdate.newBuilder()
                            .apply {
                                val proto =
                                    OcpCurrencyService.UpdateMetadataRequest.SocialLinksUpdate.newBuilder()
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

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.updateMetadata(request)
        }
    }

    suspend fun discoverTokens(
        category: DiscoverCategory
    ): OcpCurrencyService.DiscoverResponse {
        val request = OcpCurrencyService.DiscoverRequest.newBuilder()
            .setCategory(
                when (category) {
                    DiscoverCategory.Popular -> OcpCurrencyService.DiscoverRequest.Category.POPULAR
                    DiscoverCategory.New -> OcpCurrencyService.DiscoverRequest.Category.NEW
                }
            )
            .build()

        return withContext(Dispatchers.IO) {
            // Collect streamed responses into a single aggregated response
            val allMints = mutableListOf<OcpCurrencyService.Mint>()
            var lastResult = OcpCurrencyService.DiscoverResponse.Result.OK

            streamingApi.discover(request).collect { response ->
                lastResult = response.result
                allMints += response.mintsList
            }

            OcpCurrencyService.DiscoverResponse.newBuilder()
                .setResult(lastResult)
                .addAllMints(allMints)
                .build()
        }
    }
}