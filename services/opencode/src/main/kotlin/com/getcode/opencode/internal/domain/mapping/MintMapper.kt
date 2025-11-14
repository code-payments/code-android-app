package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.currency.v1.launchpadMetadataOrNull
import com.getcode.opencode.internal.network.extensions.toPublicKey
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.solana.keys.Mint
import javax.inject.Inject
import kotlin.time.Instant

internal class MintMapper @Inject constructor(
    private val vmMetadataMapper: VmMetadataMapper,
    private val launchpadMetadataMapper: LaunchpadMetadataMapper,
) : Mapper<CurrencyService.Mint, MintMetadata> {
    override fun map(from: CurrencyService.Mint): MintMetadata {
        val mint = from.address.toPublicKey()
        val vmMetadata = vmMetadataMapper.map(from.vmMetadata)
        val launchpadMetadata = from.launchpadMetadataOrNull?.let {
            launchpadMetadataMapper.map(it)
        }

        // Handle the provided `createdAt` and if it's valid use it, otherwise
        // do a mint check against USDC and return a well known mint date for it
        // otherwise return null
        val mintDate = from.createdAt.seconds.takeIf { it > 0 }
            .let { at ->
                if (at == null && mint == Mint.usdc) {
                    Token.usdc.createdAt
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
            billCustomizations = null,
        )
    }
}