package com.flipcash.app.persistence.sources.mapper.pools

import com.flipcash.app.persistence.BetOutcomeConverter
import com.flipcash.app.persistence.entities.PoolBetEntity
import com.flipcash.services.models.PoolBetMetadata
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import javax.inject.Inject

class PoolBetMetadataToEntityMapper @Inject constructor(): Mapper<Pair<ID, PoolBetMetadata>, PoolBetEntity> {
    override fun map(from: Pair<ID, PoolBetMetadata>): PoolBetEntity {
        val (poolId, metadata) = from

        return PoolBetEntity(
            idBase58 = metadata.id.base58,
            poolIdBase58 = poolId.base58,
            userIdBase58 = metadata.userId.base58,
            selectedOutcome = BetOutcomeConverter.fromBetOutcome(metadata.selectedOutcome),
            timestamp = metadata.timestamp.toEpochMilliseconds(),
            payoutDestinationBase58 = metadata.payoutDestination.base58()
        )
    }
}