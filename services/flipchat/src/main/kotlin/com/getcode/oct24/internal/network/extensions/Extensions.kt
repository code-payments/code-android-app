package com.getcode.oct24.internal.network.extensions

import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.oct24.internal.network.utils.sign
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.utils.toByteString

internal fun ByteArray.toSignature(): Flipchat.Signature {
    return Flipchat.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asPublicKey(): Flipchat.PublicKey {
    return Flipchat.PublicKey.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun ID.toUserId(): Flipchat.UserId {
    return Flipchat.UserId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.toChatId(): Flipchat.ChatId {
    return Flipchat.ChatId.newBuilder().setValue(toByteString()).build()
}

internal fun KeyPair.forAuth(): Flipchat.Auth {
    val pubKey = asPublicKey()
    return Flipchat.Auth.newBuilder()
        .setKeyPair(Flipchat.Auth.KeyPair.newBuilder()
            .setPubKey(pubKey)
            .apply { setSignature(sign(this@forAuth)) }
        ).build()
}

internal fun QueryOptions.toProto(): Flipchat.QueryOptions {
   return Flipchat.QueryOptions.newBuilder()
        .setPageSize(this@toProto.limit.toLong())
        .setOrder(
            if (this@toProto.descending) Flipchat.QueryOptions.Order.DESC
            else Flipchat.QueryOptions.Order.ASC
        ).apply {
            if (this@toProto.offset != null) {
                setOffset(this@toProto.offset.toLong())
            }

            if (this@toProto.cursor != null) {
                setPagingToken(this@toProto.cursor.toPagingToken())
            }
        }.build()
}

internal fun Cursor.toPagingToken(): Flipchat.PagingToken {
    return Flipchat.PagingToken.newBuilder().setValue(this.toByteString()).build()
}