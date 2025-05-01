package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.common.v1.Common
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.utils.toByteString

internal fun ByteArray.asSignature(): Common.Signature {
    return Common.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asPublicKey(): Common.PublicKey {
    return Common.PublicKey.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun ID.asUserId(): Common.UserId {
    return Common.UserId.newBuilder().setValue(toByteString()).build()
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