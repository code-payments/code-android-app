package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.currency.v1.launchpadMetadataOrNull
import com.codeinc.opencode.gen.currency.v1.vmMetadataOrNull
import com.getcode.opencode.internal.network.extensions.toPublicKey
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.MintMetadata
import javax.inject.Inject

internal class MintMapper @Inject constructor(
    private val vmMetadataMapper: VmMetadataMapper,
    private val launchpadMetadataMapper: LaunchpadMetadataMapper,
) : Mapper<CurrencyService.Mint, MintMetadata> {
    override fun map(from: CurrencyService.Mint): MintMetadata {
        val vmMetadata = from.vmMetadataOrNull ?.let {
            vmMetadataMapper.map(it)
        }

        val launchpadMetadata = from.launchpadMetadataOrNull ?.let {
            launchpadMetadataMapper.map(it)
        }

        return MintMetadata(
            address = from.address.toPublicKey(),
            decimals = from.decimals,
            name = from.name,
            symbol = from.symbol,
            description = from.description,
            imageUrl = from.imageUrl,
            vmMetadata = vmMetadata,
            launchpadMetadata = launchpadMetadata
        )
    }
}