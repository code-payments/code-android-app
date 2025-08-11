package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolBetOutcome.BooleanOutcome
import com.flipcash.services.models.PoolBetMetadata
import kotlinx.datetime.Instant
import javax.inject.Inject

class PoolBetMetadataMapper @Inject constructor(): Mapper<Model.SignedBetMetadata, PoolBetMetadata> {
    override fun map(from: Model.SignedBetMetadata): PoolBetMetadata {
        return PoolBetMetadata(
            id = from.betId.toId(),
            userId = from.userId.toId(),
            selectedOutcome = when (from.selectedOutcome.kindCase) {
                Model.BetOutcome.KindCase.BOOLEAN_OUTCOME -> {
                    BooleanOutcome(from.selectedOutcome.booleanOutcome)
                }
                Model.BetOutcome.KindCase.KIND_NOT_SET -> NetworkPoolBetOutcome.NotSet
            },
            payoutDestination = from.payoutDestination.value.toByteArray().toPublicKey(),
            timestamp = Instant.fromEpochMilliseconds(from.ts.seconds * 1000L)
        )
    }
}