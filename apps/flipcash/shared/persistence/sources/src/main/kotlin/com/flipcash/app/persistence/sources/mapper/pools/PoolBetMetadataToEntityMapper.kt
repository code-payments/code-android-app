package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.persistence.converters.BetOutcomeConverter
import com.flipcash.app.persistence.entities.PoolBetEntity
import com.flipcash.services.models.PoolBetMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import javax.inject.Inject

data class PoolBetMetadataParameters(
    val poolId: ID,
    val metadata: PoolBetMetadata,
    val hasSubmittedIntent: Boolean,
)

class PoolBetMetadataToEntityMapper @Inject constructor(): Mapper<PoolBetMetadataParameters, PoolBetEntity> {
    override fun map(from: PoolBetMetadataParameters): PoolBetEntity {
        val (poolId, metadata, hasSubmittedIntent) = from //from could also be named parameters

        return PoolBetEntity(
            idBase58 = metadata.id.base58,
            poolIdBase58 = poolId.base58,
            userIdBase58 = metadata.userId.base58,
            selectedOutcome = BetOutcomeConverter.fromBetOutcome(metadata.selectedOutcome),
            timestamp = metadata.timestamp.toEpochMilliseconds(),
            payoutDestinationBase58 = metadata.payoutDestination.base58(),
            hasSubmittedIntent = hasSubmittedIntent
        )
    }
}