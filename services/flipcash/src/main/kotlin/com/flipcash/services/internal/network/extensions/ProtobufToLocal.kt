package com.flipcash.services.internal.network.extensions


import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.common.v1.Common.UserId
import com.codeinc.flipcash.gen.pool.v1.Model
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.models.NetworkPoolResolution
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import com.codeinc.flipcash.gen.activity.v1.Model as ActivityModels
import com.codeinc.flipcash.gen.pool.v1.Model as PoolModels

internal fun ActivityModels.NotificationId.toId(): ID = value.toByteArray().toList()
internal fun UserId.toId(): ID = value.toByteArray().toList()
internal fun PoolModels.PoolId.toId(): ID = value.toByteArray().toList()
internal fun PoolModels.BetId.toId(): ID = value.toByteArray().toList()
internal fun Common.PublicKey.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()


internal fun Model.Resolution.toResolution(): NetworkPoolResolution = when (this.kindCase) {
    PoolModels.Resolution.KindCase.BOOLEAN_RESOLUTION -> NetworkPoolResolution.Refund
    PoolModels.Resolution.KindCase.REFUND_RESOLUTION -> NetworkPoolResolution.BooleanResolution(booleanResolution)
    PoolModels.Resolution.KindCase.KIND_NOT_SET -> NetworkPoolResolution.NotSet
}