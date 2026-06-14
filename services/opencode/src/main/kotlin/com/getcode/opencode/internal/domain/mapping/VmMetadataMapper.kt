package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.internal.network.extensions.toMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.VmMetadata
import jakarta.inject.Inject

internal class VmMetadataMapper @Inject constructor(
): Mapper<OcpCurrencyService.VmMetadata, VmMetadata> {
    override fun map(from: OcpCurrencyService.VmMetadata): VmMetadata {
        return from.toMetadata()
    }
}