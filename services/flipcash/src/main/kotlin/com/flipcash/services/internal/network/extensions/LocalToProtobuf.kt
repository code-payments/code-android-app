package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.common.v1.Common
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.codeinc.flipcash.gen.pool.v1.Model as PoolModels
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.toByteString
import com.google.protobuf.Timestamp
import kotlinx.datetime.Instant

internal fun ByteArray.asSignature(): Common.Signature {
    return Common.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asPublicKey(): Common.PublicKey {
    return Common.PublicKey.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun PublicKey.asPublicKey(): Common.PublicKey {
    return Common.PublicKey.newBuilder().setValue(bytes.toByteString()).build()
}

internal fun ID.asPoolId(): PoolModels.PoolId {
    return PoolModels.PoolId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.asPoolBetId(): PoolModels.BetId {
    return PoolModels.BetId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.asUserId(): Common.UserId {
    return Common.UserId.newBuilder().setValue(toByteString()).build()
}

internal fun Instant.asTimestamp(): Timestamp {
    return Timestamp.newBuilder().setSeconds(this.epochSeconds).build()
}

internal fun QueryOptions.asQueryOptions(): Common.QueryOptions {
    return Common.QueryOptions.newBuilder()
        .setPageSize(this@asQueryOptions.limit)
        .setOrder(
            if (this@asQueryOptions.descending) Common.QueryOptions.Order.DESC
            else Common.QueryOptions.Order.ASC
        ).apply {
            this@asQueryOptions.token?.let {
                setPagingToken(it.toPagingToken())
            }
        }.build()
}

internal fun PagingToken.toPagingToken(): Common.PagingToken {
    return Common.PagingToken.newBuilder().setValue(this.toByteString()).build()
}

internal fun List<ID>.toNotificationIds(): List<Model.NotificationId> {
    return this.map { Model.NotificationId.newBuilder().setValue(it.toByteString()).build() }
}

internal fun PoolMetadata.toProto(): PoolModels.SignedPoolMetadata {
    return PoolModels.SignedPoolMetadata.newBuilder()
        .setId(id.asPoolId())
        .setCreator(creator.asUserId())
        .setName(name)
        .setBuyIn(
            Common.FiatPaymentAmount.newBuilder()
                .setCurrency(buyIn.currencyCode.name.lowercase())
                .setNativeAmount(buyIn.doubleValue)
        )
        .setFundingDestination(fundingDestination.asPublicKey())
        .setIsOpen(isOpen)
        .setCreatedAt(createdAt.asTimestamp())
        .apply {
            when (this@toProto.resolution) {
                is NetworkPoolResolution.BooleanResolution -> {
                    setResolution(
                        PoolModels.Resolution.newBuilder()
                            .setBooleanResolution(this@toProto.resolution.value)
                    )
                }

                NetworkPoolResolution.NotSet -> Unit
                NetworkPoolResolution.Refund -> {
                    setResolution(
                        PoolModels.Resolution.newBuilder()
                            .setRefundResolution(PoolModels.Resolution.Refund.getDefaultInstance())
                    )
                }
            }
        }
        .build()
}

internal fun PoolBetMetadata.toProto(): PoolModels.SignedBetMetadata {
    return PoolModels.SignedBetMetadata.newBuilder()
        .setBetId(id.asPoolBetId())
        .setUserId(userId.asUserId())
        .apply {
            when (this@toProto.selectedOutcome) {
                is NetworkPoolBetOutcome.BooleanOutcome -> {
                    setSelectedOutcome(
                        PoolModels.BetOutcome.newBuilder()
                            .setBooleanOutcome(this@toProto.selectedOutcome.value)
                    )
                }

                NetworkPoolBetOutcome.NotSet -> Unit
            }
        }
        .setPayoutDestination(payoutDestination.asPublicKey())
        .setTs(timestamp.asTimestamp())
        .build()
}