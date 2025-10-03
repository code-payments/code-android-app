package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.network.extensions.toMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.LaunchpadMetadata
import javax.inject.Inject

class LaunchpadMetadataMapper @Inject constructor():
    Mapper<CurrencyService.LaunchpadMetadata, LaunchpadMetadata> {
    override fun map(from: CurrencyService.LaunchpadMetadata): LaunchpadMetadata {
        return from.toMetadata()
    }
}