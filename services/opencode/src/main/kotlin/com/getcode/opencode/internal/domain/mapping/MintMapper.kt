package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.currency.v1.billCustomizationOrNull
import com.codeinc.opencode.gen.currency.v1.holderMetricsOrNull
import com.codeinc.opencode.gen.currency.v1.launchpadMetadataOrNull
import com.getcode.opencode.internal.network.extensions.toMint
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import javax.inject.Inject
import kotlin.time.Instant

internal class MintMapper @Inject constructor(
    private val vmMetadataMapper: VmMetadataMapper,
    private val launchpadMetadataMapper: LaunchpadMetadataMapper,
    private val socialLinkMapper: SocialLinkMapper,
    private val customizationMapper: BillCustomizationMapper,
    private val holderMetricsMapper: HolderMetricsMapper,
) : Mapper<CurrencyService.Mint, MintMetadata> {
    override fun map(from: CurrencyService.Mint): MintMetadata {
        val mint = from.address.toMint()
        val vmMetadata = vmMetadataMapper.map(from.vmMetadata)
        val launchpadMetadata = from.launchpadMetadataOrNull?.let {
            launchpadMetadataMapper.map(it)
        }

        // Handle the provided `createdAt` and if it's valid use it, otherwise
        // do a mint check against USDF and return a well known mint date for it
        // otherwise return null
        val mintDate = from.createdAt.seconds.takeIf { it > 0 }
            .let { at ->
                if (at == null && mint == Mint.usdf) {
                    Token.usdf.createdAt
                } else {
                    at?.let { Instant.fromEpochMilliseconds(it * 1_000L) }
                }
            }

        return MintMetadata(
            address = mint,
            decimals = from.decimals,
            name = from.name,
            createdAt = mintDate,
            symbol = from.symbol,
            description = from.description,
            imageUrl = from.imageUrl,
            vmMetadata = vmMetadata,
            launchpadMetadata = launchpadMetadata,
            socialLinks = from.socialLinksList.mapNotNull(socialLinkMapper::map),
            billCustomizations = customizationMapper.map(from.billCustomizationOrNull),
            holderMetrics = from.holderMetricsOrNull?.let { holderMetricsMapper.map(it) } ?: HolderMetrics.None,
        )
    }
}