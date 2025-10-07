package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.network.extensions.toMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.VmMetadata
import jakarta.inject.Inject

internal class VmMetadataMapper @Inject constructor(
): Mapper<CurrencyService.VmMetadata, VmMetadata> {
    override fun map(from: CurrencyService.VmMetadata): VmMetadata {
        return from.toMetadata()
    }
}