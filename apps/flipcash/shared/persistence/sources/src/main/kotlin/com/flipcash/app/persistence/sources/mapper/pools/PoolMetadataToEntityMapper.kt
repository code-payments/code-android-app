package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.persistence.converters.PoolBetSummaryConverter
import com.flipcash.app.persistence.converters.PoolResolutionConverter
import com.flipcash.app.persistence.converters.PoolUserSummaryConverter
import com.flipcash.app.persistence.entities.PoolEntity
import com.flipcash.services.models.NetworkPoolBetSummary
import com.flipcash.services.models.NetworkPoolUserSummary
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.PoolMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import javax.inject.Inject

data class PoolMetadataMappingParameters(
    val metadata: PoolMetadata,
    val pagingToken: PagingToken?,
    val derivationIndex: Long,
    val rendezvousSignature: List<Byte>,
    val betSummary: NetworkPoolBetSummary,
    val userSummary: NetworkPoolUserSummary,
)
class PoolMetadataToEntityMapper @Inject constructor(): Mapper<PoolMetadataMappingParameters, PoolEntity> {
    override fun map(from: PoolMetadataMappingParameters): PoolEntity {
        val (metadata, pagingToken, derivationIndex, signature, betSummary, userSummary) = from

        return PoolEntity(
            idBase58 = metadata.id.base58,
            creatorBase58 = metadata.creator.base58,
            name = metadata.name,
            buyInAmount = metadata.buyIn.quarks,
            buyInCurrency = metadata.buyIn.currencyCode.name,
            fundingDestinationBase58 = metadata.fundingDestination.base58(),
            isOpen = metadata.isOpen,
            resolution = PoolResolutionConverter.fromPoolResolution(metadata.resolution),
            timestamp = metadata.createdAt.toEpochMilliseconds(),
            closedTimestamp = metadata.closedAt?.toEpochMilliseconds(),
            pagingTokenBase58 = pagingToken?.base58,
            derivationIndex = derivationIndex,
            rendezvousSignature = signature.base58,
            betSummary = PoolBetSummaryConverter.fromPoolBetSummary(betSummary),
            userSummary = PoolUserSummaryConverter.fromPoolUserSummary(userSummary),
        )
    }
}