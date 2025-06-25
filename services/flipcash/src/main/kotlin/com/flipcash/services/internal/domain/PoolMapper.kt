package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBetSummary
import com.getcode.opencode.mapper.Mapper
import javax.inject.Inject

class PoolMapper @Inject constructor(
    private val metadataMapper: PoolMetadataMapper,
    private val betMapper: PoolBetMapper
): Mapper<Model.PoolMetadata, NetworkPool> {
    override fun map(from: Model.PoolMetadata): NetworkPool {
        val metadata = metadataMapper.map(from.verifiedMetadata)
        return NetworkPool(
            metadata = metadata,
            rendezvousSignature = from.rendezvousSignature.value.toList(),
            bets = from.betsList.map { betMapper.map(it) },
            pagingToken = from.pagingToken.value.toList(),
            isFundingDestinationInitialized = from.isFundingDestinationInitialized,
            derivationIndex = from.derivationIndex,
            betSummary = when (from.betSummary.kindCase) {
                Model.BetSummary.KindCase.KIND_NOT_SET -> NetworkPoolBetSummary.NotSet
                Model.BetSummary.KindCase.BOOLEAN_SUMMARY -> NetworkPoolBetSummary.Boolean(
                    yes = from.betSummary.booleanSummary.numYes,
                    no = from.betSummary.booleanSummary.numNo
                )
            }
        )
    }
}