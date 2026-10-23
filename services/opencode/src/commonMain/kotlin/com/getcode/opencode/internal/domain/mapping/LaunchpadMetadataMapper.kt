package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.internal.network.extensions.toMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.solana.keys.Mint
import javax.inject.Inject

class LaunchpadMetadataMapper @Inject constructor():
    Mapper<OcpCurrencyService.LaunchpadMetadata, LaunchpadMetadata> {
    override fun map(from: OcpCurrencyService.LaunchpadMetadata): LaunchpadMetadata {
        return from.toMetadata()
    }
}