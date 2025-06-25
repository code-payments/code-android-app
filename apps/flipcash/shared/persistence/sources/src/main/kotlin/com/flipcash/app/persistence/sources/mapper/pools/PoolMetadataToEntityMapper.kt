package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.persistence.converters.PoolBetSummaryConverter
import com.flipcash.app.persistence.converters.PoolResolutionConverter
import com.flipcash.app.persistence.entities.PoolEntity
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolBetSummary
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.PoolMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import com.getcode.utils.decodeBase64
import javax.inject.Inject

data class PoolMetadataMappingParameters(
    val metadata: PoolMetadata,
    val pagingToken: PagingToken?,
    val selectedOutcome: NetworkPoolBetOutcome?,
    val derivationIndex: Long,
    val rendezvousSignature: List<Byte>?,
    val betSummary: NetworkPoolBetSummary,
)
class PoolMetadataToEntityMapper @Inject constructor(): Mapper<PoolMetadataMappingParameters, PoolEntity> {
    override fun map(from: PoolMetadataMappingParameters): PoolEntity {
        val (metadata, pagingToken, selectedOutcome, derivationIndex, signature, summary) = from

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
            didWin = metadata.resolution.didWin(selectedOutcome),
            pagingTokenBase58 = pagingToken?.base58,
            didBet = selectedOutcome != null,
            derivationIndex = derivationIndex,
            rendezvousSignature = signature?.base58,
            betSummary = PoolBetSummaryConverter.fromPoolBetSummary(summary)
        )
    }
}