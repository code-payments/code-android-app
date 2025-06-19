package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.models.BetOutcome
import com.flipcash.services.models.BetOutcome.BooleanOutcome
import com.flipcash.services.models.PoolBet
import com.flipcash.services.models.PoolBetMetadata
import com.getcode.opencode.mapper.Mapper
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
                Model.BetOutcome.KindCase.KIND_NOT_SET -> BetOutcome.NotSet
            },
            payoutDestination = from.payoutDestination.toPublicKey(),
            timestamp = Instant.fromEpochSeconds(from.ts.seconds, 0)
        )
    }
}