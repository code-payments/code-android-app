package xyz.flipchat.services.internal.network.extensions

import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.utils.toByteString
import xyz.flipchat.services.domain.model.query.PagingToken
import xyz.flipchat.services.domain.model.query.QueryOptions

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

internal fun ID.toMessageId(): Model.MessageId {
    return Model.MessageId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.toChatId(): Flipchat.ChatId {
    return Flipchat.ChatId.newBuilder().setValue(toByteString()).build()
}

internal fun QueryOptions.toProto(): Flipchat.QueryOptions {
   return Flipchat.QueryOptions.newBuilder()
        .setPageSize(this@toProto.limit.toLong())
        .setOrder(
            if (this@toProto.descending) Flipchat.QueryOptions.Order.DESC
            else Flipchat.QueryOptions.Order.ASC
        ).apply {
            this@toProto.token?.let {
                setPagingToken(it.toPagingToken())
            }
        }.build()
}

internal fun PagingToken.toPagingToken(): Flipchat.PagingToken {
    return Flipchat.PagingToken.newBuilder().setValue(this.toByteString()).build()
}